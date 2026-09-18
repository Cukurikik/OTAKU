import os, re

def update_file(path, replacements):
    with open(path, 'r') as f:
        content = f.read()
    for old, new in replacements:
        content = content.replace(old, new)
    with open(path, 'w') as f:
        f.write(content)

update_file('app/src/main/java/com/example/data/models/LibraryModels.kt', [
    ('@Entity(\n    tableName = "library_entries",\n    primaryKeys = ["sourceId", "mangaId"]\n)', '@Entity(\n    tableName = "library_entries",\n    primaryKeys = ["profileId", "sourceId", "mangaId"]\n)'),
    ('data class LibraryEntry(\n    val sourceId: String = "",', 'data class LibraryEntry(\n    val profileId: Long = 1L,\n    val sourceId: String = "",'),
    ('@Entity(\n    tableName = "chapter_entries",\n    primaryKeys = ["sourceId", "mangaId", "chapterId"]\n)', '@Entity(\n    tableName = "chapter_entries",\n    primaryKeys = ["profileId", "sourceId", "mangaId", "chapterId"]\n)'),
    ('data class ChapterEntry(\n    val sourceId: String = "",', 'data class ChapterEntry(\n    val profileId: Long = 1L,\n    val sourceId: String = "",')
])

update_file('app/src/main/java/com/example/data/models/ContentItem.kt', [
    ('data class HistoryItem(\n    @PrimaryKey val id: String = "",', '@Entity(\n    tableName = "history_items",\n    primaryKeys = ["profileId", "id"]\n)\n@Serializable\ndata class HistoryItem(\n    val profileId: Long = 1L,\n    val id: String = "",'),
    ('@Serializable\n@Entity(tableName = "history_items")\ndata class HistoryItem', '// replaced')
])

update_file('app/src/main/java/com/example/data/models/DownloadEntity.kt', [
    ('primaryKeys = ["sourceId", "mangaId", "chapterId"]', 'primaryKeys = ["profileId", "sourceId", "mangaId", "chapterId"]'),
    ('data class DownloadEntity(\n    val sourceId: String,', 'data class DownloadEntity(\n    val profileId: Long = 1L,\n    val sourceId: String,')
])

print("Updated models")
