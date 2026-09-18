import re

with open('app/src/main/java/com/example/data/sources/drama/GenericDramaScraper.kt', 'r') as f:
    content = f.read()

exception_str = 'throw Exception("Situs ini telah mengubah strukturnya. Silakan ganti server atau hubungi developer.")'

content = content.replace('val d = engine.detail(itemId) ?: return null', f'val d = engine.detail(itemId) ?: {exception_str}')
content = content.replace('catch(e: Exception) {}', f'catch(e: Exception) {{ {exception_str} }}')

with open('app/src/main/java/com/example/data/sources/drama/GenericDramaScraper.kt', 'w') as f:
    f.write(content)
