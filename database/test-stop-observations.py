"""Compare every confirmed point/time API result to independent PostGIS sampling."""
import json
import sys
from pathlib import Path
from urllib.request import urlopen
from urllib.parse import urlencode
import jsonschema
import yaml

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'data-pipeline'))
from load_review_db import connection

spec = yaml.safe_load((ROOT/'data-pipeline/contracts/openapi.yaml').read_text(encoding='utf-8'))
validator = jsonschema.Draft202012Validator({**spec,'$ref':'#/components/schemas/PlaceDetail'})
count = 0
with connection() as db:
    rows = db.execute('''SELECT c.release_id,c.place_id,s.id,
        ST_Value(r.rast,c.geom) AS pixel,o.state
        FROM shade_api.stop_confirmation c
        JOIN shade_api.release rel ON rel.id=c.release_id AND rel.active
        JOIN shade_api.snapshot s ON s.release_id=c.release_id
        JOIN shade_review.shadow_raster r ON r.dataset_version_id=s.dataset_version_id
        JOIN shade_api.stop_observation o ON o.release_id=c.release_id AND o.place_id=c.place_id AND o.snapshot_id=s.id
        ORDER BY c.place_id,s.instant''').fetchall()
    assert len(rows)==456
    for release,pid,sid,pixel,stored in rows:
        expected = 'NO_DATA' if pixel is None else 'SHADE' if pixel==1 else 'SUN'
        assert stored==expected
        query=urlencode({'releaseId':release,'snapshotId':sid})
        body=json.load(urlopen(f'http://127.0.0.1:8080/api/v1/stops/{pid}?{query}'))
        validator.validate(body)
        place=body['place']
        assert place['verification']=='VERIFIED'
        assert place['observation']['state']==expected
        assert place['observation']['snapshotId']==str(sid)
        assert place['accessId'] is None
        count+=1
    rest=db.execute("SELECT id FROM shade_api.place_reference WHERE release_id=%s AND kind='REST' LIMIT 1",(release,)).fetchone()[0]
    body=json.load(urlopen(f'http://127.0.0.1:8080/api/v1/places/{rest}?{query}'))
    assert body['place']['verification']=='CANDIDATE'
    assert body['place']['observation']['state']=='NOT_COMPUTED'
print(f'PASS: {count} independent native raster / API / schema comparisons; REST remains unconfirmed.')
