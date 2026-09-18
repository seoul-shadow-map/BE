"""Live HTTP/schema and independent PostGIS raster area comparisons."""
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

import jsonschema
import yaml

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'data-pipeline'))
from load_review_db import connection

spec = yaml.safe_load((ROOT/'data-pipeline/contracts/openapi.yaml').read_text(encoding='utf-8'))
checks = []


def check(name, condition):
    assert condition, name
    checks.append(name)


def get(path, query=None, status=200, schema=None):
    url = 'http://127.0.0.1:8080/api/v1' + path
    if query: url += '?' + urllib.parse.urlencode(query)
    try:
        response = urllib.request.urlopen(url)
    except urllib.error.HTTPError as error:
        response = error
    body = json.load(response)
    check(path + ' HTTP ' + str(status), response.status == status)
    if schema or status != 200:
        jsonschema.Draft202012Validator({**spec, '$ref': '#/components/schemas/' + (schema or 'Problem')},
                                       format_checker=jsonschema.FormatChecker()).validate(body)
        checks.append(path + ' contract')
    return body


config = get('/config')
release = config['context']['releaseId']
catalog = get('/catalog', {'releaseId': release, 'localDate': config['availableDates'][0]})
check('heatmap ready but limited DSM scope', next(f for f in config['features'] if f['featureId'] == 'F03')['state'] == 'PARTIAL')
check('route analysis remains unapproved', all(not t['analysisReady'] for t in catalog['times']))
all_ids = None
with connection() as conn:
    for time in catalog['times']:
        sid = time['snapshotId']
        query = {'releaseId': release, 'snapshotId': sid, 'bbox': '127.04,37.50,127.07,37.52'}
        body = get('/heatmap', query, schema='HeatmapResponse')
        features = body['features']
        ids = {f['id'] for f in features}
        if all_ids is None: all_ids = ids
        check('same fixed 3168 cells ' + sid, len(ids) == 3168 and ids == all_ids)
        check('requested time ' + sid, body['snapshotId'] == sid and body['instant'] == time['instant'])
        check('DSM 20m native policy', body['policy']['surface'] == 'DSM_PLANIMETRIC' and body['policy']['gridSizeM'] == 20)
        pixel_count, shadow_count = conn.execute('''SELECT (ST_SummaryStats(r.rast,1,true)).count,
            (ST_SummaryStats(r.rast,1,true)).sum FROM shade_review.shadow_raster r
            JOIN shade_api.snapshot s ON s.dataset_version_id=r.dataset_version_id WHERE s.id=%s''', (sid,)).fetchone()
        totals = {key: sum(f['properties']['metrics'][key] for f in features)
                  for key in ['targetAreaM2', 'validAreaM2', 'shadeAreaM2', 'unknownAreaM2']}
        check('native PostGIS total area ' + sid, totals['validAreaM2'] == pixel_count*4)
        check('native PostGIS total shadow ' + sid, totals['shadeAreaM2'] == shadow_count*4)
        check('valid plus unknown equals target', totals['validAreaM2'] + totals['unknownAreaM2'] == totals['targetAreaM2'])
        samples = sorted(features, key=lambda f: f['properties']['metrics']['targetAreaM2'])[:2] + features[100:103]
        for sample in samples:
            detail = get('/grids/' + sample['id'], {'releaseId': release, 'snapshotId': sid}, schema='AreaResult')
            check('detail equals map metrics', detail['metrics'] == sample['properties']['metrics'])
            check('detail equals map geometry', detail['geometry'] == sample['geometry'])
            count, total = conn.execute('''SELECT (ST_SummaryStats(ST_Clip(r.rast,g.geom),1,true)).count,
                (ST_SummaryStats(ST_Clip(r.rast,g.geom),1,true)).sum
                FROM shade_review.shadow_raster r JOIN shade_api.snapshot s ON s.dataset_version_id=r.dataset_version_id
                JOIN shade_api.heatmap_grid g ON g.release_id=s.release_id
                WHERE s.id=%s AND g.id=%s''', (sid, sample['id'])).fetchone()
            check('independent native raster clip matches cell', count*4 == detail['metrics']['validAreaM2'] and total*4 == detail['metrics']['shadeAreaM2'])
        empty = get('/heatmap', {**query, 'bbox': '126.9,37.4,126.91,37.41'})
        check('outside is empty not zero-shade fabricated grid', empty['features'] == [])

query = {'releaseId': release, 'snapshotId': catalog['times'][0]['snapshotId'], 'bbox': '127.04,37.50,127.07,37.52'}
for bbox in ['NaN,37,128,38', '-180,-85,180,85', '127,38,126,37', 'bad']:
    get('/heatmap', {**query, 'bbox': bbox}, 400)
get('/heatmap', {**query, 'snapshotId': str(uuid.uuid4())}, 404)
get('/heatmap', {**query, 'releaseId': str(uuid.uuid4())}, 409)
get('/heatmap', {**query, 'snapshotId': "' OR 1=1 --"}, 400)
get('/grids/' + str(uuid.uuid4()), query, 404)
get('/grids/bad-id', query, 400)
get('/heatmap', {'releaseId': release, 'bbox': query['bbox']}, 400)
resource = get('/resources', {'releaseId': release, 'kind': 'HEATMAP'}, schema='Resources')
check('heatmap resource available', resource['items'][0]['availability'] == 'AVAILABLE')
(ROOT/'data/reports/heatmap-api.json').write_text(json.dumps({'passed': True, 'checks': checks}, indent=2), encoding='utf-8')
print('PASS', len(checks), 'heatmap API checks')
