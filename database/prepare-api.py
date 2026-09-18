"""Provision least-privilege local roles, then register a versioned review catalog.

Run --roles before starting Spring (Flyway creates shade_api tables).
Run --catalog after Flyway has completed. Does not alter review data or approvals.
"""
import argparse, hashlib, json, secrets, sys, uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'data-pipeline'))
from load_review_db import connection
from psycopg import sql
from psycopg.types.json import Jsonb

def env():
    return dict(x.split('=',1) for x in (ROOT/'backend/.env').read_text().splitlines() if '=' in x and not x.startswith('#'))

def provision():
    settings=env(); additions={}
    for key in ('API_DB_PASSWORD','MIGRATION_DB_PASSWORD'):
        if key not in settings: additions[key]=secrets.token_urlsafe(36)
    if additions:
        with (ROOT/'backend/.env').open('a',encoding='utf-8') as f:
            f.write('\n'+'\n'.join(k+'='+v for k,v in additions.items())+'\n')
        settings.update(additions)
    with connection() as c:
        for role,key in [('shadow_api','API_DB_PASSWORD'),('shadow_migrator','MIGRATION_DB_PASSWORD')]:
            if not c.execute('SELECT 1 FROM pg_roles WHERE rolname=%s',(role,)).fetchone():
                c.execute(sql.SQL('CREATE ROLE {} LOGIN PASSWORD {}').format(sql.Identifier(role),sql.Literal(settings[key])))
        c.execute('GRANT CONNECT ON DATABASE shadow_map TO shadow_api,shadow_migrator')
        c.execute('CREATE SCHEMA IF NOT EXISTS shade_api AUTHORIZATION shadow_migrator')
        c.execute('GRANT USAGE ON SCHEMA shade_review TO shadow_migrator')
        c.execute('GRANT SELECT ON ALL TABLES IN SCHEMA shade_review TO shadow_migrator')
    print('API and migration roles ready; passwords not printed.')

def issue(code,message):return dict(code=code,message=message,field=None,retryable=False)
def ident(value):return str(uuid.uuid5(uuid.NAMESPACE_URL,'shade-api:'+value))
def resource(id,release,kind,url=None,snapshot=None,**extra):
    body=dict(id=id,kind=kind,version=release,availability='AVAILABLE' if url else 'PREPARING',snapshotId=snapshot,
        format=None,delivery='MANIFEST' if url else 'NONE',publicUrl=url,expiresAt=None,bounds=None,sourceCrs=None,
        verticalDatum=None,heightUnit=None,transformNotes=None,minZoom=None,maxZoom=None,legend=[],validMaskResourceId=None,
        validationLevel='FORMAT_CHECKED' if url else 'UNVERIFIED',issues=[])
    body.update(extra);return body

def register():
    manifest=json.loads((ROOT/'data/processed/wmts-manifest.json').read_text(encoding='utf-8'))
    with connection() as c:
        artifacts=c.execute('SELECT source_path,content_sha256 FROM shade_review.artifact WHERE is_current ORDER BY source_path').fetchall()
        digest=hashlib.sha256(json.dumps([artifacts,manifest],sort_keys=True).encode()).hexdigest()
        release=ident(digest)
        c.execute('SELECT pg_advisory_xact_lock(hashtext(%s))',('shade-api-register',))
        c.execute('INSERT INTO shade_api.release(id,fingerprint) VALUES(%s,%s) ON CONFLICT DO NOTHING',(release,digest))
        c.execute('''INSERT INTO shade_api.place_reference
            SELECT %s,id,dataset_version_id,name,kind,
            CASE WHEN geometry_status='REFERENCE_ONLY' THEN geom ELSE NULL END,
            geometry_status,COALESCE(NULLIF(payload->>'canonical_place_id',''),id::text)::uuid,
            payload->>'coordinate_role',payload->>'source',
            COALESCE(NULLIF(payload->>'ars_id',''),NULLIF(payload->>'source_id','')),
            NULLIF(payload->>'address',''),NULLIF(payload->>'coordinate_evidence_url',''),NULLIF(payload->>'review_reason','')
            FROM shade_review.place ON CONFLICT DO NOTHING''',(release,))
        for layer in manifest['layers']:
            raster=c.execute('SELECT dataset_version_id,instant FROM shade_review.shadow_raster WHERE analysis_sha256=%s',(layer['source_sha256'],)).fetchone()
            if not raster or raster[1].isoformat()!=__import__('datetime').datetime.fromisoformat(layer['instant']).isoformat():
                # Compare instants, not text time zones.
                if not raster or raster[1]!=__import__('datetime').datetime.fromisoformat(layer['instant']): raise ValueError('Manifest/raster mismatch')
            snapshot=ident(release+':snapshot:'+str(raster[0]))
            wmts=dict(url=layer['tile_template'],layer=layer['id'],matrixSet=layer['matrix_set'],bounds=layer['bounds'],tileSize=layer['tile_size'],minZoom=layer['min_zoom'],maxZoom=layer['max_zoom'])
            c.execute('INSERT INTO shade_api.snapshot VALUES(%s,%s,%s,%s,%s,%s) ON CONFLICT DO NOTHING',(snapshot,release,raster[0],raster[1],layer['date'],Jsonb(wmts)))
            b=layer['bounds']; ring=[[b[0],b[1]],[b[2],b[1]],[b[2],b[3]],[b[0],b[3]],[b[0],b[1]]]
            rid=ident(snapshot+':wmts')
            body=resource(rid,release,'SHADOW',wmts['url'],snapshot,format='image/png',delivery='RASTER_TILE',sourceCrs='EPSG:5186',
                bounds={'type':'MultiPolygon','coordinates':[[ring]]},minZoom=wmts['minZoom'],maxZoom=wmts['maxZoom'],wmts=wmts,
                transformNotes='표시만 EPSG:3857 최근린 재투영. 분석 원천은 EPSG:5186 TIF.',validationLevel='ALIGNED',
                issues=[issue('POLICY_UNAPPROVED','표시 자료 등록이며 장소별 분석 승인을 의미하지 않습니다.')])
            c.execute('INSERT INTO shade_api.resource VALUES(%s,%s,%s,%s,%s) ON CONFLICT DO NOTHING',(rid,release,snapshot,'SHADOW',Jsonb(body)))
        host='https://mapprime.synology.me:15289'
        sources={'BUILDINGS':host+'/younguk/pointcloud/teheranro/tile/tileset.json',
                 'BLOCKS':host+'/younguk/pointcloud/teheranro/teheranro_shadow_tiles_revised/tileset.json',
                 'TERRAIN':host+'/seoul/data/terrain/1m_v1.1/layer.json','POINT_CLOUD':None,'HEATMAP':None}
        for kind,url in sources.items():
            rid=ident(release+':'+kind);body=resource(rid,release,kind,url)
            c.execute('INSERT INTO shade_api.resource VALUES(%s,%s,NULL,%s,%s) ON CONFLICT DO NOTHING',(rid,release,kind,Jsonb(body)))
        c.execute('UPDATE shade_api.release SET active=false WHERE active AND id<>%s',(release,))
        c.execute('UPDATE shade_api.release SET active=true WHERE id=%s',(release,))
    print('Registered review release',release,'with',len(manifest['layers']),'snapshots. No service approval changed.')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--roles',action='store_true');p.add_argument('--catalog',action='store_true');a=p.parse_args()
    if a.roles:provision()
    if a.catalog:register()
