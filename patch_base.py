import re
import os

# BaseMangaSource
with open('app/src/main/java/com/example/data/sources/base/BaseMangaSource.kt', 'r') as f:
    content = f.read()
content = content.replace('emit(emptyList())', 'throw Exception("Situs ini telah mengubah strukturnya. Silakan ganti server atau hubungi developer.")')
with open('app/src/main/java/com/example/data/sources/base/BaseMangaSource.kt', 'w') as f:
    f.write(content)

# BaseDramaSource
with open('app/src/main/java/com/example/data/sources/base/BaseDramaSource.kt', 'r') as f:
    content = f.read()

# Make BaseDramaSource use fetchDocument if it doesn't already
if 'fun fetchDocument' not in content:
    fetch_doc = '''
    protected suspend fun fetchDocument(url: String): org.jsoup.nodes.Document {
        var doc = kotlin.runCatching { fetcher.fetchHtml(url) }.getOrNull()
        val title = doc?.title()?.lowercase() ?: ""
        if (doc == null || title.contains("just a moment") || title.contains("cloudflare") || title.contains("access denied") || title.contains("302 found") || doc.text().contains("cloudflare")) {
            doc = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                kotlinx.coroutines.withTimeoutOrNull(25000L) {
                    kotlin.runCatching { fetcher.fetchHtmlRendered(url, 12000L) }.getOrNull()
                }
            }
        }
        return doc ?: throw Exception("Failed to fetch $url or Cloudflare bypass failed.")
    }
'''
    content = content.replace('protected fun currentBaseUrl(): String = effectiveBaseUrl(context)', 'protected fun currentBaseUrl(): String = effectiveBaseUrl(context)\n' + fetch_doc)

content = content.replace('fetcher.fetchHtml(itemId)', 'fetchDocument(itemId)')
content = content.replace('fetcher.fetchHtml(episodeId)', 'fetchDocument(episodeId)')

content = content.replace('emit(emptyList())', 'throw Exception("Situs ini telah mengubah strukturnya. Silakan ganti server atau hubungi developer.")')

# Empty check for episodes
if 'if (episodes.isEmpty()) throw Exception("Situs ini telah mengubah strukturnya. Silakan ganti server atau hubungi developer.")' not in content:
    content = content.replace('SourceStatusBus.clear(sourceId)\n            emit(episodes)', 'if (episodes.isEmpty()) throw Exception("Situs ini telah mengubah strukturnya. Silakan ganti server atau hubungi developer.")\n            SourceStatusBus.clear(sourceId)\n            emit(episodes)')

with open('app/src/main/java/com/example/data/sources/base/BaseDramaSource.kt', 'w') as f:
    f.write(content)
