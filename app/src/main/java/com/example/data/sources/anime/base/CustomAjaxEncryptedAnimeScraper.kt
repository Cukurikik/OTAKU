package com.example.data.sources.anime.base

import android.content.Context
import android.util.Base64
import com.example.data.NetworkModule
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.anime.extractors.CryptoJsDecryptor
import com.example.data.sources.base.BaseAnimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Kelompok 2: Custom AJAX Nonce & Encrypted Mirrors
 * Karakteristik Arsitektur:
 * - Target: Animasu, Animasu Movie, Kuronime, AnimeIndo, Animasu ID
 * - Player menggunakan Base64 encoded iframes di <select class="mirror"> <option value="base64...">
 * - Endpoint AJAX admin-ajax.php dengan nonce dinamis dan data-post / data-nume
 * - Payload terenkripsi CryptoJS AES ("U2FsdGVkX1...") yang memerlukan ekstraksi key & dekripsi native
 * - Dukungan fallback video extractor engine langsung ke direct/hls stream
 */
open class CustomAjaxEncryptedAnimeScraper(
    context: Context,
    override val sourceId: String,
    override val sourceName: String,
    override val baseUrl: String,
    private val defaultPassphrase: String = ""
) : BaseAnimeSource(context) {

    protected open val cardSelector = ".bsx, .bixbox .bs, .listupd .bs, article.bs, .post-show ul li, .item, .venz ul li"
    protected open val cardLinkSelector = "a"
    protected open val cardTitleSelector = ".tt, .title, .entry-title, h2, h3"
    protected open val cardImageSelector = "img, .thumb img, .kover img, .video-thumb img"
    protected open val episodeSelector = ".episodelist li, .eplister li, .epslist li, .server-list li, ul.servers li, .lsteps li, .box-list-episode a, a.ep-item"

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val url = if (page <= 1) currentBaseUrl() else "${currentBaseUrl()}/page/$page/"
            val doc = fetchDocument(url)
            val items = AnimeDomExtractor.extractCards(doc, sourceId, sourceName, currentBaseUrl(), cardSelector)
            emit(items)
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            emit(emptyList())
        }
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = if (page <= 1) "${currentBaseUrl()}/?s=$encodedQuery" else "${currentBaseUrl()}/page/$page/?s=$encodedQuery"
            val doc = fetchDocument(url)
            val items = AnimeDomExtractor.extractCards(doc, sourceId, sourceName, currentBaseUrl(), cardSelector)
            emit(items)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        return try {
            val doc = fetchDocument(itemId)
            AnimeDomExtractor.extractDetail(doc, sourceId, sourceName, itemId, currentBaseUrl())
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            null
        }
    }

    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        try {
            val doc = fetchDocument(itemId)
            val eps = AnimeDomExtractor.extractEpisodes(doc, currentBaseUrl(), episodeSelector)
            if (eps.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(eps)
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override fun getVideoStream(episodeId: String): Flow<VideoStreamInfo> = flow {
        try {
            val interceptor = com.example.network.interceptor.VideoUrlInterceptor(context)
            val customHeaders = this@CustomAjaxEncryptedAnimeScraper.requestHeaders.toMutableMap()
            if (!customHeaders.containsKey("Referer")) {
                customHeaders["Referer"] = episodeId
            }

            // 1. Direct Interception pada episodeId via headless network sniffer
            val directInterception = interceptor.interceptVideo(episodeId, episodeId, timeoutMs = 15000L, headers = customHeaders)
            if (directInterception != null && directInterception.qualities.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(directInterception)
                return@flow
            }

            val doc = fetchDocument(episodeId)
            val html = doc.html()
            val qualities = mutableListOf<VideoQuality>()
            val embedUrls = mutableSetOf<String>()

            // 1. Check for Base64 encoded iframes in select.mirror option
            doc.select("select.mirror option, select#select-mirror option, .mirror select option").forEach { option ->
                val base64 = option.attr("value")
                if (base64.isNotBlank() && base64.length > 20 && !base64.contains(" ")) {
                    try {
                        val decodedHtml = String(Base64.decode(base64, Base64.DEFAULT))
                        val iframe = Jsoup.parseBodyFragment(decodedHtml).selectFirst("iframe")
                        val src = iframe?.attr("src")?.ifEmpty { iframe.attr("data-src") }
                        if (!src.isNullOrBlank()) {
                            val serverName = option.text().trim().takeIf { it.isNotBlank() && !it.contains("Pilih", true) } ?: "Server ${embedUrls.size + 1}"
                            if (!embedUrls.contains(src)) {
                                qualities.add(VideoQuality(serverName, src, headers = customHeaders))
                                embedUrls.add(src)
                            }
                        }
                    } catch (e: Exception) {
                        // ignore base64 decode failure
                    }
                }
            }

            // 2. Custom AJAX Nonce detection & execution (e.g. kuronime, animeindo)
            var actionNonce: String? = null
            var ajaxUrl = "${currentBaseUrl()}/wp-admin/admin-ajax.php"
            for (script in doc.select("script")) {
                val scriptContent = script.html()
                if (scriptContent.contains("action:\"player_ajax\"") || scriptContent.contains("action: 'player_ajax'") || scriptContent.contains("player_ajax")) {
                    val nonceMatch = Regex("""nonce\s*:\s*['"]([^'"]+)['"]""").find(scriptContent)
                    if (nonceMatch != null) actionNonce = nonceMatch.groupValues[1]

                    val ajaxMatch = Regex("""(?:ajax_url|url)\s*:\s*['"]([^'"]*admin-ajax\.php)['"]""").find(scriptContent)
                    if (ajaxMatch != null) ajaxUrl = ajaxMatch.groupValues[1]
                }
            }

            val mirrors = doc.select(".server li, .mirror li, ul.daftarmirror li a, .megavid .server, div.server a, ul.mirror-select li")
            for (mirror in mirrors) {
                val postId = mirror.attr("data-post").ifEmpty { mirror.attr("data-id") }
                val num = mirror.attr("data-nume").ifEmpty { mirror.attr("data-server") }
                val type = mirror.attr("data-type").ifEmpty { "embed" }

                if (postId.isNotBlank() && num.isNotBlank()) {
                    val requestBody = FormBody.Builder()
                        .add("action", "player_ajax")
                        .add("post", postId)
                        .add("nume", num)
                        .add("type", type)

                    if (actionNonce != null) {
                        requestBody.add("nonce", actionNonce)
                    }

                    val request = Request.Builder()
                        .url(ajaxUrl)
                        .post(requestBody.build())
                        .header("Referer", episodeId)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .build()

                    try {
                        val response = NetworkModule.okHttpClient.newCall(request).execute()
                        if (response.isSuccessful) {
                            val jsonStr = response.body?.string() ?: ""
                            val iframeMatch = Regex("""src=\\?["'](.*?)\\?["']""").find(jsonStr)
                            val src = iframeMatch?.groupValues?.get(1)?.replace("\\/", "/")
                            if (src != null && !embedUrls.contains(src)) {
                                val serverName = mirror.text().trim().takeIf { it.isNotBlank() } ?: "Server ${embedUrls.size + 1}"
                                qualities.add(VideoQuality(serverName, src, headers = customHeaders))
                                embedUrls.add(src)
                            }
                        }
                    } catch (e: Exception) {
                        // ignore ajax errors
                    }
                }
            }

            // 3. Native CryptoJS AES decryptor detection
            val aesMatches = Regex("""["'](U2FsdGVkX1[a-zA-Z0-9+/=]{16,})["']""").findAll(html).toList()
            if (aesMatches.isNotEmpty()) {
                val candidateKeys = linkedSetOf<String>()
                if (defaultPassphrase.isNotBlank()) candidateKeys.add(defaultPassphrase)
                candidateKeys.add(sourceId)
                candidateKeys.add(baseUrl.removePrefix("https://").removePrefix("http://").substringBefore("/"))

                val passRegex = Regex("""(?:var|let|const|pass|key|secret)\s*[:=]\s*["']([^"']{4,48})["']""")
                for (m in passRegex.findAll(html)) {
                    val v = m.groupValues[1]
                    if (!v.startsWith("U2FsdGVkX1") && !v.startsWith("http")) candidateKeys.add(v)
                }

                for (aesMatch in aesMatches) {
                    val encrypted = aesMatch.groupValues[1]
                    for (k in candidateKeys) {
                        val dec = CryptoJsDecryptor.decrypt(encrypted, k) ?: continue
                        val parsed = CryptoJsDecryptor.parseDecryptedPayload(dec, episodeId)
                        if (parsed.qualities.isNotEmpty()) {
                            emit(VideoStreamInfo(
                                episodeId = episodeId,
                                qualities = parsed.qualities,
                                subtitles = parsed.subtitles,
                                headers = mapOf("Referer" to episodeId)
                            ))
                            return@flow
                        } else if (parsed.embedUrl.isNotBlank() && !embedUrls.contains(parsed.embedUrl)) {
                            embedUrls.add(parsed.embedUrl)
                            qualities.add(VideoQuality("AES Stream", parsed.embedUrl, headers = customHeaders))
                        }
                    }
                }
            }

            // 4. Dom Extractor universal server extraction
            val universalMirrors = AnimeDomExtractor.extractAllServersAndEmbeds(doc, currentBaseUrl(), episodeId)
            universalMirrors.forEach { (serverName, url) ->
                if (!embedUrls.contains(url)) {
                    qualities.add(VideoQuality(serverName, url, headers = customHeaders))
                    embedUrls.add(url)
                }
            }

            // 5. Direct iframes in HTML
            doc.select("iframe[src], iframe[data-src]").forEach { iframe ->
                val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
                if (src.isNotBlank() && !embedUrls.contains(src) && !src.contains("facebook.com") && !src.contains("disqus.com")) {
                    qualities.add(VideoQuality("Server ${embedUrls.size + 1}", src, headers = customHeaders))
                    embedUrls.add(src)
                }
            }

            // 6. Sniff each embed URL via VideoUrlInterceptor
            for (embedUrl in embedUrls) {
                val iframeInfo = interceptor.interceptVideo(embedUrl, episodeId, timeoutMs = 12000L, headers = customHeaders)
                if (iframeInfo != null && iframeInfo.qualities.isNotEmpty()) {
                    SourceStatusBus.clear(sourceId)
                    emit(iframeInfo)
                    return@flow
                }
                val streamUrl = interceptor.interceptStreamUrl(embedUrl, timeoutMs = 10000L, headers = customHeaders)
                if (!streamUrl.isNullOrBlank()) {
                    SourceStatusBus.clear(sourceId)
                    emit(VideoStreamInfo(
                        episodeId = episodeId,
                        embedUrl = streamUrl,
                        qualities = listOf(VideoQuality("Auto", streamUrl, isM3U8 = streamUrl.contains(".m3u8"), headers = customHeaders)),
                        headers = customHeaders
                    ))
                    return@flow
                }
            }

            val directQualities = qualities.filter { it.url.contains(".mp4") || it.url.contains(".m3u8") }
            if (directQualities.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(VideoStreamInfo(episodeId = episodeId, embedUrl = directQualities.first().url, qualities = directQualities, headers = customHeaders))
                return@flow
            } else if (embedUrls.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(VideoStreamInfo(episodeId = episodeId, embedUrl = embedUrls.first(), headers = customHeaders))
                return@flow
            }

            // Fallback to BaseAnimeSource
            super.getVideoStream(episodeId).collect { emit(it) }
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }
}
