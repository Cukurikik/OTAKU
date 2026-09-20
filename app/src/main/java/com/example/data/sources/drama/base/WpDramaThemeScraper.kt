package com.example.data.sources.drama.base

import android.content.Context
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.base.BaseDramaSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jsoup.nodes.Element

abstract class WpDramaThemeScraper(
    context: Context,
    override val sourceId: String,
    override val sourceName: String,
    override val baseUrl: String,
    private val itemSelector: String = ".post-item, .item, .bs, article, .gmr-item-modulepost, .movie-item, .video-block",
    private val titleSelector: String = "h1, h2, h3, h4, h5, .title, .tt, .entry-title, .movie-content h3",
    private val imageSelector: String = "img",
    private val linkSelector: String = "a",
    private val searchPath: String = "/?s="
) : BaseDramaSource(context) {

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        var targetUrl = ""
        try {
            targetUrl = if (page == 1) currentBaseUrl() else "${currentBaseUrl()}/page/$page/"
            val doc = try {
                fetchDocument(targetUrl)
            } catch (e: Exception) {
                if (page > 1) {
                    emit(emptyList())
                    return@flow
                }
                throw e
            }
            val items = doc.select(itemSelector).mapNotNull { parseItem(it) }
            emit(items)
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            if (page > 1) {
                emit(emptyList())
            } else {
                throw e
            }
        }
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        var targetUrl = ""
        try {
            targetUrl = if (page == 1) "${currentBaseUrl()}$searchPath$query" else "${currentBaseUrl()}/page/$page$searchPath$query"
            val doc = try {
                fetchDocument(targetUrl)
            } catch (e: Exception) {
                if (page > 1) {
                    emit(emptyList())
                    return@flow
                }
                throw e
            }
            val items = doc.select(itemSelector).mapNotNull { parseItem(it) }
            emit(items)
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            if (page > 1) {
                emit(emptyList())
            } else {
                throw e
            }
        }
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        return try {
            val doc = fetchDocument(itemId)
            val title = doc.selectFirst("h1.entry-title, .s-title, h1, .movie-content h3.title, .movie-content .title")?.text()
                ?: doc.selectFirst("meta[property='og:title']")?.attr("content")
                ?: ""
            val cleanTitle = title.replace(Regex("(?i)Lokalfilm\\s*-\\s*Movie Details"), "").trim().ifBlank { title }
            val image = doc.selectFirst(".thumb img, .poster img, .movie-thumb img, meta[property='og:image']")?.let {
                if (it.tagName() == "meta") it.attr("content") else it.absUrl("src")
            } ?: ""
            val description = doc.selectFirst(".entry-content, .desc, .sinopsis, meta[name='description']")?.let {
                if (it.tagName() == "meta") it.attr("content") else it.text()
            } ?: ""
            val genres = doc.select(".genres a, .gmr-movie-genre a, .cat a, .tags a").map { it.text().trim() }.filter { it.isNotBlank() }.distinct()
            val metadata = extractDramaMetadata(doc, cleanTitle)
            
            ContentItem(
                id = itemId,
                title = cleanTitle,
                imageUrl = image,
                sourceId = sourceId,
                source = sourceName,
                type = ContentType.DRAMA,
                description = description,
                genres = genres,
                castList = metadata.castList,
                subtitleStatus = metadata.subtitleStatus,
                seasonNumber = metadata.seasonNumber
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseItem(el: Element): ContentItem? {
        val a = el.selectFirst(linkSelector) ?: return null
        val href = a.absUrl("href")
        val img = el.selectFirst(imageSelector)
        var title = el.selectFirst(titleSelector)?.text()?.takeIf { it.isNotBlank() } 
            ?: a.attr("title").takeIf { it.isNotBlank() } 
            ?: img?.attr("alt")?.takeIf { it.isNotBlank() && !it.equals("movie", ignoreCase = true) } 
            ?: a.text().takeIf { it.isNotBlank() }

        if (title.isNullOrBlank()) {
            val segment = href.trimEnd('/').substringAfterLast('/')
            if (segment.isNotBlank()) {
                title = segment.replace('-', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
            }
        }
        val src = img?.absUrl("src")?.ifEmpty { img.absUrl("data-src") } ?: ""
        
        if (title.isNullOrBlank() || href.isEmpty()) return null
        
        return ContentItem(
            id = href,
            title = title,
            imageUrl = src,
            sourceId = sourceId,
            source = sourceName,
            type = ContentType.DRAMA
        )
    }
    
    
}
