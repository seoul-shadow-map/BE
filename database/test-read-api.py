"""Read-only integration checks against the actual PostGIS data and OpenAPI contract."""
import argparse, datetime, json, sys, urllib.request, urllib.parse, urllib.error, uuid
from pathlib import Path
import yaml, jsonschema
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'data-pipeline'))
from load_review_db import connection
from psycopg import errors
import psycopg

spec=yaml.safe_load((ROOT/'data-pipeline/contracts/openapi.yaml').read_text(encoding='utf-8'))
checks=[]
base='http://127.0.0.1:8080'

def check(name,condition):
    if not condition: raise AssertionError(name)
    checks.append(name)

def request(path,query=None,status=200,schema=None):
    url=base+path+('?' + urllib.parse.urlencode(query) if query else '')
    try: r=urllib.request.urlopen(url,timeout=10)
    except urllib.error.HTTPError as e: r=e
    data=json.load(r)
    check(path+' status '+str(status),r.status==status)
    if path.startswith('/api/'):
        check('request ID header',bool(r.headers.get('X-Request-Id')))
        check('no-store',r.headers.get('Cache-Control')=='no-store')
        if status>=400:schema='Problem'
    if schema:
        validator=jsonschema.Draft202012Validator({'$ref':'#/components/schemas/'+schema,'components':spec['components']},format_checker=jsonschema.FormatChecker())
        validator.validate(data)
        check('schema '+schema,True)
    text=json.dumps(data)
    check('no SQL or private source paths',not any(x in text for x in ['SELECT p.','data/raw','data\\\\raw','POSTGRES_PASSWORD','API_DB_PASSWORD','org.postgresql']))
    return data

