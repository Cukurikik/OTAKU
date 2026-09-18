path = 'app/src/main/java/com/example/data/models/PageDownloadEntity.kt'
with open(path, 'r') as f:
    c = f.read()

c = c.replace('primaryKeys = ["sourceId", "mangaId", "chapterId", "pageIndex"]', 'primaryKeys = ["profileId", "sourceId", "mangaId", "chapterId", "pageIndex"]')
c = c.replace('parentColumns = ["sourceId", "mangaId", "chapterId"]', 'parentColumns = ["profileId", "sourceId", "mangaId", "chapterId"]')
c = c.replace('childColumns = ["sourceId", "mangaId", "chapterId"]', 'childColumns = ["profileId", "sourceId", "mangaId", "chapterId"]')
c = c.replace('Index("sourceId", "mangaId", "chapterId")', 'Index("profileId", "sourceId", "mangaId", "chapterId")')
c = c.replace('data class PageDownloadEntity(\n    val sourceId: String,', 'data class PageDownloadEntity(\n    val profileId: Long = 1L,\n    val sourceId: String,')

with open(path, 'w') as f:
    f.write(c)

