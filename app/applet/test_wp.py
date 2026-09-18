import urllib.request
import ssl
from bs4 import BeautifulSoup

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

url = "https://01.apkomik.com/manga/?page=1&order=update"
try:
    req = urllib.request.Request(
        url, 
        headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}
    )
    with urllib.request.urlopen(req, context=ctx, timeout=10) as response:
        html = response.read()
        print(f"SUCCESS: {url} - Length: {len(html)}")
        soup = BeautifulSoup(html, 'html.parser')
        
        # Test WPManga selector: div.bsx, .uta, div.utao, div.bs, div.item-thumb
        elements = soup.select("div.bsx, .uta, div.utao, div.bs, div.item-thumb")
        print(f"Selector count: {len(elements)}")
        for el in elements[:3]:
            a = el.select_one("a")
            title = a.get("title") if a else "No a tag"
            href = a.get("href") if a else "No a tag"
            print(f"Title: {title}, Href: {href}")
            
except Exception as e:
    print(f"FAILED: {url} - Error: {e}")
