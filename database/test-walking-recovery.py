"""Audit source-backed repairs and verify formerly disconnected pairs via pgRouting."""
import collections
import csv
import json
import sys
import urllib.request
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'data-pipeline'))
import spatial
from shapely import wkt
from load_review_db import connection

def components(graph):
    adj=collections.defaultdict(set)
    for e in graph['edges']:
        adj[e['source']].add(e['target']);adj[e['target']].add(e['source'])
    labels={}
    for n in adj:
        if n in labels: continue
        label=len(set(labels.values()));todo=[n];labels[n]=label
        while todo:
            for neighbor in adj[todo.pop()]:
                if neighbor not in labels: labels[neighbor]=label;todo.append(neighbor)
    return labels

folder=ROOT/'data/processed/walking-trial'
graph=json.loads((folder/'graph.json').read_text())
repairs=json.loads((folder/'node-recovery.json').read_text())
with (ROOT/'data/processed/network/walk_link-staging.csv').open(encoding='utf-8-sig') as f:
    source={r['링크 ID']:r for r in csv.DictReader(f)}
for repair in repairs:
    assert len(set(repair['sourceLinkIds']))>=2
    for eid in repair['sourceLinkIds']:
        row=source[eid];geom=wkt.loads(row['geom_5186_wkt'])
        xy=geom.coords[0] if row['source_ref']==repair['id'] else geom.coords[-1]
        assert tuple(repair['coordinates'])==tuple(xy)
with connection() as c:
    release=c.execute('SELECT id FROM shade_api.release WHERE active').fetchone()[0]
    old=c.execute('''SELECT network_id,graph FROM shade_api.walking_trial_revision
        WHERE release_id=%s ORDER BY registered_at LIMIT 1''',(release,)).fetchone()
    current=c.execute('SELECT network_id FROM shade_api.walking_trial WHERE release_id=%s',(release,)).fetchone()[0]
before,after=components(old[1]),components(graph)
assert {e['id'] for e in old[1]['edges']} <= {e['id'] for e in graph['edges']}
new_edges={e['id'] for e in graph['edges']}-{e['id'] for e in old[1]['edges']}
nodes={n['id']:n for n in graph['nodes']}
pairs={}
for a in before:
    for b in before:
        if before[a]!=before[b] and after[a]==after[b]:
            pairs.setdefault(tuple(sorted([before[a],before[b]])),(a,b))
verified=[]
for a,b in pairs.values():
    for start,end in [(a,b),(b,a)]:
        body={'releaseId':str(release),'networkId':current,
              'origin':{k:nodes[start][k] for k in ['longitude','latitude']},
              'destination':{k:nodes[end][k] for k in ['longitude','latitude']}}
        request=urllib.request.Request('http://127.0.0.1:8080/api/v1/walking-routes',
            data=json.dumps(body).encode(),headers={'Content-Type':'application/json'})
        response=json.load(urllib.request.urlopen(request))
        assert response['status']=='READY',response
        assert response['routingEngine']=='pgRouting/pgr_dijkstra'
        verified.append({'origin':start,'destination':end,'lengthM':response['lengthM']})
assert verified
report={'oldComponents':len(set(before.values())),'newComponents':len(set(after.values())),
        'recoveredNodes':len(repairs),'restoredEdges':len(new_edges),'verifiedRoutes':verified,
        'remainingComponentSizes':sorted(collections.Counter(after.values()).values(),reverse=True),
        'oldNetworkId':old[0],'newNetworkId':current}
(ROOT/'data/reports/walking-trial/connectivity-repair.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report))
