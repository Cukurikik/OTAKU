path = 'app/src/main/java/com/example/data/db/LibraryDao.kt'
with open(path, 'r') as f:
    c = f.read()

c = c.replace('AND mangaId = library_entries.mangaId AND read = 0), lastUpdated = :lastUpdated"', 'AND mangaId = library_entries.mangaId AND read = 0), lastUpdated = :lastUpdated WHERE profileId = :profileId"')

with open(path, 'w') as f:
    f.write(c)

