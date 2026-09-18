"""Independent shortest-distance oracle against the actual HTTP/DB trial API."""
import heapq
import json
import math
import random
import urllib.request
import urllib.error
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = 'http://127.0.0.1:8080/api/v1'
checks = 0
def call(path, body=None, status=200):
    global checks
    req = urllib.request.Request(BASE+path, data=None if body is None else json.dumps(body).encode(),
                                 headers={'Content-Type':'application/json'})
    try: response = urllib.request.urlopen(req,timeout=10)
    except urllib.error.HTTPError as e: response=e
    assert response.status == status, (response.status,status)
    result=json.load(response)
    checks+=1
    return result

release=call('/config')['context']['releaseId']
meta=call('/walking-network?releaseId='+release)
graph=json.loads((ROOT/'data/processed/walking-trial/graph.json').read_text(encoding='utf-8'))
nodes={n['id']:n for n in graph['nodes']}
adj={n:[] for n in nodes}
edges={e['id']:e for e in graph['edges']}
for e in edges.values():
    adj[e['source']].append((e['target'],e['length']))
    adj[e['target']].append((e['source'],e['length']))
def distance(a,b):
    q=[(0,a)];seen={a:0}
    while q:
        d,n=heapq.heappop(q)
        if d!=seen[n]:continue
        if n==b:return d
        for target,cost in adj[n]:
            candidate=d+cost
            if candidate<seen.get(target,math.inf):
                seen[target]=candidate;heapq.heappush(q,(candidate,target))
    return None
def point(n):return {k:n[k] for k in ('longitude','latitude')}
def body(a,b):return dict(releaseId=release,networkId=meta['networkId'],origin=point(a),destination=point(b))
random.seed(16)
for a,b in [random.sample(list(nodes.values()),2) for _ in range(30)]:
    r=call('/walking-routes',body(a,b))
    # Coincident source nodes are allowed; compare against the actual reported snaps.
    start,end=r['origin']['node']['id'],r['destination']['node']['id']
    expected=distance(start,end)
    assert r['navigationApproved'] is False
    assert r['directionPolicy']=='ASSUMED_BIDIRECTIONAL'
    if start==end:assert r['status']=='SAME_NODE';continue
    if expected is None:assert r['status']=='NO_PATH' and r['geometry'] is None;continue
    assert r['status']=='READY' and abs(r['lengthM']-expected)<1e-6
    current=start
    for ident in r['edgeIds']:
        e=edges[ident]
        assert current in (e['source'],e['target'])
        current=e['target'] if current==e['source'] else e['source']
    assert current==end
    assert r['geometry']['coordinates'][0]==[nodes[start]['longitude'],nodes[start]['latitude']]
    assert r['geometry']['coordinates'][-1]==[nodes[end]['longitude'],nodes[end]['latitude']]
a,b=list(nodes.values())[:2]
assert call('/walking-routes',body(a,a))['status']=='SAME_NODE'
invalid=body(a,b);invalid['origin']={'longitude':127.18,'latitude':37.6}
assert call('/walking-routes',invalid)['status']=='OUTSIDE_NETWORK'
invalid=body(a,b);invalid['networkId']='stale';call('/walking-routes',invalid,409)
for p in [None,{}, {'longitude':37.5,'latitude':127}, {'longitude':'not-number','latitude':37.5}]:
    invalid=body(a,b);invalid['origin']=p;call('/walking-routes',invalid,400)
invalid=body(a,b);invalid['releaseId']='invalid';call('/walking-routes',invalid,400)
sys.path.insert(0,str(ROOT/'data-pipeline'))
from spatial import Transformer
from shapely.geometry import LineString
from shapely.ops import transform
from load_review_db import connection
with connection() as conn:
    engine=conn.execute('SELECT pgr_version()').fetchone()[0]
e=max((e for e in graph['edges'] if len(e['coordinates'])==2),key=lambda e:e['length'])
forward=Transformer.from_crs(4326,5186,always_xy=True).transform
inverse=Transformer.from_crs(5186,4326,always_xy=True).transform
line=transform(forward,LineString(e['coordinates']))
middle=[transform(inverse,line.interpolate(t,normalized=True)) for t in (.2,.8)]
for start,end in (middle,list(reversed(middle))):
    r=call('/walking-routes',body(dict(longitude=start.x,latitude=start.y),dict(longitude=end.x,latitude=end.y)))
    assert r['routingEngine']=='pgRouting/pgr_dijkstra'
    assert r['status']=='READY' and abs(r['lengthM']-e['length']*.6)<.001
    assert r['origin']['distanceM']<.001 and r['destination']['distanceM']<.001
out=ROOT/'data/reports/walking-trial';out.mkdir(exist_ok=True)
(out/'api-verification.json').write_text(json.dumps({'passed':True,'httpChecks':checks,'independentOraclePairs':30,'pgroutingVersion':engine,'sameEdgeInteriorBothDirections':True}),encoding='utf-8')
print('Walking trial API checks passed:',checks)
