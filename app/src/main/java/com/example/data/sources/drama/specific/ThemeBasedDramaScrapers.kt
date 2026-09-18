package com.example.data.sources.drama.specific

import android.content.Context
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.drama.base.WpDramaThemeScraper

class VikiScraper(context: Context) : WpDramaThemeScraper(
    context = context,
    sourceId = "viki",
    sourceName = "Viki",
    baseUrl = "https://www.viki.com"
)

class LicilScraper(context: Context) : WpDramaThemeScraper(
    context = context,
    sourceId = "licil",
    sourceName = "Licil",
    baseUrl = "https://digitalepiphanies.org",
    itemSelector = ".gmr-item-modulepost, .item, article",
    titleSelector = ".entry-title, h2, h3, a[title]",
    imageSelector = "img",
    linkSelector = "a[itemprop='url'], a.gmr-watch-btn, a"
)

class LokalfilmScraper(context: Context) : WpDramaThemeScraper(
    context = context,
    sourceId = "lokalfilm",
    sourceName = "LokalFilm",
    baseUrl = "https://lokalfilm.id",
    itemSelector = ".movie-item",
    titleSelector = "h3, .title",
    imageSelector = "img",
    linkSelector = "a"
) {
    override fun getRecommendations(page: Int): kotlinx.coroutines.flow.Flow<List<com.example.data.models.ContentItem>> = kotlinx.coroutines.flow.flow {
        try {
            val url = when (page) {
                1 -> currentBaseUrl()
                2 -> "${currentBaseUrl()}/sub-category/12"
                3 -> "${currentBaseUrl()}/sub-category/2"
                4 -> "${currentBaseUrl()}/sub-category/3"
                5 -> "${currentBaseUrl()}/sub-category/7"
                6 -> "${currentBaseUrl()}/sub-category/8"
                else -> {
                    emit(emptyList())
                    return@flow
                }
            }
            val doc = fetchDocument(url)
            val items = doc.select(".movie-item").mapNotNull { el ->
                val a = el.selectFirst("a") ?: return@mapNotNull null
                val href = a.absUrl("href")
                if (href.isBlank()) return@mapNotNull null
                val img = el.selectFirst("img")
                val src = img?.absUrl("src")?.ifEmpty { img.absUrl("data-src") } ?: ""
                var title = el.selectFirst("h3, .title")?.text()?.takeIf { it.isNotBlank() }
                    ?: img?.attr("alt")?.takeIf { it.isNotBlank() && !it.equals("movie", ignoreCase = true) }
                    
                if (title.isNullOrBlank()) {
                    val id = href.trimEnd('/').substringAfterLast('/')
                    title = "Film $id"
                }
                com.example.data.models.ContentItem(
                    id = href,
                    title = title,
                    imageUrl = src,
                    sourceId = sourceId,
                    source = sourceName,
                    type = com.example.data.models.ContentType.DRAMA
                )
            }
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
}

class JucelebrityScraper(context: Context) : WpDramaThemeScraper(
    context = context,
    sourceId = "jucelebrity",
    sourceName = "JuCelebrity",
    baseUrl = "https://jucelebrity.com"
)

class LicilBaratScraper(context: Context) : WpDramaThemeScraper(
    context = context,
    sourceId = "licil-barat",
    sourceName = "Licil Barat",
    baseUrl = "https://digitalepiphanies.org/category/western-series",
    itemSelector = ".gmr-item-modulepost, .item, article",
    titleSelector = ".entry-title, h2, h3, a[title]",
    imageSelector = "img",
    linkSelector = "a[itemprop='url'], a.gmr-watch-btn, a"
)

class Ip178DramaScraper(context: Context) : WpDramaThemeScraper(
    context = context,
    sourceId = "ip-178",
    sourceName = "Streaming 1 (178)",
    baseUrl = "http://178.128.91.191",
    itemSelector = ".venz ul li, .post-item, .item, article",
    titleSelector = "h2, .title, .entry-title",
    imageSelector = "img",
    linkSelector = "a"
)

class Ip134DramaScraper(context: Context) : WpDramaThemeScraper(
    context = context,
    sourceId = "ip-134",
    sourceName = "Streaming 2 (134)",
    baseUrl = "http://134.209.105.11",
    itemSelector = "article, .animepost, .item",
    titleSelector = "h2, h3, .title",
    imageSelector = "img",
    linkSelector = "a"
)

