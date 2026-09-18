import re

with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'r') as f:
    text = f.read()

print("Found setCache in PlayerScreen:", "setCache" in text)
