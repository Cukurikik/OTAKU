import urllib.request, urllib.parse, re

worker = "https://anime-aggregator.njirlah.workers.dev/?url="
token = "cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4"

for u in ["https://01.apkomik.com/", "https://web1.mgkomik.cc/", "https://01.komiku.asia/"]:
    target = worker + urllib.parse.quote(u)
    req = urllib.request.Request(target, headers={
        "Authorization": f"Bearer {token}",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    })
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = resp.read().decode('utf-8', errors='ignore')
            title = re.search(r'<title>(.*?)</title>', data, re.I)
            t_str = title.group(1).strip() if title else "NO TITLE"
            print(f"[OK] {u} -> len={len(data)}, title='{t_str[:40]}'")
    except Exception as e:
        print(f"[ERR] {u} -> {e}")
