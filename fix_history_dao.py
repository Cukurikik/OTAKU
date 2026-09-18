path = 'app/src/main/java/com/example/data/db/HistoryDao.kt'
with open(path, 'r') as f:
    c = f.read()

c = c.replace('WHERE profileId = :profileId WHERE profileId = :profileId AND id = :id', 'WHERE profileId = :profileId AND id = :id')

with open(path, 'w') as f:
    f.write(c)

