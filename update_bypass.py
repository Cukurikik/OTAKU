import re

with open('app/src/main/java/com/example/ui/screens/CloudflareBypassScreen.kt', 'r') as f:
    content = f.read()

# Disable images in settings
settings_patch = '''
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = false
                    settings.blockNetworkImage = true
                    settings.userAgentString = com.example.data.NetworkModule.userAgent
'''

content = re.sub(r'settings\.javaScriptEnabled = true\s*settings\.domStorageEnabled = true\s*settings\.userAgentString = [^\n]+', settings_patch.strip(), content)

with open('app/src/main/java/com/example/ui/screens/CloudflareBypassScreen.kt', 'w') as f:
    f.write(content)
