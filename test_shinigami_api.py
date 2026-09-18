import urllib.request, urllib.parse, re

worker = "https://anime-aggregator.njirlah.workers.dev/?url="
token = "cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4"

u = "https://g.shinigami.asia/_app/immutable/entry/app.DgAKayyX.js"
req = urllib.request.Request(worker + urllib.parse.quote(u), headers={
    "Authorization": f"Bearer {token}",
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
})
data = urllib.request.urlopen(req).read().decode('utf-8', errors='ignore')
print("App JS length:", len(data))

# Find node chunks
nodes = re.findall(r'nodes/(\d+\.[a-zA-Z0-9_-]+\.js)', data)
print("Found nodes:", nodes)

for n in nodes[:6]:
    node_url = f"https://g.shinigami.asia/_app/immutable/nodes/{n}"
    n_req = urllib.request.Request(worker + urllib.parse.quote(node_url), headers={
        "Authorization": f"Bearer {token}",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    })
    try:
        n_data = urllib.request.urlopen(n_req).read().decode('utf-8', errors='ignore')
        api_matches = re.findall(r'["\'](/api/[^"\']+|https?://[^"\']+)["\']', n_data)
        if api_matches:
            print(f"Node {n} endpoints:", [m for m in api_matches if 'api' in m or 'series' in m or 'v1' in m][:5])
    except Exception as e:
        print(f"Node {n} err:", e)
