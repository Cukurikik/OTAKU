import re

with open('app/src/main/java/com/example/data/sources/drama/base/WpDramaThemeScraper.kt', 'r') as f:
    content = f.read()

# Remove fetchDocument
content = re.sub(r'// Fallback if needed.*\}', '', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/sources/drama/base/WpDramaThemeScraper.kt', 'w') as f:
    f.write(content)
