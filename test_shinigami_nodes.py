import urllib.request, urllib.parse, re

worker = "https://anime-aggregator.njirlah.workers.dev/?url="
token = "cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4"

nodes = ['14.BWXvwJpZ.js', '28.Fc7C73Fu.js', '29.3ZgaO15v.js', '30.6T6-wz5K.js', '31.RSesNNDa.js', '3.BE4kNSF2.js', '4.DVB0OgQl.js']

for n in nodes:
    node_url = f"https://g.shinigami.asia/_app/immutable/nodes/{n}"
    n_req = urllib.request.Request(worker + urllib.parse.quote(node_url), headers={
        "Authorization": f"Bearer {token}",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    })
    try:
        n_data = urllib.request.urlopen(n_req).read().decode('utf-8', errors='ignore')
        matches = re.findall(r'fetch\([`"\']([^`"\']+)`?["\']', n_data)
        if matches:
            print(f"Node {n} fetch:", matches)
        else:
            urls = re.findall(r'https?://[a-zA-Z0-9.-]+(?:/[a-zA-Z0-9._~:/?#\[\]@!$&\'()*+,;=-]*)?', n_data)
            print(f"Node {n} urls:", [u for u in urls if 'ad' not in u and 'schema' not in u][:4])
    except Exception as e:
        print(f"Node {n} err:", e)
