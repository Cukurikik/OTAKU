import urllib.request, urllib.parse, json

worker = "https://anime-aggregator.njirlah.workers.dev/?url="
token = "cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4"

def get(url):
    target = worker + urllib.parse.quote(url)
    req = urllib.request.Request(target, headers={
        "Authorization": "Bearer " + token,
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": "https://g.shinigami.asia/",
        "Origin": "https://g.shinigami.asia"
    })
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode('utf-8'))

# 1. List
res = get("https://api.shngm.io/v1/manga/list?page=1&page_size=3&sort=latest&sort_order=desc")
first = res['data'][0]
print("Manga:", first['title'], "| ID:", first['manga_id'], "| Cover:", first['cover_image_url'])

# 2. Detail
det = get(f"https://api.shngm.io/v1/manga/detail/{first['manga_id']}")
print("Detail Title:", det['data']['title'], "| Genres:", [g['name'] for g in det['data'].get('taxonomy', {}).get('Genre', [])])

# 3. Chapters
ch_res = get(f"https://api.shngm.io/v1/chapter/{first['manga_id']}/list?page=1&page_size=10")
print("Total chapters returned:", len(ch_res.get('data', [])))
first_ch = ch_res['data'][0]
print("First Chapter:", first_ch['chapter_number'], "| ID:", first_ch['chapter_id'])

# 4. Pages
p_res = get(f"https://api.shngm.io/v1/chapter/detail/{first_ch['chapter_id']}")
base_url = p_res['data']['base_url']
path = p_res['data']['chapter']['path']
files = p_res['data']['chapter']['data']
full_pages = [f"{base_url}{path}{f}" for f in files]
print(f"Pages count: {len(full_pages)}, Sample page 1: {full_pages[0]}")
