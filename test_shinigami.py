import urllib.request, urllib.parse, re

worker = "https://anime-aggregator.njirlah.workers.dev/?url="
token = "cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4"

u = "https://g.shinigami.asia/"
req = urllib.request.Request(worker + urllib.parse.quote(u), headers={
    "Authorization": f"Bearer {token}",
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
})
html = urllib.request.urlopen(req).read().decode('utf-8', errors='ignore')

print("Shinigami HTML snippet:\n", html[:1500])

# Cek scripts atau data JSON di shinigami (apakah ada SvelteKit / Next / API)
scripts = re.findall(r'<script[^>]*>([\s\S]*?)</script>', html)
print(f"Found {len(scripts)} scripts")
for s in scripts:
    if "api" in s.lower() or "series" in s.lower() or "page" in s.lower():
        print("Script match:", s[:200])