def main():
    global base
    p=argparse.ArgumentParser();p.add_argument('--base',default=base);a=p.parse_args();base=a.base.rstrip('/')
    request('/actuator/health')
    config=request('/api/v1/config',schema='Config');rid=config['context']['releaseId']
    check('real data kept in REVIEW stage',config['context']['dataMode']=='REAL' and config['context']['dataStage']=='REVIEW')
    check('no routing policy falsely approved',config['policies']==[] and config['routingBoundary'] is None)
    check('unimplemented analyses are PREPARING',all(f['state']=='PREPARING' for f in config['features'] if f['featureId'] in ('F04','F05','F06')))
    catalog=request('/api/v1/catalog',dict(releaseId=rid,localDate='2026-09-15'),schema='Catalog')
    check('all 19 instants',len(catalog['times'])==19 and len({t['snapshotId'] for t in catalog['times']})==19)
    check('analysis remains disabled',all(not t['analysisReady'] for t in catalog['times']))
    sid=catalog['times'][2]['snapshotId']
    resources=request('/api/v1/resources',dict(releaseId=rid,kind='SHADOW'),schema='Resources')
    check('19 independent WMTS resources',len(resources['items'])==19)
    check('UUID differs from WMTS layer ID',all(r['snapshotId']!=r['wmts']['layer'] for r in resources['items']))
    check('display grid only 3857 WMTS',all(r['wmts']['matrixSet']=='WebMercatorQuad' and r['wmts']['tileSize']==256 for r in resources['items']))
    filtered=request('/api/v1/resources',dict(releaseId=rid,kind='SHADOW',snapshotId=sid),schema='Resources')
    check('snapshot filter',len(filtered['items'])==1 and filtered['items'][0]['snapshotId']==sid)
    request('/api/v1/resources',dict(releaseId=rid),schema='Resources')
    empty=request('/api/v1/catalog',dict(releaseId=rid,localDate='2026-09-16'),schema='Catalog');check('unprovided date empty',empty['times']==[])
    common=dict(releaseId=rid,snapshotId=sid,bbox='127.04,37.50,127.07,37.52',limit=3)
    with connection() as db:
        for route,kind in [('stops','STOP'),('places','REST')]:
            first=request('/api/v1/'+route,common,schema='PlaceList')
            check(route+' nonempty',len(first['items'])==3)
            seen=set();page=first
            for _ in range(3):
                for item in page['items']:
                    check('no duplicate pagination',item['id'] not in seen);seen.add(item['id'])
                    confirmed=db.execute('SELECT 1 FROM shade_api.stop_confirmation WHERE release_id=%s AND place_id=%s',(rid,item['id'])).fetchone()
                    check('confirmation determines eligibility',item['verification']==('VERIFIED' if confirmed else 'CANDIDATE') and item['accessId'] is None)
                    if not confirmed: check('candidate remains uncomputed',item['observation']['state']=='NOT_COMPUTED')
                    check('correct collection',item['kind']==kind)
                    point=item['point']['coordinates'];check('coordinate order and bounds',127.04<=point[0]<=127.07 and 37.50<=point[1]<=37.52)
                    expected=db.execute('SELECT ST_X(ST_Transform(geom,4326)),ST_Y(ST_Transform(geom,4326)) FROM shade_review.place WHERE id=%s',(item['id'],)).fetchone()
                    check('actual 5186 to 4326 transformation',max(abs(point[i]-expected[i]) for i in (0,1))<1e-8)
                if not page['nextCursor']:break
                page=request('/api/v1/'+route,dict(common,cursor=page['nextCursor']),schema='PlaceList')
            id=first['items'][0]['id']
            detail=request('/api/v1/'+route+'/'+id,dict(releaseId=rid,snapshotId=sid),schema='PlaceDetail')
            check('list/detail identity',detail['place']==first['items'][0])
            request('/api/v1/'+('places' if route=='stops' else 'stops')+'/'+id,dict(releaseId=rid),status=404)
            request('/api/v1/'+route,dict(common,cursor=first['nextCursor'],q='changed'),status=400)
            shade=request('/api/v1/'+route,dict(common,shadeOnly='true'),schema='PlaceList');check('unverified points excluded from shade filter',shade['items']==[])
        # Snapshot creation must not promote imported rows.
        check('review approvals unchanged',db.execute('SELECT count(*) FROM shade_review.record WHERE analysis_eligible OR route_eligible').fetchone()[0]==0)
    for scope in config['candidateScopes']:
        result=request('/api/v1/places',dict(releaseId=rid,view='UNLOCATED',candidateScopeId=scope['id'],limit=2),schema='PlaceList')
        check('unlocated never gains coordinates',all(i['point'] is None for i in result['items']))
    for bbox in ['NaN,37,128,38','127,38,126,37','-180,-85,180,85','127,37,127,38','127,37,128']:
        request('/api/v1/stops',dict(common,bbox=bbox),status=400)
    for overrides in [dict(limit=0),dict(limit=101),dict(limit='abc'),dict(cursor='bad'),dict(releaseId='bad'),dict(shadeOnly='bad'),dict(q='x'*101)]:
        request('/api/v1/stops',dict(common,**overrides),status=400)
    request('/api/v1/stops',dict(common,releaseId=str(uuid.uuid4())),status=409)
    request('/api/v1/stops',dict(common,snapshotId=str(uuid.uuid4())),status=404)
    request('/api/v1/places/'+str(uuid.uuid4()),dict(releaseId=rid),status=404)
    request('/api/v1/catalog',dict(releaseId=rid,localDate='2026-99-99'),status=400)
    request('/api/v1/places',dict(releaseId=rid,view='UNLOCATED'),status=400)
    injected=request('/api/v1/stops',dict(common,q="' OR 1=1 --"),schema='PlaceList');check('SQL injection treated as text',injected['items']==[])
    settings=dict(x.split('=',1) for x in (ROOT/'backend/.env').read_text().splitlines() if '=' in x and not x.startswith('#'))
    with psycopg.connect(host='127.0.0.1',port=55433,dbname='shadow_map',user='shadow_api',password=settings['API_DB_PASSWORD']) as c:
        for statement in ['SELECT * FROM shade_review.record LIMIT 1',"UPDATE shade_api.release SET active=false WHERE false"]:
            try:
                with c.transaction():c.execute(statement)
            except errors.InsufficientPrivilege:check('read-only API account denied privileged action',True)
            else:raise AssertionError('API account has excess privilege')
    report=dict(passed=True,checked_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),checks=len(checks),releaseId=rid,base=base,details=checks)
    target=ROOT/'data/reports/backend-read-api.json';target.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print('PASS',len(checks),'checks; report:',target)

if __name__=='__main__':main()
