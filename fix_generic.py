path = 'app/src/main/java/com/example/data/sources/anime/GenericAnimeScraper.kt'
with open(path, 'r') as f:
    c = f.read()

c = c.replace('super.getChapters(com.example.data.ProfileManager.currentProfileIdValue, ', 'super.getChapters(')

with open(path, 'w') as f:
    f.write(c)
