package com.example.data.sources.templates

import android.content.Context
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.base.BaseAnimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * Reusable HTML scraper template for anime sources.
 * Keep site-specific selectors in the concrete source, not in UI/repository code.
 */
abstract class AnimeScraperTemplate(context: Context) : BaseAnimeSource(context) {
    data class AnimeSelectors(
        val card: String,
        val title: String,
        val link: String,
        val image: String,
        val description: String,
        val genres: String,
        val episode: String,
        val episodeTitle: String,
        val episodeDate: String,
        val searchCard: String,
    )

    protected abstract val selectors: AnimeSelectors
    protected open val contentType: ContentType = ContentType.ANIME

    protected open fun recommendationsUrl(page: Int): String =
        if (page <= 1) currentBaseUrl() else "${currentBaseUrl()}/page/$page/"

    protected open fun searchUrl(query: String, page: Int): String =
        "${currentBaseUrl()}/?s=${URLEncoder.encode(query, "UTF-8")}" +
            if (page > 1) "&paged=$page" else ""

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val doc = fetchDocument(recommendationsUrl(page))
            val result = parseCards(doc, selectors.card)
            if (page == 1 && result.isEmpty()) {
                throw Exception("Selector tidak cocok, HTML berubah atau diblokir.")
            }
            SourceStatusBus.clear(sourceId)
            emit(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        try {
            val doc = fetchDocument(searchUrl(query, page))
            val result = parseCards(doc, selectors.searchCard)
            SourceStatusBus.clear(sourceId)
            emit(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        
        return try {
            val doc = fetchDocument(itemId)
            val title = firstText(doc, selectors.title).ifBlank { doc.title() }
            val image = firstImage(doc, selectors.image)
            val description = firstText(doc, selectors.description)
            val genres = doc.select(selectors.genres).map { it.text().trim() }.filter { it.isNotBlank() }.distinct()
            SourceStatusBus.clear(sourceId)
            ContentItem(
                id = itemId,
                title = title,
                source = sourceName,
                imageUrl = image,
                type = contentType,
                description = description,
                genres = genres,
                sourceId = sourceId,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        
        try {
            val doc = fetchDocument(itemId)
            // replaced by patch
            var episodes = doc.select(selectors.episode).mapNotNull { element ->
                val anchor = if (element.tagName() == "a") element else element.selectFirst("a") ?: return@mapNotNull null
                val url = anchor.absUrl("href").ifBlank { return@mapNotNull null }
                val title = if (selectors.episodeTitle.isBlank()) anchor.text() else
                    element.selectFirst(selectors.episodeTitle)?.text().orEmpty().ifBlank { anchor.text() }
                val date = if (selectors.episodeDate.isBlank()) "" else
                    element.selectFirst(selectors.episodeDate)?.text().orEmpty()
                Chapter(url, title.ifBlank { "Episode" }, url, date)
            }.distinctBy { it.url }

            if (episodes.isEmpty()) {
                val dataContentElements = doc.select("[data-content*='href=']")
                for (el in dataContentElements) {
                    val htmlContent = el.attr("data-content")
                    val dummyDoc = org.jsoup.Jsoup.parseBodyFragment(htmlContent, baseUrl)
                    val anchors = dummyDoc.select("a[href]")
                    if (anchors.isNotEmpty()) {
                        episodes = anchors.map { a ->
                            Chapter(a.absUrl("href"), a.text().ifBlank { "Episode" }, a.absUrl("href"), "")
                        }.distinctBy { it.url }.reversed() // usually data-content has Ep 1 first
                        break
                    }
                }
                
                if (episodes.isEmpty()) {
                    // Try to find generic chapter list if still empty
                    val genericAnchors = doc.select("a[href*='episode'], a[href*='ep-']").filter { 
                        it.absUrl("href").contains("episode") && !it.absUrl("href").contains("check-episode")
                    }
                    if (genericAnchors.isNotEmpty()) {
                        episodes = genericAnchors.map { a ->
                            Chapter(a.absUrl("href"), a.text().ifBlank { "Episode" }, a.absUrl("href"), "")
                        }.distinctBy { it.url }
                    }
                }
            }
            if (episodes.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(episodes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            
        }
    }

    private fun parseCards(doc: Document, cardSelector: String): List<ContentItem> =
        doc.select(cardSelector).mapNotNull { card ->
            println("parseCards: evaluating card")
            val anchor = card.selectFirst(selectors.link) ?: if (card.tagName() == "a") card else null
            println("parseCards: anchor html=${anchor?.outerHtml()}")
            val url = anchor?.absUrl("href").orEmpty()
            println("parseCards: absUrl=$url")
            if (url.isBlank()) return@mapNotNull null
            val title = card.selectFirst(selectors.title)?.text()?.trim()
                .orEmpty().ifBlank { anchor?.attr("title").orEmpty().trim() }.ifBlank { anchor?.text().orEmpty().trim() }
            println("parseCards: title=$title")
            if (title.isBlank()) return@mapNotNull null
            ContentItem(
                id = url,
                title = title,
                source = sourceName,
                imageUrl = firstImage(card, selectors.image),
                type = contentType,
                sourceId = sourceId,
            )
        }.distinctBy { it.id }

    private fun firstText(root: Element, selector: String): String =
        selector.takeIf { it.isNotBlank() }?.let { root.selectFirst(it)?.text()?.trim() }.orEmpty()

    private fun firstImage(root: Element, selector: String): String =
        selector.takeIf { it.isNotBlank() }?.let { root.selectFirst(it)?.let { image ->
            image.absUrl("data-lazy-src")
                .ifBlank { image.absUrl("data-original") }
                .ifBlank { image.absUrl("data-src") }
                .ifBlank { image.absUrl("data-srcset") }
                .ifBlank { image.absUrl("data-setbg") }
                .ifBlank { image.absUrl("src") }
        } }.orEmpty()
}
