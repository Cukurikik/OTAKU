import re

with open('./app/src/main/java/com/example/ui/screens/ReaderScreen.kt', 'r') as f:
    content = f.read()

# Replace ZoomableImage( calls to add enableSmartPanel parameter before modifier, or at the end
# It's better to just regex replace `modifier = Modifier...` with `enableSmartPanel = enableSmartPanel, modifier = ...` only inside ZoomableImage

def repl(match):
    return match.group(0) + ",\n                                        enableSmartPanel = enableSmartPanel"

content = re.sub(r'contentScale = scale,\s*modifier = Modifier\.[a-zA-Z0-9().]+', repl, content)

with open('./app/src/main/java/com/example/ui/screens/ReaderScreen.kt', 'w') as f:
    f.write(content)

