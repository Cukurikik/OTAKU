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
                val img = UniversalMangaDomExtractor.extractImageUrl(it, baseUrl)
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
                val img = UniversalMangaDomExtractor.extractImageUrl(it, baseUrl)
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
            val img = UniversalMangaDomExtractor.extractImageUrl(doc.selectFirst(".thumb img, .infomanga img, .kover img, .bigcover img") ?: doc, baseUrl)
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
            return UniversalMangaDomExtractor.extractChapterPages(doc, chapterId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            return emptyList()
        }
    }
}
