path = 'app/src/main/java/com/example/data/db/DownloadDao.kt'
with open(path, 'r') as f:
    content = f.read()

replacements = [
    ('SELECT * FROM downloads ORDER BY priority DESC, createdAt ASC', 'SELECT * FROM downloads WHERE profileId = :profileId ORDER BY priority DESC, createdAt ASC'),
    ('fun getAllDownloads(): Flow<List<DownloadEntity>>', 'fun getAllDownloads(profileId: Long): Flow<List<DownloadEntity>>'),
    ('WHERE sourceId = :sourceId AND mangaId = :mangaId AND chapterId = :chapterId', 'WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId AND chapterId = :chapterId'),
    ('suspend fun getDownload(sourceId: String, mangaId: String, chapterId: String): DownloadEntity?', 'suspend fun getDownload(profileId: Long, sourceId: String, mangaId: String, chapterId: String): DownloadEntity?'),
    ('SELECT * FROM downloads WHERE state IN', 'SELECT * FROM downloads WHERE profileId = :profileId AND state IN'),
    ('suspend fun getPendingDownloads(): List<DownloadEntity>', 'suspend fun getPendingDownloads(profileId: Long): List<DownloadEntity>'),
    ('SELECT * FROM downloads"', 'SELECT * FROM downloads WHERE profileId = :profileId"'),
    ('suspend fun getAllDownloadsSync(): List<DownloadEntity>', 'suspend fun getAllDownloadsSync(profileId: Long): List<DownloadEntity>')
]

for old, new in replacements:
    content = content.replace(old, new)

with open(path, 'w') as f:
    f.write(content)

print("Updated DownloadDao")
