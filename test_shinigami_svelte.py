import urllib.request, urllib.parse, re, json

worker = "https://anime-aggregator.njirlah.workers.dev/?url="
token = "cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4"

for endpoint in [
    "https://g.shinigami.asia/__data.json",
    "https://g.shinigami.asia/explore/__data.json",
    "https://g.shinigami.asia/api/v1/series",
    "https://g.shinigami.asia/api/v1/latest",
    "https://g.shinigami.asia/api/series"
]:
    target = worker + urllib.parse.quote(endpoint)
    req = urllib.request.Request(target, headers={
        "Authorization": f"Bearer {token}",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    })
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = resp.read()
            print(f"[OK] {endpoint} -> status={resp.status}, len={len(data)}, snippet={data[:200]}")
    except Exception as e:
        print(f"[ERR] {endpoint} -> {e}")

# Also let us inspect the bottom of https://g.shinigami.asia/
u = "https://g.shinigami.asia/"
req = urllib.request.Request(worker + urllib.parse.quote(u), headers={
    "Authorization": f"Bearer {token}",
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
})
html = urllib.request.urlopen(req).read().decode('utf-8', errors='ignore')
print("\nBottom of Shinigami HTML:")
print(html[-2000:])
