"""Load local trial only; never promote reviewed source eligibility."""
import hashlib
import json
import sys
import argparse
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'data-pipeline'))
from load_review_db import connection
from psycopg.types.json import Jsonb

folder = ROOT / 'data/processed/walking-trial'
body = (folder / 'graph.json').read_text(encoding='utf-8')
metadata = json.loads((folder / 'metadata.json').read_text(encoding='utf-8'))
assert hashlib.sha256(body.encode()).hexdigest() == metadata['networkId']
assert metadata['navigationApproved'] is False
parser = argparse.ArgumentParser()
parser.add_argument('--replace-local', action='store_true', help='Archive and activate a revised local graph; networkId guards stale clients.')
args = parser.parse_args()
with connection() as conn:
    conn.execute("SELECT pg_advisory_xact_lock(hashtext('shade-api-register'))")
    release = conn.execute('SELECT id FROM shade_api.release WHERE active').fetchone()[0]
    old = conn.execute('SELECT network_id FROM shade_api.walking_trial WHERE release_id=%s', (release,)).fetchone()
    if old and old[0] != metadata['networkId'] and not args.replace_local:
        raise ValueError('Use --replace-local to archive and version this local graph')
    conn.execute('''INSERT INTO shade_api.walking_trial_revision(release_id,network_id,graph,metadata)
        SELECT release_id,network_id,graph,metadata FROM shade_api.walking_trial WHERE release_id=%s
        ON CONFLICT DO NOTHING''', (release,))
    conn.execute('''INSERT INTO shade_api.walking_trial_revision(release_id,network_id,graph,metadata)
        VALUES(%s,%s,%s,%s) ON CONFLICT DO NOTHING''',
                 (release, metadata['networkId'], Jsonb(json.loads(body)), Jsonb(metadata)))
    conn.execute('''INSERT INTO shade_api.walking_trial VALUES(%s,%s,%s,%s)
        ON CONFLICT (release_id) DO UPDATE SET network_id=excluded.network_id,
        graph=excluded.graph, metadata=excluded.metadata''',
                 (release, metadata['networkId'], Jsonb(json.loads(body)), Jsonb(metadata)))
print('Local trial graph registered; source approval flags unchanged.')
