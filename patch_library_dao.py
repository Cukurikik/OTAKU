import os

path = 'app/src/main/java/com/example/data/db/LibraryDao.kt'
with open(path, 'r') as f:
    content = f.read()

# Replace all queries
replacements = [
    ('SELECT * FROM library_entries ORDER BY title ASC', 'SELECT * FROM library_entries WHERE profileId = :profileId ORDER BY title ASC'),
    ('fun getAllLibrary(): Flow<List<LibraryEntry>>', 'fun getAllLibrary(profileId: Long): Flow<List<LibraryEntry>>'),
    ('suspend fun getAllEntriesSync(): List<LibraryEntry>', 'suspend fun getAllEntriesSync(profileId: Long): List<LibraryEntry>'),
    ('WHERE sourceId = :sourceId AND mangaId = :mangaId', 'WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId'),
    ('suspend fun getLibraryEntry(sourceId: String, mangaId: String): LibraryEntry?', 'suspend fun getLibraryEntry(profileId: Long, sourceId: String, mangaId: String): LibraryEntry?'),
    ('fun observeLibraryEntry(sourceId: String, mangaId: String): Flow<LibraryEntry?>', 'fun observeLibraryEntry(profileId: Long, sourceId: String, mangaId: String): Flow<LibraryEntry?>'),
    ('suspend fun deleteLibraryEntry(sourceId: String, mangaId: String)', 'suspend fun deleteLibraryEntry(profileId: Long, sourceId: String, mangaId: String)'),
    ('suspend fun updateUnreadCount(sourceId: String, mangaId: String, lastUpdated: Long = System.currentTimeMillis())', 'suspend fun updateUnreadCount(profileId: Long, sourceId: String, mangaId: String, lastUpdated: Long = System.currentTimeMillis())'),
    ('WHERE sourceId = library_entries.sourceId', 'WHERE profileId = library_entries.profileId AND sourceId = library_entries.sourceId'),
    ('suspend fun updateAllUnreadCounts(lastUpdated: Long = System.currentTimeMillis())', 'suspend fun updateAllUnreadCounts(profileId: Long, lastUpdated: Long = System.currentTimeMillis())'),
    ('WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId AND read = 0', 'WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId AND read = 0') # Fix nested select if needed
]

for old, new in replacements:
    content = content.replace(old, new)

# Special fix for updateUnreadCount nested query
content = content.replace(
    'SELECT COUNT(*) FROM chapter_entries WHERE profileId = :profileId AND sourceId = :sourceId',
    'SELECT COUNT(*) FROM chapter_entries WHERE profileId = :profileId AND sourceId = :sourceId'
)

with open(path, 'w') as f:
    f.write(content)

print("Updated LibraryDao")
