import re

path = 'app/src/main/java/com/example/data/db/ChapterDao.kt'
with open(path, 'r') as f:
    content = f.read()

replacements = [
    ('WHERE sourceId = :sourceId AND mangaId = :mangaId', 'WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId'),
    ('fun getChapters(sourceId: String, mangaId: String): Flow<List<ChapterEntry>>', 'fun getChapters(profileId: Long, sourceId: String, mangaId: String): Flow<List<ChapterEntry>>'),
    ('suspend fun getChaptersList(sourceId: String, mangaId: String): List<ChapterEntry>', 'suspend fun getChaptersList(profileId: Long, sourceId: String, mangaId: String): List<ChapterEntry>'),
    ('suspend fun getChapter(sourceId: String, mangaId: String, chapterId: String): ChapterEntry?', 'suspend fun getChapter(profileId: Long, sourceId: String, mangaId: String, chapterId: String): ChapterEntry?'),
    ('suspend fun updateProgress(sourceId: String, mangaId: String, chapterId: String, lastPageRead: Int, lastReadAt: Long, isRead: Boolean)', 'suspend fun updateProgress(profileId: Long, sourceId: String, mangaId: String, chapterId: String, lastPageRead: Int, lastReadAt: Long, isRead: Boolean)'),
    ('suspend fun getUnreadCount(sourceId: String, mangaId: String): Int', 'suspend fun getUnreadCount(profileId: Long, sourceId: String, mangaId: String): Int'),
    ('suspend fun markAllChaptersAsRead(sourceId: String, mangaId: String)', 'suspend fun markAllChaptersAsRead(profileId: Long, sourceId: String, mangaId: String)'),
    ('suspend fun updateChapterReadStatus(sourceId: String, mangaId: String, chapterId: String, read: Boolean)', 'suspend fun updateChapterReadStatus(profileId: Long, sourceId: String, mangaId: String, chapterId: String, read: Boolean)'),
    ('SELECT sourceId FROM chapter_entries WHERE read = 1', 'SELECT sourceId FROM chapter_entries WHERE profileId = :profileId AND read = 1'),
    ('fun getReadChaptersSourceIds(): Flow<List<String>>', 'fun getReadChaptersSourceIds(profileId: Long): Flow<List<String>>'),
    ('SELECT * FROM chapter_entries"', 'SELECT * FROM chapter_entries WHERE profileId = :profileId"'),
    ('suspend fun getAllChaptersSync(): List<ChapterEntry>', 'suspend fun getAllChaptersSync(profileId: Long): List<ChapterEntry>'),
    ('fun getAllChaptersFlow(): Flow<List<ChapterEntry>>', 'fun getAllChaptersFlow(profileId: Long): Flow<List<ChapterEntry>>'),
    ('SELECT * FROM chapter_entries WHERE read = 1"', 'SELECT * FROM chapter_entries WHERE profileId = :profileId AND read = 1"'),
    ('fun getReadChaptersFlow(): Flow<List<ChapterEntry>>', 'fun getReadChaptersFlow(profileId: Long): Flow<List<ChapterEntry>>'),
    ('suspend fun getReadChaptersSync(): List<ChapterEntry>', 'suspend fun getReadChaptersSync(profileId: Long): List<ChapterEntry>')
]

for old, new in replacements:
    content = content.replace(old, new)

with open(path, 'w') as f:
    f.write(content)

print("Updated ChapterDao")
