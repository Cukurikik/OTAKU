import urllib.request, urllib.parse, json

worker = "https://anime-aggregator.njirlah.workers.dev/?url="
token = "cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4"
chapter_id = "4f046ddc-4686-4275-9df9-b6e8131d60a9"
u = f"https://api.shngm.io/v1/chapter/detail/{chapter_id}"

target = worker + urllib.parse.quote(u)
req = urllib.request.Request(target, headers={
    "Authorization": f"Bearer {token}",
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": "https://g.shinigami.asia/",
    "Origin": "https://g.shinigami.asia"
})
data = urllib.request.urlopen(req).read().decode('utf-8')
obj = json.loads(data)
print("Data keys:", obj.get("data", {}).keys())
print("Data content preview:", json.dumps(obj.get("data", {}), indent=2)[:500])
