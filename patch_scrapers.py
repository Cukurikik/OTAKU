import os
import re

dirs = ['app/src/main/java/com/example/data/sources/manga/', 'app/src/main/java/com/example/data/sources/drama/']
exception_str = 'throw Exception("Situs ini telah mengubah strukturnya. Silakan ganti server atau hubungi developer.")'

for d in dirs:
    for root, _, files in os.walk(d):
        for f in files:
            if not f.endswith('.kt'): continue
            path = os.path.join(root, f)
            with open(path, 'r') as file:
                content = file.read()
            
            # Find catch blocks
            def replace_in_catch(match):
                catch_block = match.group(0)
                catch_block = catch_block.replace('emit(emptyList())', exception_str)
                catch_block = catch_block.replace('return emptyList()', exception_str)
                # Also replace `return null` with `throw Exception(...)` in getDetail catch blocks
                # But only if it's returning a single item null
                if 'getDetail' in content:
                    catch_block = catch_block.replace('return null', exception_str)
                return catch_block
            
            content = re.sub(r'catch\s*\([^{]+\)\s*\{[^}]+\}', replace_in_catch, content)

            # Insert empty checks before emitting/returning for chapters/pages
            # E.g., `emit(chapters)` -> `if (chapters.isEmpty()) throw Exception(...)\nemit(chapters)`
            
            content = re.sub(r'emit\((chapters|pages|episodes)\)', r'if (\1.isEmpty()) ' + exception_str + r'\n            emit(\1)', content)
            
            content = re.sub(r'return\s+(pages)', r'if (\1.isEmpty()) ' + exception_str + r'\n            return \1', content)
            
            with open(path, 'w') as file:
                file.write(content)
