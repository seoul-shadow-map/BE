"""Independent native-TIF validation for all 19 times; no WMTS data is used."""
import sys,json,urllib.request,urllib.error,math,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'data-pipeline'))
from load_review_db import connection
import rasterio
from pyproj import Transformer
from shapely.geometry import LineString
base='http://127.0.0.1:8080/api/v1'
def get(path):return json.load(urllib.request.urlopen(base+path))
def post(body):return json.load(urllib.request.urlopen(urllib.request.Request(base+'/walking-exposure',data=json.dumps(body).encode(),headers={'Content-Type':'application/json'})))
c=get('/config');release=c['context']['releaseId'];network=get('/walking-network?releaseId='+release)['networkId']
cat=get('/catalog?releaseId='+release+'&localDate='+c['availableDates'][0])
coordinates=[[127.047,37.504],[127.053,37.505],[127.055,37.506]]
project=Transformer.from_crs(4326,5186,always_xy=True)
native=[project.transform(*p) for p in coordinates]
with connection() as db:paths=dict(db.execute('select s.id,r.analysis_path from shade_api.snapshot s join shade_review.shadow_raster r on r.dataset_version_id=s.dataset_version_id where s.release_id=%s',(release,)))
reports=[]
for t in cat['times']:
 b=dict(releaseId=release,networkId=network,snapshotId=t['snapshotId'],routeKey='independent',geometry=dict(type='LineString',coordinates=coordinates))
 start=time.time();d=post(b);sums={'SHADE':0.,'SUN':0.,'UNKNOWN':0.}
 with rasterio.open(ROOT/paths[__import__('uuid').UUID(t['snapshotId'])]) as raster:
  for a,z in zip(native,native[1:]):
   u,v=(~raster.transform)*a;(w,h)=(~raster.transform)*z
   fractions={0.,1.}
   for q0,q1 in [(u,w),(v,h)]:
    if q0!=q1:
     fractions.update((x-q0)/(q1-q0) for x in range(math.ceil(min(q0,q1)),math.floor(max(q0,q1))+1) if 0<(x-q0)/(q1-q0)<1)
   fractions=sorted(fractions);distance=math.dist(a,z)
   for lo,hi in zip(fractions,fractions[1:]):
    mid=(lo+hi)/2;x=a[0]+(z[0]-a[0])*mid;y=a[1]+(z[1]-a[1])*mid
    value=next(raster.sample([(x,y)],masked=True))[0]
    state='UNKNOWN' if __import__('numpy').ma.is_masked(value) else 'SHADE' if value==1 else 'SUN' if value==0 else 'UNKNOWN'
    sums[state]+=distance*(hi-lo)
 m=d['metrics']
 for state,field in [('SHADE','shadeLengthM'),('SUN','sunLengthM'),('UNKNOWN','unknownLengthM')]:assert abs(sums[state]-m[field])<.003,(state,sums,m)
 assert abs(sum(sums.values())-m['totalLengthM'])<.003
 assert abs(sum(s['lengthM'] for s in d['segments'])-m['totalLengthM'])<.00001
 assert m['expectedDurationSec'] is None
 reports.append(dict(snapshot=t['snapshotId'],metrics=m,ms=round((time.time()-start)*1000)))
# Out-of-coverage must remain unknown, not sun; reversed geometry preserves length and direction.
b['geometry']['coordinates']=[[127.00,37.50],[127.001,37.501]]
d=post(b);assert d['metrics']['shadeRatio'] is None and d['metrics']['coverage']==0
b['geometry']['coordinates']=coordinates[::-1];d=post(b);assert abs(d['metrics']['totalLengthM']-reports[-1]['metrics']['totalLengthM'])<.003
assert d['segments'][0]['geometry']['coordinates'][0]==coordinates[-1]
for change in [dict(snapshotId='bad'),dict(networkId='wrong'),dict(geometry=dict(type='LineString',coordinates=[[0,0],[127,37]]))]:
 try:post({**b,**change});raise AssertionError('Invalid request accepted')
 except urllib.error.HTTPError as e:assert e.code in [400,404,409,422]
out=ROOT/'data/reports/route-exposure';out.mkdir(exist_ok=True)
(out/'verification.json').write_text(json.dumps(dict(passed=True,snapshots=reports,outsideUnknown=True,reversed=True,invalidRequests=True),indent=2),encoding='utf-8')
print('PASS: 19 native TIFs, independent pixel-boundary lengths, NoData/outside, reverse and invalid requests')
