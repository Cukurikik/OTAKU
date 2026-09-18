package com.example.data.sources.direct

import android.content.Context
import com.example.data.models.ContentType
import com.example.data.models.Chapter
import com.example.data.models.Page
import com.example.data.sources.WebViewFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.json.JSONObject
import org.json.JSONArray

class ComicScraperEngine(
    private val context: Context,
    private val config: ScraperSourceConfig
) {
    private val fetcher by lazy { WebViewFetcher(context) }

    suspend fun search(query: String, page: Int = 1): List<ScrapedCard> =
        parseCards(fetch(config.searchUrl(query, page)))

    suspend fun popular(page: Int = 1): List<ScrapedCard> =
        parseCards(fetch(config.popularUrl(page)))

    suspend fun detail(url: String): ScrapedDetail? {
        val doc = fetch(url) ?: return null
        val title = doc.selectFirst("h1, .entry-title, .title, .infox h1, .post-title h1, .post-title, .comic-title")?.text()?.trim().orEmpty()
        if (title.isEmpty()) return null

        val image = doc.selectFirst("meta[property='og:image'], .poster img, .thumb img, .summary_image img, .manga-info-pic img, .comic-cover img, .bixbox .thumb img, .series-thumb img, .book-thumbnail img")
            ?.let { it.absUrl("data-src").ifEmpty { it.absUrl("src") }.ifEmpty { it.attr("content") } }.orEmpty()

        val description = config.effectiveSelectors.description.takeIf { it.isNotBlank() }
            ?.let { doc.select(it).text().trim() }.orEmpty()

        val genres = config.effectiveSelectors.genres.takeIf { it.isNotBlank() }
            ?.let { doc.select(it).map { e -> e.text().trim() }.filter(String::isNotBlank) }
            ?: emptyList()

        return ScrapedDetail(url, url, title, image, description, genres)
    }

    suspend fun chapters(url: String): List<ScrapedEpisode> {
        val doc = fetch(url) ?: return emptyList()
        val chapters = doc.select(config.effectiveSelectors.episode).mapNotNull { element ->
            val link = element.selectFirst(config.effectiveSelectors.episodeLink) ?: if (element.tagName() == "a") element else null
                ?: return@mapNotNull null
            
            val href = link.absUrl("href").ifEmpty { link.attr("href") }
            if (href.isBlank()) return@mapNotNull null

            val title = if (config.effectiveSelectors.episodeTitle.isBlank()) link.text().trim()
            else element.select(config.effectiveSelectors.episodeTitle).text().trim()
            
            var label = title.ifBlank { element.text().trim() }
            if (label.isBlank()) label = link.text().trim()

            ScrapedEpisode(href, href, EpisodeNumberNormalizer.parse(label), label)
        }
        return EpisodeNumberNormalizer.sort(chapters).distinctBy { it.url }
    }

    suspend fun pages(chapterUrl: String): List<Page> = withContext(Dispatchers.IO) {
        val doc1 = runCatching { fetcher.fetchHtml(chapterUrl) }.getOrNull()
        val rawHtml1 = doc1?.html() ?: ""

        val jsPages = extractImagesFromJS(rawHtml1)
        if (jsPages.isNotEmpty()) {
            return@withContext jsPages
        }

        val extractorScript = """
            (function() {
                var urls = [];
                try {
                    if (typeof ts_reader !== 'undefined' && ts_reader.params && ts_reader.params.sources) {
                        return JSON.stringify(ts_reader.params.sources[0].images);
                    }
                } catch(e) {}
                try {
                    if (typeof chapter_preloaded_images !== 'undefined') {
                        return JSON.stringify(chapter_preloaded_images);
                    }
                } catch(e) {}
                
                var imgs = document.querySelectorAll('${config.effectiveSelectors.page.replace("'", "\\'")}');
                if (imgs.length === 0) {
                    // Ultra-broad fallback for NextJS or custom frameworks
                    imgs = document.querySelectorAll('img[src*="chapter"], img[src*="manga"], img[alt*="chapter"], img[data-src*="chapter"]');
                }
                
                for(var i = 0; i < imgs.length; i++) {
                    var src = imgs[i].getAttribute('data-src') || 
                              imgs[i].getAttribute('data-lazy-src') || 
                              imgs[i].getAttribute('data-original') || 
                              imgs[i].getAttribute('src');
                    if (src && !src.includes('avatar') && !src.includes('logo') && !src.startsWith('data:image')) {
                        if (src.startsWith('//')) src = 'https:' + src;
                        else if (src.startsWith('/')) src = window.location.origin + src;
                        urls.push(src);
                    }
                }
                return JSON.stringify(urls);
            })();
        """.trimIndent()

        val customHeaders = mutableMapOf<String, String>()
        val ua = com.example.data.sources.SourceHeaderStore.getUserAgent(context, config.sourceId)
        val cookie = com.example.data.sources.SourceHeaderStore.getCookie(context, config.sourceId)
        val referer = com.example.data.sources.SourceHeaderStore.getReferer(context, config.sourceId)
        if (!ua.isNullOrBlank()) customHeaders["User-Agent"] = ua
        if (!cookie.isNullOrBlank()) customHeaders["Cookie"] = cookie
        if (!referer.isNullOrBlank()) customHeaders["Referer"] = referer

        val jsonResult = runCatching { 
            fetcher.executeJsExtractor(chapterUrl, extractorScript, 4500L, customHeaders) 
        }.getOrNull()

        if (jsonResult != null && jsonResult != "null" && jsonResult.isNotBlank()) {
            try {
                val array = JSONArray(jsonResult)
                val pages = mutableListOf<Page>()
                for (i in 0 until array.length()) {
                    val src = array.getString(i).replace("\\/", "/")
                    if (isValidComicPage(src)) {
                        pages.add(Page(url = src))
                    }
                }
                if (pages.isNotEmpty()) return@withContext pages.distinctBy { it.url }
            } catch (e: Exception) {}
        }

        val doc2 = withContext(Dispatchers.Main) {
            runCatching { fetcher.fetchHtmlRendered(chapterUrl, 8000L, headers = customHeaders) }.getOrNull()
        } ?: return@withContext emptyList()

        var domPages = doc2.select(config.selectors.page).mapNotNull { img ->
            val src = img.absUrl("data-src").ifEmpty {
                img.absUrl("data-lazy-src").ifEmpty {
                    img.absUrl("data-original").ifEmpty {
                        img.absUrl("data-srcset")?.split(",")?.firstOrNull()?.split(" ")?.firstOrNull()?.trim() ?:
                        img.absUrl(config.selectors.pageAttribute).ifEmpty {
                            img.absUrl("src")
                        }
                    }
                }
            }
            if (isValidComicPage(src)) Page(url = src.trim().replace("\\/", "/")) else null
        }
        
        if (domPages.isEmpty()) {
            domPages = doc2.select("img[src*=chapter], img[src*=manga], img[src*=uploads][class*=page], img[src*=uploads][id*=image], .reader-area img").mapNotNull { img ->
                val src = img.absUrl("src").ifEmpty { img.absUrl("data-src") }
                if (isValidComicPage(src)) Page(url = src.trim().replace("\\/", "/")) else null
            }
        }

        return@withContext domPages.distinctBy { it.url }
    }

    private fun isValidComicPage(url: String): Boolean {
        if (url.isBlank() || url == "null" || url.startsWith("data:image")) return false
        val lower = url.lowercase()
        return !lower.contains("avatar") && 
               !lower.contains("logo") && 
               !lower.contains("banner") &&
               !lower.contains("discord") &&
               !lower.contains("histats") &&
               !lower.contains("gif") &&
               !lower.contains("about:blank")
    }

    private fun extractImagesFromJS(rawHtml: String): List<Page> {
        val pages = mutableListOf<Page>()
        
        val tsReaderMatch = "ts_reader\\.run\\((.*?)\\);".toRegex(RegexOption.IGNORE_CASE).find(rawHtml)
        if (tsReaderMatch != null) {
            try {
                val jsonStr = tsReaderMatch.groupValues[1]
                val json = JSONObject(jsonStr)
                val sources = json.optJSONArray("sources")
                if (sources != null && sources.length() > 0) {
                    val images = sources.getJSONObject(0).optJSONArray("images")
                    if (images != null && images.length() > 0) {
                        for (i in 0 until images.length()) {
                            val url = images.getString(i).replace("\\/", "/")
                            if (isValidComicPage(url)) pages.add(Page(url = url))
                        }
                        return pages
                    }
                }
            } catch (e: Exception) {}
        }

        val wpMangaMatch = "chapter_preloaded_images\\s*=\\s*\\[(.*?)\\]".toRegex(RegexOption.IGNORE_CASE).find(rawHtml)
        if (wpMangaMatch != null) {
            try {
                val jsonStr = "[${wpMangaMatch.groupValues[1]}]"
                val jsonArray = org.json.JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val url = jsonArray.getString(i).replace("\\/", "/")
                    if (isValidComicPage(url)) pages.add(Page(url = url))
                }
                return pages
            } catch (e: Exception) {}
        }
        
        // Next.js State Extraction
        val nextStateMatch = "__NEXT_DATA__.*?(\\{.*?\\})</script>".toRegex(RegexOption.IGNORE_CASE).find(rawHtml)
        if (nextStateMatch != null) {
            try {
                val jsonStr = nextStateMatch.groupValues[1]
                val jsArrayImages = "\"(https?:\\\\?/\\\\?/[^\"]+\\.(?:jpg|jpeg|png|webp)[^\"]*)\"".toRegex(RegexOption.IGNORE_CASE).findAll(jsonStr)
                val extracted = jsArrayImages.mapNotNull { match ->
                    val rawSrc = match.groupValues[1].replace("\\/", "/")
                    if (isValidComicPage(rawSrc)) Page(url = rawSrc) else null
                }.toList().distinctBy { it.url }
                if (extracted.size > 2) return extracted
            } catch (e: Exception) {}
        }

        val jsArrayImages = "\"(https?:\\\\?/\\\\?/[^\"]+\\.(?:jpg|jpeg|png|webp)[^\"]*)\"".toRegex(RegexOption.IGNORE_CASE).findAll(rawHtml)
        val extracted = jsArrayImages.mapNotNull { match ->
            val rawSrc = match.groupValues[1].replace("\\/", "/")
            if (isValidComicPage(rawSrc)) Page(url = rawSrc) else null
        }.toList().distinctBy { it.url }
        
        if (extracted.size > 2) {
            return extracted
        }
        
        return emptyList()
    }

    private suspend fun fetch(url: String): Document? = withContext(Dispatchers.IO) {
        val customHeaders = mutableMapOf<String, String>()
        val ua = com.example.data.sources.SourceHeaderStore.getUserAgent(context, config.sourceId)
        val cookie = com.example.data.sources.SourceHeaderStore.getCookie(context, config.sourceId)
        val referer = com.example.data.sources.SourceHeaderStore.getReferer(context, config.sourceId)
        if (!ua.isNullOrBlank()) customHeaders["User-Agent"] = ua
        if (!cookie.isNullOrBlank()) customHeaders["Cookie"] = cookie
        if (!referer.isNullOrBlank()) customHeaders["Referer"] = referer

        var doc = runCatching { fetcher.fetchHtml(url, customHeaders) }.getOrNull()
        if (doc == null || doc.title().contains("Just a moment") || doc.title().contains("Cloudflare") || doc.title().contains("Access Denied") || doc.title().contains("Security check")) {
            doc = withContext(Dispatchers.Main) {
                kotlinx.coroutines.withTimeoutOrNull(25000L) {
                    runCatching { fetcher.fetchHtmlRendered(url, 8000L, headers = customHeaders) }.getOrNull()
                }
            }
        }
        doc
    }

    private fun parseCards(doc: Document?): List<ScrapedCard> {
        if (doc == null) return emptyList()
        return doc.select(config.effectiveSelectors.card).mapNotNull { card ->
            val link = card.selectFirst(config.effectiveSelectors.cardLink) ?: if (card.tagName() == "a") card else null ?: return@mapNotNull null
            val url = link.absUrl("href").ifEmpty { link.attr("href") }
            
            var title = if (config.effectiveSelectors.cardTitle.isBlank()) link.text().trim()
            else card.select(config.effectiveSelectors.cardTitle).text().trim()
            
            if (title.isBlank()) title = link.attr("title").trim()
            if (title.isBlank()) title = link.text().trim()
            
            if (url.isBlank() || title.isBlank()) return@mapNotNull null
            
            var image = card.selectFirst(config.effectiveSelectors.cardImage)?.let {
                it.absUrl("data-src").ifEmpty {
                    it.absUrl("data-lazy-src").ifEmpty {
                        it.absUrl("data-original").ifEmpty {
                            it.absUrl("src")
                        }
                    }
                }
            }.orEmpty()
            
            if (image.isBlank()) {
                val bgElement = card.selectFirst("[style*=background-image], [data-bg]")
                image = bgElement?.absUrl("data-bg").orEmpty()
                if (image.isBlank()) {
                    val style = bgElement?.attr("style").orEmpty()
                    val bgMatch = "url\\(['\"]?(.*?)['\"]?\\)".toRegex(RegexOption.IGNORE_CASE).find(style)
                    image = bgMatch?.groupValues?.get(1)?.let { 
                        if (it.startsWith("http")) it else if (it.startsWith("//")) "https:$it" else "" 
                    }.orEmpty()
                }
            }
            
            ScrapedCard(url, url, title, image)
        }.distinctBy { it.url }
    }
}
