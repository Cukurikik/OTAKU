import urllib.request, urllib.parse, re

worker = "https://anime-aggregator.njirlah.workers.dev/?url="
token = "cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4"

u = "https://g.shinigami.asia/_app/immutable/entry/app.DgAKayyX.js"
req = urllib.request.Request(worker + urllib.parse.quote(u), headers={
    "Authorization": f"Bearer {token}",
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
})
data = urllib.request.urlopen(req).read().decode('utf-8', errors='ignore')
nodes = re.findall(r'nodes/(\d+\.[a-zA-Z0-9_-]+\.js)', data)

all_endpoints = set()
for n in nodes:
    node_url = f"https://g.shinigami.asia/_app/immutable/nodes/{n}"
    n_req = urllib.request.Request(worker + urllib.parse.quote(node_url), headers={
        "Authorization": f"Bearer {token}",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    })
    try:
        n_data = urllib.request.urlopen(n_req).read().decode('utf-8', errors='ignore')
        matches = re.findall(r'https://api\.shngm\.io[a-zA-Z0-9_/.-]+', n_data)
        for m in matches:
            all_endpoints.add(m)
        # also find relative api paths like /v1/...
        v1_matches = re.findall(r'["\'](/v1/[a-zA-Z0-9_/?&=.-]+)["\']', n_data)
        for v in v1_matches:
            all_endpoints.add(v)
    except Exception as e:
        pass

print("Discovered Shinigami endpoints:")
for ep in sorted(all_endpoints):
    print(" ", ep)
