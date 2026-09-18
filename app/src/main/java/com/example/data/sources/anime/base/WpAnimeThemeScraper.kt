package com.example.data.sources.anime.base

import android.content.Context
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.base.BaseAnimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jsoup.nodes.Element

abstract class WpAnimeThemeScraper(
    context: Context,
    override val sourceId: String,
    override val sourceName: String,
    override val baseUrl: String,
    private val itemSelector: String = ".venz ul li, .post-show ul li, .listupd .bs, .listo .bsx, article.bs",
    private val titleSelector: String = "h2, .tt, .title",
    private val imageSelector: String = "img",
    private val linkSelector: String = "a",
    private val searchPath: String = "/?s="
) : BaseAnimeSource(context) {

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val url = if (page == 1) currentBaseUrl() else "${currentBaseUrl()}/page/$page/"
            val doc = fetchDocument(url)
            val items = doc.select(itemSelector).mapNotNull { parseItem(it) }
            emit(items)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        try {
            val url = if (page == 1) "${currentBaseUrl()}$searchPath$query" else "${currentBaseUrl()}/page/$page$searchPath$query"
            val doc = fetchDocument(url)
            val items = doc.select(itemSelector).mapNotNull { parseItem(it) }
            emit(items)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        return try {
            val doc = fetchDocument(itemId)
            val title = doc.selectFirst(".info-content h1, .infox h1, .entry-title")?.text() ?: ""
            val image = doc.selectFirst(".fotoanime img, .thumb img, .infox img")?.absUrl("src") ?: ""
            val description = doc.selectFirst(".sinopc, .entry-content, .desc")?.text() ?: ""
            
            ContentItem(
                id = itemId,
                title = title,
                imageUrl = image,
                sourceId = sourceId,
                source = sourceName,
                type = ContentType.ANIME,
                description = description
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseItem(el: Element): ContentItem? {
        val a = el.selectFirst(linkSelector) ?: return null
        val href = a.absUrl("href")
        val title = el.selectFirst(titleSelector)?.text() ?: a.attr("title")
        val img = el.selectFirst(imageSelector)
        val src = img?.absUrl("src")?.ifEmpty { img.absUrl("data-src") } ?: ""
        
        if (title.isEmpty() || href.isEmpty()) return null
        
        return ContentItem(
            id = href,
            title = title,
            imageUrl = src,
            sourceId = sourceId,
            source = sourceName,
            type = ContentType.ANIME
        )
    }
}
