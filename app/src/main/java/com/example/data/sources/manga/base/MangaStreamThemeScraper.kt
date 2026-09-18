package com.example.data.sources.manga.base

import android.content.Context
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.SourceStatusBus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.example.data.sources.base.BaseMangaSource

abstract class MangaStreamThemeScraper(
    context: Context,
    override val sourceId: String,
    override val sourceName: String,
    override val baseUrl: String,
    private val mangaPath: String = "manga"
) : BaseMangaSource(context) {

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val url = if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/"
            val doc = fetchDocument(url)
            val items = doc.select(".bsx, .bixbox .bs, .listupd .bs, .item, .utao").mapNotNull {
                val link = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val img = it.selectFirst("img")?.let { img ->
                    img.attr("src").ifEmpty { img.attr("data-src") }.ifEmpty { img.attr("data-lazy-src") }
                } ?: ""
                val title = it.selectFirst(".tt, .title, h4, h2")?.text()?.trim() ?: "Unknown"

                ContentItem(
                    id = link,
                    title = title,
                    source = sourceName,
                    imageUrl = img,
                    type = ContentType.COMIC,
                    sourceId = sourceId
                )
            }.distinctBy { it.id }
            SourceStatusBus.clear(sourceId)
            emit(items)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: com.example.data.sources.CloudflareException) {
            throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            emit(emptyList())
        }
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        try {
            val q = java.net.URLEncoder.encode(query, "UTF-8")
            val url = if (page == 1) "$baseUrl/?s=$q" else "$baseUrl/page/$page/?s=$q"
            val doc = fetchDocument(url)
            val items = doc.select(".bsx, .bixbox .bs, .listupd .bs, .item, .utao").mapNotNull {
                val link = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val img = it.selectFirst("img")?.let { img ->
                    img.attr("src").ifEmpty { img.attr("data-src") }.ifEmpty { img.attr("data-lazy-src") }
                } ?: ""
                val title = it.selectFirst(".tt, .title, h4, h2")?.text()?.trim() ?: "Unknown"

                ContentItem(
                    id = link,
                    title = title,
                    source = sourceName,
                    imageUrl = img,
                    type = ContentType.COMIC,
                    sourceId = sourceId
                )
            }
            SourceStatusBus.clear(sourceId)
            emit(items)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: com.example.data.sources.CloudflareException) {
            throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            emit(emptyList())
        }
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        try {
            val doc = fetchDocument(itemId)
            val title = doc.selectFirst(".entry-title, h1[itemprop=name], .tt, h1")?.text()?.trim() ?: "Unknown"
            val img = doc.selectFirst(".thumb img, .infomanga img, .kover img, .bigcover img")?.let { img ->
                img.attr("src").ifEmpty { img.attr("data-src") }
            } ?: ""
            val desc = doc.selectFirst(".entry-content, .desc, .description, .sinopsis, [itemprop=description]")?.text()?.trim() ?: ""
            val genres = doc.select(".mgen a, .genres a, .genre-info a").map { it.text().trim() }

            return ContentItem(
                id = itemId,
                title = title,
                source = sourceName,
                imageUrl = img,
                description = desc,
                genres = genres,
                type = ContentType.COMIC,
                sourceId = sourceId
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            return null
        }
    }

    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        try {
            val doc = fetchDocument(itemId)
            val chapters = doc.select("#chapterlist li, .eplister li, .cl ul li, .lchx, .bxcl ul li").mapNotNull {
                val a = it.selectFirst("a") ?: return@mapNotNull null
                val href = a.attr("href")
                if (href.isBlank()) return@mapNotNull null
                
                val chapNum = it.selectFirst(".chapternum, .epnum")?.text()?.trim() ?: a.text().trim()
                val date = it.selectFirst(".chapterdate, .rightoff")?.text()?.trim() ?: ""
                
                Chapter(id = href, title = chapNum, url = href, date = date)
            }.distinctBy { it.id }
            emit(chapters)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: com.example.data.sources.CloudflareException) {
            throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            emit(emptyList())
        }
    }

    override suspend fun getPagesList(chapterId: String): List<String> {
        try {
            val doc = fetchDocument(chapterId)
            
            // 1. Check for standard reader area images
            var pages = doc.select("#readerarea img, .reader-area img, .ts-main-image").mapNotNull {
                it.attr("src").ifEmpty { it.attr("data-src") }.ifEmpty { it.attr("data-lazy-src") }.trim()
            }.filter { it.isNotBlank() && !it.contains("data:image") && !it.contains("banner") && !it.contains("logo") }
            
            // 2. Fallback to extracting from JS ts_reader
            if (pages.isEmpty()) {
                val scriptData = doc.select("script:containsData(ts_reader)").html()
                if (scriptData.isNotBlank()) {
                    val match = Regex("\"images\":\\[(.*?)\\]").find(scriptData)
                    if (match != null) {
                        val imagesStr = match.groupValues[1]
                        pages = imagesStr.split(",").map { 
                            it.trim().removeSurrounding("\"").replace("\\/", "/") 
                        }.filter { it.isNotBlank() }
                    }
                }
            }
            return pages
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            return emptyList()
        }
    }
}
