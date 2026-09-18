"""Atomically register verified native DSM aggregates for the active release."""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'data-pipeline'))
from load_review_db import connection
from psycopg.types.json import Jsonb

data = json.loads((ROOT/'data/processed/heatmap/aggregates.json').read_text(encoding='utf-8'))
with connection() as conn:
    conn.execute("SELECT pg_advisory_xact_lock(hashtext('shade-api-register'))")
    release = conn.execute('SELECT id FROM shade_api.release WHERE active').fetchone()[0]
    snapshots = conn.execute('''SELECT s.id, r.analysis_sha256 FROM shade_api.snapshot s
        JOIN shade_review.shadow_raster r ON r.dataset_version_id=s.dataset_version_id
        WHERE s.release_id=%s''', (release,)).fetchall()
    by_hash = {row[1]: row[0] for row in snapshots}
    if len(by_hash) != 19 or set(by_hash) != {s['sourceSha256'] for s in data['snapshots']}:
        raise ValueError('Release raster hashes do not match aggregates')
    with conn.cursor() as cursor:
        cursor.executemany('''INSERT INTO shade_api.heatmap_grid VALUES(
            %s,%s,ST_Multi(ST_MakeEnvelope(%s,%s,%s,%s,5186)),%s) ON CONFLICT DO NOTHING''',
            [(release, c['id'], *c['bounds'], c['metrics']['targetAreaM2']) for c in data['snapshots'][0]['cells']])
        for snapshot in data['snapshots']:
            sid = by_hash[snapshot['sourceSha256']]
            existing = conn.execute('SELECT policy_id FROM shade_api.heatmap_run WHERE release_id=%s AND snapshot_id=%s', (release, sid)).fetchone()
            if existing and str(existing[0]) != data['policyId']:
                raise ValueError('Policy changed: register a new release instead of replacing analysis')
            conn.execute('INSERT INTO shade_api.heatmap_run VALUES(%s,%s,%s,%s,%s) ON CONFLICT DO NOTHING',
                         (release, sid, data['policyId'], snapshot['sourceSha256'], Jsonb(data['policy'])))
            cursor.executemany('INSERT INTO shade_api.heatmap_metric VALUES(%s,%s,%s,%s,%s) ON CONFLICT DO NOTHING',
                               [(release, sid, c['id'], c['metrics']['validAreaM2'], c['metrics']['shadeAreaM2']) for c in snapshot['cells']])
    count = conn.execute('SELECT count(*) FROM shade_api.heatmap_metric WHERE release_id=%s', (release,)).fetchone()[0]
    assert count == sum(len(s['cells']) for s in data['snapshots'])
    # Resource readiness is derived from the atomically completed aggregate set.
    conn.execute('''UPDATE shade_api.resource SET body = body || %s
                    WHERE release_id=%s AND kind='HEATMAP' ''', (Jsonb({
        'availability': 'AVAILABLE', 'delivery': 'MANIFEST', 'format': 'application/geo+json',
        'publicUrl': 'http://127.0.0.1:8080/api/v1/heatmap', 'sourceCrs': 'EPSG:5186',
        'validationLevel': 'ALIGNED',
        'transformNotes': 'DSM native 20m planimetric grid. Query requires releaseId, snapshotId and bbox.',
        'issues': [{'code': 'UNCERTAIN', 'message': '건물·수목 포함 DSM 평면 면적 비율이며 보행 공간 비율과 다릅니다.', 'field': None, 'retryable': False}],
    }), release))
print('Registered', count, 'DSM heatmap cells; source data and place eligibility unchanged.')
