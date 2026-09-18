"""Admin-only extension installation; the API remains a read-only role."""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'data-pipeline'))
from load_review_db import connection
with connection() as conn:
    conn.execute('CREATE EXTENSION IF NOT EXISTS pgrouting')
    print('pgRouting:', conn.execute('SELECT pgr_version()').fetchone()[0])
