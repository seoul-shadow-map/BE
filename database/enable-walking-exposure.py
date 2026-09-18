"""Enable read-only native raster analysis for the existing API role."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'data-pipeline'))
from load_review_db import connection
with connection() as c:
    count=c.execute("SELECT count(*) FROM shade_review.shadow_raster WHERE ST_SRID(rast)<>5186 OR ST_SkewX(rast)<>0 OR ST_SkewY(rast)<>0 OR ST_ScaleX(rast)<>2 OR ST_ScaleY(rast)<>-2").fetchone()[0]
    if count: raise ValueError('Native raster grid requires review')
    c.execute('GRANT USAGE ON SCHEMA shade_review TO shadow_api')
    c.execute('GRANT SELECT (dataset_version_id,rast) ON shade_review.shadow_raster TO shadow_api')
print('Native raster read permissions ready; no source data changed.')
