import urllib.request, urllib.parse, json

worker = "https://anime-aggregator.njirlah.workers.dev/?url="
token = "cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4"

manga_id = "bf1ca1b5-0aac-4f44-b042-ad2928994d32"
chapter_id = "4f046ddc-4686-4275-9df9-b6e8131d60a9"

endpoints = [
    f"https://api.shngm.io/v1/manga/detail?manga_id={manga_id}",
    f"https://api.shngm.io/v1/chapter/list?manga_id={manga_id}&page=1&page_size=100",
    f"https://api.shngm.io/v1/chapter/detail?chapter_id={chapter_id}"
]

for u in endpoints:
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
            print(f"[OK] {u} -> len={len(data)}")
            print("Snippet:", data[:250])
    except Exception as e:
        print(f"[ERR] {u} -> {e}")
