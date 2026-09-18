import urllib.request
import urllib.parse
import re

worker = "https://anime-aggregator.njirlah.workers.dev/?url="
token = "cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4"

def check(url):
    print(f"\n--- Checking {url} ---")
    target = worker + urllib.parse.quote(url)
    req = urllib.request.Request(target, headers={
        "Authorization": f"Bearer {token}",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    })
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = resp.read().decode('utf-8', errors='ignore')
            title = re.search(r'<title>(.*?)</title>', data, re.I)
            print("Title:", title.group(1) if title else "NO TITLE")
            print("Length:", len(data))
            # Find links
            links = re.findall(r'href=["\'](https?://[^"\']+|/[^"\']+)["\']', data)
            print("Total links:", len(links))
            comic_links = [l for l in links if any(k in l for k in ['/manga/', '/komik/', '/series/', '/manhwa/'])]
            print("Comic links sample:", list(set(comic_links))[:5])
    except Exception as e:
        print("Error:", e)

check("https://01.apkomik.com/")
check("https://g.shinigami.asia/")
check("https://doujinku.org/")
check("https://01.komiku.asia/")
check("https://komikcast.bz/")
check("https://komikcast.cz/")
check("https://komikcast.ch/")
