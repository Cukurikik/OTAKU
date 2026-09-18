path = 'app/src/main/java/com/example/data/db/HistoryDao.kt'
with open(path, 'r') as f:
    content = f.read()

replacements = [
    ('SELECT * FROM history_items ORDER BY timestamp DESC', 'SELECT * FROM history_items WHERE profileId = :profileId ORDER BY timestamp DESC'),
    ('fun getAllHistory(): Flow<List<HistoryItem>>', 'fun getAllHistory(profileId: Long): Flow<List<HistoryItem>>'),
    ('suspend fun getAllHistorySync(): List<HistoryItem>', 'suspend fun getAllHistorySync(profileId: Long): List<HistoryItem>'),
    ('DELETE FROM history_items WHERE id = :id', 'DELETE FROM history_items WHERE profileId = :profileId AND id = :id'),
    ('suspend fun deleteHistoryById(id: String)', 'suspend fun deleteHistoryById(profileId: Long, id: String)'),
    ('SELECT * FROM history_items WHERE id = :id LIMIT 1', 'SELECT * FROM history_items WHERE profileId = :profileId AND id = :id LIMIT 1'),
    ('fun getHistoryItemSync(id: String): HistoryItem?', 'fun getHistoryItemSync(profileId: Long, id: String): HistoryItem?'),
    ('DELETE FROM history_items', 'DELETE FROM history_items WHERE profileId = :profileId'),
    ('suspend fun deleteAllHistory()', 'suspend fun deleteAllHistory(profileId: Long)')
]

for old, new in replacements:
    content = content.replace(old, new)

with open(path, 'w') as f:
    f.write(content)

print("Updated HistoryDao")
