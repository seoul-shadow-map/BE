"""Record user-confirmed Teheranro stop coordinates and sample all native rasters.

Confirmation applies to the displayed stop reference points, not waiting polygons
or pedestrian access. This does not change or approve other place candidates.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'data-pipeline'))
import spatial  # Isolate bundled PROJ from system PostGIS.
import numpy as np
import rasterio
from common import sha
from load_review_db import connection

EVIDENCE = '2026-09-16: User reviewed displayed bus stop locations and confirmed all correct. Scope: Teheranro native TIF extent.'


def sample(source, values, valid, x, y):
    row, col = source.index(x, y)
    if not (0 <= row < source.height and 0 <= col < source.width):
        return 'OUTSIDE_COVERAGE'
    if not valid[row, col]:
        return 'NO_DATA'
    return 'SHADE' if values[row, col] == 1 else 'SUN'


def main():
    catalog = json.loads((ROOT / 'data/processed/shadow-catalog.json').read_text(encoding='utf-8'))
    entries = catalog['snapshots']
    if len(entries) != 19:
        raise ValueError('Expected all 19 snapshots')
    with rasterio.open(ROOT / 'data/processed/shadow' / entries[0]['file']) as first:
        bounds = tuple(first.bounds)
        signature = (first.shape, tuple(first.transform))
    report = {'evidence': EVIDENCE, 'crs': 'EPSG:5186', 'surface': 'DSM', 'stops': [], 'observations': []}
    with connection() as conn:
        conn.execute("SELECT pg_advisory_xact_lock(hashtext('shade-api-register'))")
        release = conn.execute('SELECT id FROM shade_api.release WHERE active').fetchone()[0]
        stops = conn.execute('''SELECT id, source_identifier, ST_X(geom), ST_Y(geom)
            FROM shade_api.place_reference WHERE release_id=%s AND kind='STOP'
              AND id=canonical_id AND ST_Intersects(geom,ST_MakeEnvelope(%s,%s,%s,%s,5186))
            ORDER BY id''', (release, *bounds)).fetchall()
        snapshots = dict(conn.execute('''SELECT r.analysis_sha256, s.id FROM shade_api.snapshot s
            JOIN shade_review.shadow_raster r ON r.dataset_version_id=s.dataset_version_id
            WHERE s.release_id=%s''', (release,)).fetchall())
        if set(snapshots) != {e['output_sha256'] for e in entries}:
            raise ValueError('Snapshot versions do not match native rasters')
        for pid, ars, x, y in stops:
            conn.execute('''INSERT INTO shade_api.stop_confirmation
                (release_id,place_id,method,evidence,geom)
                VALUES(%s,%s,'USER_CONFIRMED',%s,ST_SetSRID(ST_MakePoint(%s,%s),5186))
                ON CONFLICT DO NOTHING''', (release, pid, EVIDENCE, x, y))
            same = conn.execute('''SELECT ST_Equals(geom,ST_SetSRID(ST_MakePoint(%s,%s),5186))
                FROM shade_api.stop_confirmation WHERE release_id=%s AND place_id=%s''', (x,y,release,pid)).fetchone()[0]
            if not same:
                raise ValueError('Confirmed coordinates changed; review required')
            report['stops'].append({'id': str(pid), 'sourceIdentifier': ars, 'coordinates': [x,y]})
        for entry in entries:
            path = ROOT / 'data/processed/shadow' / entry['file']
            if sha(path) != entry['output_sha256']:
                raise ValueError('Native raster hash mismatch')
            with rasterio.open(path) as source:
                if source.crs.to_epsg() != 5186 or (source.shape, tuple(source.transform)) != signature:
                    raise ValueError('Native raster CRS or grid mismatch')
                values, valid = source.read(1), source.read_masks(1) > 0
                if not np.isin(values[valid], [0, 1]).all():
                    raise ValueError('Unexpected categorical raster values')
                for pid, ars, x, y in stops:
                    state = sample(source, values, valid, x, y)
                    conn.execute('''INSERT INTO shade_api.stop_observation VALUES(%s,%s,%s,%s,%s)
                        ON CONFLICT DO NOTHING''', (release,pid,snapshots[entry['output_sha256']],entry['output_sha256'],state))
                    report['observations'].append({'placeId':str(pid), 'sourceIdentifier':ars,
                        'snapshotId':str(snapshots[entry['output_sha256']]), 'instant':entry['instant'],
                        'state':state, 'sourceSha256':entry['output_sha256']})
    target = ROOT / 'data/reports/confirmed-stop-observations.json'
    target.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(f"Confirmed {len(stops)} stops; sampled {len(report['observations'])} native DSM observations.")


if __name__ == '__main__':
    main()
