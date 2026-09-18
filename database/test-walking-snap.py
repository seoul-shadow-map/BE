"""Independent projected nearest-segment oracle for selection correction."""
import json
import sys
import math
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'data-pipeline'))
from spatial import Transformer
from shapely.geometry import Point,LineString
base='http://127.0.0.1:8080/api/v1'
config=json.load(urlopen(base+'/config'));release=config['context']['releaseId']
meta=json.load(urlopen(base+'/walking-network?releaseId='+release))
graph=json.loads((ROOT/'data/processed/walking-trial/graph.json').read_text())
project=Transformer.from_crs(4326,5186,always_xy=True)
unproject=Transformer.from_crs(5186,4326,always_xy=True)
lines=[LineString([project.transform(*p) for p in e['coordinates']]) for e in graph['edges']]
def call(origin,network=meta['networkId'],status=200):
    body={'releaseId':release,'networkId':network,'origin':origin}
    req=Request(base+'/walking-snap',data=json.dumps(body).encode(),headers={'Content-Type':'application/json'})
    try:response=urlopen(req)
    except HTTPError as error:response=error
    assert response.status==status
    return json.load(response)
distances=[]
for x,y in [(204450,545000),(204470,545120),(204500,545180),(203700,544500),(205000,545250)]:
    lon,lat=unproject.transform(x,y);point=Point(x,y)
    expected=min(line.distance(point) for line in lines)
    result=call({'longitude':lon,'latitude':lat})
    assert abs(result['distanceM']-expected)<.001
    actual=Point(project.transform(result['node']['longitude'],result['node']['latitude']))
    assert min(line.distance(actual) for line in lines)<.001
    assert abs(actual.distance(point)-expected)<.001
    again=call({k:result['node'][k] for k in ('longitude','latitude')})
    assert again['distanceM']<.001
    distances.append(round(expected,2))
assert max(distances)>25
call({'longitude':127,'latitude':37.5},network='old',status=409)
call({'longitude':0,'latitude':0},status=400)
print('PASS: nearest line, corrected point on network, idempotence, >25m, stale version, invalid input.',distances)
