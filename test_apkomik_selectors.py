import urllib.request, urllib.parse, re

worker = "https://anime-aggregator.njirlah.workers.dev/?url="
token = "cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4"

u = "https://01.apkomik.com/"
req = urllib.request.Request(worker + urllib.parse.quote(u), headers={
    "Authorization": f"Bearer {token}",
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
})
html = urllib.request.urlopen(req).read().decode('utf-8', errors='ignore')

print("Contains .page-item-detail:", ".page-item-detail" in html)
print("Contains .item-summary:", ".item-summary" in html)
print("Contains .bsx:", ".bsx" in html)
print("Contains .listupd:", ".listupd" in html)
print("Contains .bixbox:", ".bixbox" in html)
print("Contains /manga/:", "/manga/" in html)

# Cari blok komik di homepage apkomik
blocks = re.findall(r'<div class="([^"]*(?:bsx|item|manga|post|card|listupd)[^"]*)"', html, re.I)
print("Matching divs:", list(set(blocks))[:10])

# Cari link dan judulnya
sample_links = re.findall(r'<a[^>]+href="([^"]*/manga/[^"]*)"[^>]*>([\s\S]*?)</a>', html, re.I)
print("Sample a with /manga/:", len(sample_links))
for href, inner in sample_links[:5]:
    # find title or text
    print("Link:", href, "-> inner snippet:", re.sub(r'<[^>]+>', ' ', inner).strip()[:40])

