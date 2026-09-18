package com.example.data.sources.tachiyomi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.ContentSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.asFlow
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class TachiyomiExtensionAdapter(
    private val mangaSource: Source,
    private val pkgName: String
) : ContentSource {

    override val sourceId: String = "${pkgName}_${mangaSource.id}"
    override val sourceName: String = mangaSource.name
    
    override fun getRecommendations(page: Int): Flow<List<ContentItem>> {
        return mangaSource.fetchPopularManga(page).asFlow().map { mangasPage ->
            mangasPage.mangas.map { sManga ->
                ContentItem(
                    id = sManga.url,
                    title = sManga.title,
                    source = sourceName,
                    imageUrl = sManga.thumbnail_url ?: "",
                    type = ContentType.COMIC,
                    sourceId = sourceId,
                    description = sManga.description ?: "",
                    genres = sManga.genre?.split(",")?.map { it.trim() } ?: emptyList()
                )
            }
        }
    }
    
    override fun search(query: String, page: Int): Flow<List<ContentItem>> {
        return mangaSource.fetchSearchManga(page, query, FilterList()).asFlow().map { mangasPage ->
            mangasPage.mangas.map { sManga ->
                ContentItem(
                    id = sManga.url,
                    title = sManga.title,
                    source = sourceName,
                    imageUrl = sManga.thumbnail_url ?: "",
                    type = ContentType.COMIC,
                    sourceId = sourceId,
                    description = sManga.description ?: "",
                    genres = sManga.genre?.split(",")?.map { it.trim() } ?: emptyList()
                )
            }
        }
    }
    
    override suspend fun getDetail(itemId: String): ContentItem? {
        return withContext(Dispatchers.IO) {
            try {
                val initialManga = SManga.create().apply { url = itemId }
                var detailedManga: SManga? = null
                mangaSource.fetchMangaDetails(initialManga).asFlow().collect { detailedManga = it }
                detailedManga?.let { sManga ->
                    ContentItem(
                        id = sManga.url,
                        title = sManga.title,
                        source = sourceName,
                        imageUrl = sManga.thumbnail_url ?: "",
                        type = ContentType.COMIC,
                    sourceId = sourceId,
                        description = sManga.description ?: "",
                        genres = sManga.genre?.split(",")?.map { it.trim() } ?: emptyList()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ContentItem(
                    id = itemId, title = "Data Unavailable (IP Blocked)", source = sourceName,
                    imageUrl = "", type = ContentType.COMIC,
                    sourceId = sourceId,
                    description = "Failed to load detail from extension. Emulator IP might be blocked by Cloudflare. Download the APK to view chapters.", genres = emptyList()
                )
            }
        }
    }
    
    override fun getChapters(itemId: String): Flow<List<Chapter>> {
        val initialManga = SManga.create().apply { url = itemId }
        return mangaSource.fetchChapterList(initialManga).asFlow().map { sChapters ->
            val mapped = sChapters.map { sChapter ->
                Chapter(
                    id = sChapter.url,
                    title = sChapter.name,
                    url = sChapter.url,
                    date = sChapter.date_upload.toString()
                )
            }
            if (mapped.isEmpty()) throw Exception("Empty chapters in Tachiyomi")
            mapped
        }
    }
    
    override fun getPages(chapterId: String): Flow<List<com.example.data.models.Page>> {
        val initialChapter = SChapter.create().apply { url = chapterId }
        return mangaSource.fetchPageList(initialChapter).asFlow().map { sPages ->
            val mapped = sPages.map { sPage ->
                com.example.data.models.Page(url = sPage.imageUrl ?: sPage.url)
            }
            if (mapped.isEmpty()) throw Exception("Empty pages in Tachiyomi")
            mapped
        }
    }
    
    companion object {
        fun loadAniyomiSource(pkgName: String, factory: String, context: android.content.Context): ContentSource? {
            return try {
                val cls = context.classLoader.loadClass(factory)
                val instance = try {
                    cls.getDeclaredConstructor(android.content.Context::class.java).newInstance(context)
                } catch (e: Exception) {
                    cls.getDeclaredConstructor().newInstance()
                }
                if (instance is Source) {
                    TachiyomiExtensionAdapter(instance, pkgName)
                } else {
                    null
                }
            } catch (e: Exception) { null }
        }
    }
}
