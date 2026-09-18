import re

with open('app/src/main/java/com/example/data/sources/WebViewFetcher.kt', 'r') as f:
    content = f.read()

# Disable images in settings
settings_patch = '''
                webView!!.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadsImagesAutomatically = false
                    blockNetworkImage = true
                    userAgentString = NetworkModule.userAgent
                }
'''

content = re.sub(r'webView!!\.settings\.apply \{[\s\S]*?\}', settings_patch.strip(), content)

with open('app/src/main/java/com/example/data/sources/WebViewFetcher.kt', 'w') as f:
    f.write(content)
