import urllib.request, urllib.parse, json

worker = "https://anime-aggregator.njirlah.workers.dev/?url="
token = "cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4"

for u in [
    "https://api.shngm.io/v1/manga/list?page=1&page_size=10&sort=latest&sort_order=desc",
    "https://api.shngm.io/v1/manga/top?time_range=week"
]:
    target = worker + urllib.parse.quote(u)
    req = urllib.request.Request(target, headers={
        "Authorization": f"Bearer {token}",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer": "https://g.shinigami.asia/",
        "Origin": "https://g.shinigami.asia"
    })
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = resp.read().decode('utf-8')
            print(f"[OK] {u} -> length: {len(data)}")
            obj = json.loads(data)
            print("Keys:", obj.keys())
            if "data" in obj:
                print("First item:", json.dumps(obj["data"][:1], indent=2))
    except Exception as e:
        print(f"[ERR] {u} -> {e}")
