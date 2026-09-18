package com.example.data.sources.direct

import android.content.Context
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.models.Chapter
import com.example.data.sources.VideoExtractor
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.WebViewFetcher
import com.example.data.sources.anime.extractors.JsPackerUnpacker
import com.example.data.sources.anime.extractors.CryptoJsDecryptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Shared pipeline for direct Anime/Drama HTML scraping.
 * Pipeline: request -> normalize HTML -> parse cards -> detail -> episodes -> player -> media.
 */
class AnimeDramaScraperEngine(
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
        var title = doc.selectFirst("meta[property='og:title'], meta[name='twitter:title'], h1, .entry-title, .title, .anime__details__title h3, .product__item__text h5, .infox h1, .bixbox .ts-breadcrumb li:last-child span")?.let { 
            it.attr("content").ifEmpty { it.text() }
        }?.trim().orEmpty()
        
        // Remove trailing ratings or weird texts if necessary
        title = title.replace(Regex("(?i)Subtitle Indonesia.*"), "").trim()
        if (title.isEmpty()) return null

        val image = doc.selectFirst("meta[property='og:image'], .poster img, .thumb img, .anime__details__pic, .set-bg, .product__item__pic, .post-thumbnail img, .movie-thumb img, .kover img")
            ?.let { 
                it.absUrl("data-setbg").ifEmpty {
                    it.absUrl("data-src").ifEmpty {
                        it.attr("content").ifEmpty {
                            it.absUrl("src")
                        }
                    }
                }
            }.orEmpty()

        val description = config.effectiveSelectors.description.takeIf { it.isNotBlank() }
            ?.let { doc.select(it).text().trim() }.orEmpty()
            .ifBlank { doc.select("meta[property='og:description'], meta[name='twitter:description'], meta[name='description']").attr("content").trim() }

        val genres = config.effectiveSelectors.genres.takeIf { it.isNotBlank() }
            ?.let { doc.select(it).map { e -> e.text().trim() }.filter(String::isNotBlank) }
            ?.takeIf { it.isNotEmpty() }
            ?: doc.select("a[href*='/genre/'], a[href*='genres']").map { e -> e.text().trim() }.filter { it.isNotBlank() && it.length < 25 }

        return ScrapedDetail(url, url, title, image, description, genres)
    }

    suspend fun episodes(url: String): List<ScrapedEpisode> {
        val doc = fetch(url) ?: return emptyList()
        
        var episodes = doc.select(config.effectiveSelectors.episode).mapNotNull { element ->
            val link = element.selectFirst(config.effectiveSelectors.episodeLink) ?: if (element.tagName() == "a") element else null
                ?: return@mapNotNull null
            
            val href = link.absUrl("href").ifEmpty { link.attr("href") }
            if (href.isBlank()) return@mapNotNull null

            val title = if (config.effectiveSelectors.episodeTitle.isBlank()) link.text().trim()
            else element.select(config.effectiveSelectors.episodeTitle).text().trim()
            
            val label = title.ifBlank { element.text().trim() }
            ScrapedEpisode(href, href, EpisodeNumberNormalizer.parse(label), label)
        }
        
        if (episodes.isEmpty()) {
            // Broad fallback for Kuramanime or similar sites hiding episodes in data-content or script
            val popoverHtml = doc.select("[data-content]").map { it.attr("data-content") }
                .firstOrNull { it.contains("episode") || it.contains("href=") || it.contains("btn") }
            if (popoverHtml != null) {
                val popoverDoc = Jsoup.parse(popoverHtml, url)
                episodes = popoverDoc.select("a").mapNotNull { link ->
                    val href = link.absUrl("href")
                    if (href.isBlank() || href.contains("javascript") || href.contains("#")) return@mapNotNull null
                    val label = link.text().trim()
                    ScrapedEpisode(href, href, EpisodeNumberNormalizer.parse(label), label)
                }
            }
        }
        
        if (episodes.isEmpty()) {
            // Ultimate fallback: Find any link containing 'episode' in href
            episodes = doc.select("a[href*=episode], a[href*=watch], a[href*='/tv/'], a[href*='-episode-']").mapNotNull { link ->
                val href = link.absUrl("href").ifEmpty { link.attr("href") }
                if (href.isBlank() || href.contains("javascript") || href.contains("#") || href.equals(url) || href.contains("facebook.com")) return@mapNotNull null
                val label = link.text().trim()
                if (label.isBlank() || label.length > 50) return@mapNotNull null
                ScrapedEpisode(href, href, EpisodeNumberNormalizer.parse(label), label)
            }
        }
        
        // Final fallback: If this is a tube site or has no episodes, make the current page the episode
        if (episodes.isEmpty()) {
            episodes = listOf(ScrapedEpisode(url, url, 1.0, "Watch Now"))
        }

        return EpisodeNumberNormalizer.sort(episodes).distinctBy { it.url }
    }

    suspend fun resolveVideo(episodeUrl: String): VideoStreamInfo? {
        val doc = fetch(episodeUrl) ?: return null
        
        val servers = mutableListOf<com.example.data.sources.VideoServer>()
        var serverIdx = 1

        val htmlBody = doc.html()

        // 1. NATIVE EXTRACTOR: JS Packer Unpacker (eval(function(p,a,c,k,e,d)...))
        val evalRegex = Regex("""eval\(function\(p,a,c,k,e,d\).*?\{.*?\}\((.*?)\)\)""")
        for (evalMatch in evalRegex.findAll(htmlBody)) {
            val unpacked = JsPackerUnpacker.unpack(evalMatch.value) ?: continue
            val srcRegex = Regex("""(?:file|src)\s*:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""")
            val srcMatch = srcRegex.find(unpacked)
            if (srcMatch != null) {
                val srcUrl = srcMatch.groupValues[1].replace("\\/", "/")
                val isM3u8 = srcUrl.contains(".m3u8")
                servers.add(com.example.data.sources.VideoServer(
                    "Native Server ${serverIdx++}", srcUrl, 
                    listOf(com.example.data.sources.VideoQuality("auto", srcUrl, isM3u8, mapOf("Referer" to episodeUrl)))
                ))
            }
        }

        // 2. NATIVE EXTRACTOR: CryptoJS AES payload (e.g. Kuramanime, Animeindo, Animasu)
        val aesPayloadRegex = Regex("""["'](U2FsdGVkX1[a-zA-Z0-9+/=]{16,})["']""")
        val aesMatches = aesPayloadRegex.findAll(htmlBody).toList()
        if (aesMatches.isNotEmpty()) {
            val possiblePasswords = linkedSetOf<String>()
            val varRegex = Regex("""(?:var|let|const)\s+([a-zA-Z0-9_$]+)\s*=\s*["']([^"']{3,128})["']""")
            for (m in varRegex.findAll(htmlBody)) {
                val varName = m.groupValues[1]
                val value = m.groupValues[2]
                if (varName.contains("pass", ignoreCase = true) ||
                    varName.contains("key", ignoreCase = true) ||
                    varName.contains("secret", ignoreCase = true) ||
                    varName.contains("token", ignoreCase = true)
                ) {
                    possiblePasswords.add(value)
                }
            }
            val passRegex = Regex("""["']([a-zA-Z0-9_-]{4,48})["']""")
            possiblePasswords.addAll(passRegex.findAll(htmlBody).map { it.groupValues[1] }.filter { !it.startsWith("U2FsdGVkX1") && !it.startsWith("http") }.toList())
            possiblePasswords.add("kuramanime")
            possiblePasswords.add("animeindo")
            possiblePasswords.add("animasu")
            possiblePasswords.add("otakudesu")
            possiblePasswords.add("samehadaku")
            possiblePasswords.add("anichin")
            possiblePasswords.add("nimegami")

            for (aesMatch in aesMatches) {
                val encrypted = aesMatch.groupValues[1]
                for (pass in possiblePasswords) {
                    val decrypted = CryptoJsDecryptor.decrypt(encrypted, pass) ?: continue
                    if (decrypted.isNotBlank()) {
                        val streamResult = CryptoJsDecryptor.parseDecryptedPayload(decrypted, episodeUrl)
                        if (streamResult.qualities.isNotEmpty()) {
                            servers.add(com.example.data.sources.VideoServer(
                                "Direct AES Server ${serverIdx++}",
                                streamResult.qualities.first().url,
                                streamResult.qualities
                            ))
                            break
                        } else if (streamResult.embedUrl.isNotBlank()) {
                            servers.add(com.example.data.sources.VideoServer(
                                "AES Embed Server ${serverIdx++}",
                                streamResult.embedUrl,
                                emptyList()
                            ))
                            break
                        }
                    }
                }
            }
        }

        // Sometimes player is inside a JS variable or base64 encoded
        val embeds = doc.select(config.selectors.player).mapNotNull { e ->
            e.attr(config.selectors.videoAttribute).ifBlank { 
                e.absUrl("src").ifBlank { e.attr("data-src").ifBlank { e.attr("data-litespeed-src") } }
            }
        }.filter(String::isNotBlank).distinct()
        
        val extractorEngine = com.example.data.sources.VideoExtractorEngine(context)

        for (embed in embeds) {
            extractorEngine.extractVideo(embed, episodeUrl)?.let { 
                val isPlayableUrl = it.embedUrl.lowercase().let { u -> u.endsWith(".mp4") || u.endsWith(".m3u8") || u.contains(".mp4?") || u.contains(".m3u8?") }; if (it.qualities.isNotEmpty() || isPlayableUrl) {
                    servers.add(com.example.data.sources.VideoServer("Server ${serverIdx++}", it.embedUrl, it.qualities, it.subtitles, it.headers))
                }
            }
        }
        
        // Deep Iframe Crawler: Fallback to all iframes if standard selectors fail to yield playable qualities
        val genericIframes = doc.select("iframe").mapNotNull { e ->
            e.absUrl("src").ifBlank { e.attr("data-src") }
        }.filter(String::isNotBlank).distinct()
        
        for (embed in genericIframes) {
            if (servers.any { it.embedUrl == embed }) continue
            extractorEngine.extractVideo(embed, episodeUrl)?.let { 
                val isPlayableUrl = it.embedUrl.lowercase().let { u -> u.endsWith(".mp4") || u.endsWith(".m3u8") || u.contains(".mp4?") || u.contains(".m3u8?") }; if (it.qualities.isNotEmpty() || isPlayableUrl) {
                    servers.add(com.example.data.sources.VideoServer("Server ${serverIdx++}", it.embedUrl, it.qualities, it.subtitles, it.headers))
                }
            }
        }
        
        // Button data-url extraction
        val buttonElements = doc.select("[data-url], [data-video], [data-src], .server-item")
        for (e in buttonElements) {
            val url = e.attr("data-url").ifBlank { e.attr("data-video").ifBlank { e.attr("data-src") } }
            if (!url.startsWith("http")) continue
            if (servers.any { it.embedUrl == url }) continue
            var serverName = e.text().trim()
            if (serverName.isBlank() || serverName.length > 20) {
                serverName = "Server ${serverIdx++}"
            }
            extractorEngine.extractVideo(url, episodeUrl)?.let { 
                val isPlayableUrl = it.embedUrl.lowercase().let { u -> u.endsWith(".mp4") || u.endsWith(".m3u8") || u.contains(".mp4?") || u.contains(".m3u8?") }; if (it.qualities.isNotEmpty() || isPlayableUrl) {
                    servers.add(com.example.data.sources.VideoServer(serverName, it.embedUrl, it.qualities, it.subtitles, it.headers))
                }
            }
        }
        
        // Try decoding base64 iframes embedded in data attributes
        val base64Embeds = doc.select("[data-content]").mapNotNull { e ->
            try {
                val b64 = e.attr("data-content")
                if (b64.isNotBlank() && !b64.contains("<")) {
                    String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
                } else null
            } catch (ex: Exception) { null }
        }.filter { it.startsWith("http") || it.contains("iframe") }

        for (b64 in base64Embeds) {
            if (b64.startsWith("http")) {
                if (servers.any { it.embedUrl == b64 }) continue
                extractorEngine.extractVideo(b64, episodeUrl)?.let { 
                    val isPlayableUrl = it.embedUrl.lowercase().let { u -> u.endsWith(".mp4") || u.endsWith(".m3u8") || u.contains(".mp4?") || u.contains(".m3u8?") }; if (it.qualities.isNotEmpty() || isPlayableUrl) {
                        servers.add(com.example.data.sources.VideoServer("Server ${serverIdx++}", it.embedUrl, it.qualities, it.subtitles, it.headers))
                    }
                }
            } else {
                val parsedB64 = Jsoup.parse(b64, episodeUrl)
                val iframes = parsedB64.select("iframe").mapNotNull { e -> e.absUrl("src").ifBlank { e.attr("data-src") } }.filter(String::isNotBlank)
                for (embed in iframes) {
                    if (servers.any { it.embedUrl == embed }) continue
                    extractorEngine.extractVideo(embed, episodeUrl)?.let { 
                        val isPlayableUrl = it.embedUrl.lowercase().let { u -> u.endsWith(".mp4") || u.endsWith(".m3u8") || u.contains(".mp4?") || u.contains(".m3u8?") }; if (it.qualities.isNotEmpty() || isPlayableUrl) {
                            servers.add(com.example.data.sources.VideoServer("Server ${serverIdx++}", it.embedUrl, it.qualities, it.subtitles, it.headers))
                        }
                    }
                }
            }
        }

        if (servers.isNotEmpty()) {
            val best = servers.first()
            return VideoStreamInfo(episodeUrl, embedUrl = best.embedUrl, qualities = best.qualities, subtitles = best.subtitles, headers = best.headers, servers = servers)
        }

        // Try headless WebView VideoExtractorEngine with custom headers
        try {
            val customHeaders = mutableMapOf<String, String>()
            val ua = com.example.data.sources.SourceHeaderStore.getUserAgent(context, config.sourceId)
            val cookie = com.example.data.sources.SourceHeaderStore.getCookie(context, config.sourceId)
            val referer = com.example.data.sources.SourceHeaderStore.getReferer(context, config.sourceId)
            if (!ua.isNullOrBlank()) customHeaders["User-Agent"] = ua
            if (!cookie.isNullOrBlank()) customHeaders["Cookie"] = cookie
            if (!referer.isNullOrBlank()) customHeaders["Referer"] = referer

            val mainResult = extractorEngine.extractVideo(episodeUrl, episodeUrl, customHeaders = customHeaders)
            if (mainResult != null && mainResult.qualities.isNotEmpty()) {
                return mainResult
            }

            for (candidateUrl in embeds + genericIframes) {
                val candidateResult = extractorEngine.extractVideo(candidateUrl, episodeUrl, customHeaders = customHeaders)
                if (candidateResult != null && candidateResult.qualities.isNotEmpty()) {
                    return candidateResult
                }
            }
        } catch (_: Exception) {}

        // Return first embed URL as fallback, or generic iframe, or self URL
        return embeds.firstOrNull()?.let { VideoStreamInfo(episodeUrl, embedUrl = it) }
            ?: genericIframes.firstOrNull()?.let { VideoStreamInfo(episodeUrl, embedUrl = it) }
            ?: buttonElements.mapNotNull { e -> e.attr("data-url").ifBlank { e.attr("data-video").ifBlank { e.attr("data-src") } } }.filter { it.startsWith("http") }.firstOrNull()?.let { VideoStreamInfo(episodeUrl, embedUrl = it) }
            ?: VideoStreamInfo(episodeUrl, embedUrl = episodeUrl) // Ultimate fallback: return the page url itself
    }

    private suspend fun fetch(url: String): Document? = withContext(Dispatchers.IO) {
        val customHeaders = mutableMapOf<String, String>()
        val ua = com.example.data.sources.SourceHeaderStore.getUserAgent(context, config.sourceId)
        val cookie = com.example.data.sources.SourceHeaderStore.getCookie(context, config.sourceId)
        val referer = com.example.data.sources.SourceHeaderStore.getReferer(context, config.sourceId)
        if (!ua.isNullOrBlank()) customHeaders["User-Agent"] = ua
        if (!cookie.isNullOrBlank()) customHeaders["Cookie"] = cookie
        if (!referer.isNullOrBlank()) customHeaders["Referer"] = referer

        if (config.sourceId == "viki") {
            return@withContext withContext(Dispatchers.Main) {
                kotlinx.coroutines.withTimeoutOrNull(25000L) {
                    runCatching { fetcher.fetchHtmlRendered(url, 12000L, headers = customHeaders) }.getOrNull()
                }
            }
        }
        var doc = runCatching { fetcher.fetchHtml(url, customHeaders) }.getOrNull()
        if (doc == null || doc.title().contains("Just a moment") || doc.title().contains("Cloudflare") || doc.title().contains("Access Denied")) {
            doc = withContext(Dispatchers.Main) {
                kotlinx.coroutines.withTimeoutOrNull(25000L) {
                    runCatching { fetcher.fetchHtmlRendered(url, 12000L, headers = customHeaders) }.getOrNull()
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
            if (title.isBlank()) title = url.trimEnd('/').substringAfterLast("/").replace("-", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
            
            if (url.isBlank() || title.isBlank()) return@mapNotNull null
            
            val image = card.selectFirst(config.effectiveSelectors.cardImage)?.let {
                it.absUrl("data-setbg").ifEmpty {
                    it.absUrl("data-src").ifEmpty {
                        it.attr("data-src").ifEmpty {
                            it.absUrl("data-lazy-src").ifEmpty {
                                it.absUrl("src")
                            }
                        }
                    }
                }
            }.orEmpty().ifBlank { card.selectFirst("img")?.absUrl("src").orEmpty() }
            
            ScrapedCard(url, url, title, image)
        }.distinctBy { it.url }
    }
}
