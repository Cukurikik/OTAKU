import urllib.request, urllib.parse, re, json

worker = "https://anime-aggregator.njirlah.workers.dev/?url="
token = "cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4"

def fetch_page_props(url):
    target = worker + urllib.parse.quote(url)
    req = urllib.request.Request(target, headers={
        "Authorization": "Bearer " + token,
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    })
    with urllib.request.urlopen(req, timeout=10) as resp:
        data = resp.read().decode('utf-8', errors='ignore')
        m = re.search(r'data-page=[\"\x27]([\s\S]*?)[\"\x27]', data)
        if m:
            val = m.group(1).replace("&quot;", "\"").replace("&amp;", "&")
            return json.loads(val).get('props', {})
    return {}

# 1. Browse
home_props = fetch_page_props("https://komikcast.app/")
latest = home_props.get('latestMangaUpdates', [])
print("Browse items count:", len(latest))
item = latest[0]
print("First item:", item['title'], "| Slug:", item['slug'], "| Poster:", item['poster'])

# 2. Detail
detail_props = fetch_page_props(f"https://komikcast.app/manga/{item['slug']}")
manga = detail_props.get('manga', {})
print("Detail title:", manga.get('title'), "| Chapters count:", len(manga.get('chapters', [])))
first_ch = manga.get('chapters', [])[0]
print("First chapter number:", first_ch.get('chapter_number'), "| slug:", first_ch.get('slug'))

# 3. Pages
read_props = fetch_page_props(f"https://komikcast.app/manga/{item['slug']}/chapter/{first_ch.get('chapter_number')}")
ch = read_props.get('chapter', {})
images = ch.get('images', [])
print(f"Pages count: {len(images)}, Sample image 1: {images[0]['image_path'] if images else 'NONE'}")
