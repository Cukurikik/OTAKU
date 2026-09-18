package com.example.data.sources.anime.specific

import android.content.Context
import android.util.Base64
import com.example.data.NetworkModule
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.SourceHeaderStore
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.anime.base.AnimeDomExtractor
import com.example.data.sources.anime.extractors.ApiResponseParser
import com.example.data.sources.anime.extractors.CryptoJsDecryptor
import com.example.data.sources.anime.extractors.JsPackerUnpacker
import com.example.data.sources.base.BaseAnimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

class KuramanimeScraper(context: Context) : BaseAnimeSource(context) {
    override val sourceId = "kuramanime"
    override val sourceName = "Kuramanime"
    override val baseUrl = "https://v19.kuramanime.ing"

    private val cardSelector: String
        get() = com.example.data.sources.dynamic.DynamicSourceConfigManager.getSelector(
            sourceId, "card", "#animeList a, .filter__gallery a, .product__item a, .product__sidebar__view__item a"
        )
    private val titleSelector: String
        get() = com.example.data.sources.dynamic.DynamicSourceConfigManager.getSelector(
            sourceId, "title", "h5, .sidebar-title-h5, .title, .entry-title, h3"
        )
    private val imageSelector: String
        get() = com.example.data.sources.dynamic.DynamicSourceConfigManager.getSelector(
            sourceId, "card_image", ".product__sidebar__view__item, .set-bg, img"
        )

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val url = if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/"
            val doc = fetchDocument(url)
            var items = AnimeDomExtractor.extractCards(doc, sourceId, sourceName, currentBaseUrl(), cardSelector)
            if (items.isEmpty()) {
                items = doc.select(cardSelector).mapNotNull {
                    val link = it.attr("href").takeIf { h -> h.contains("/anime/") } ?: return@mapNotNull null
                    val img = it.selectFirst(imageSelector)?.let { el ->
                        el.attr("data-setbg").ifEmpty { el.attr("src") }
                    } ?: ""
                    val title = it.selectFirst(titleSelector)?.text()?.trim() ?: "Unknown"
                    
                    ContentItem(
                        id = link,
                        title = title,
                        source = sourceName,
                        imageUrl = img,
                        type = ContentType.ANIME,
                        sourceId = sourceId
                    )
                }.distinctBy { it.id }
            }
            emit(items)
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            emit(emptyList())
        }
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = if (page <= 1) "$baseUrl/anime?search=$q&order_by=latest" else "$baseUrl/anime?search=$q&order_by=latest&page=$page"
            val doc = fetchDocument(url)
            var items = AnimeDomExtractor.extractCards(doc, sourceId, sourceName, currentBaseUrl(), cardSelector)
            if (items.isEmpty()) {
                items = doc.select(cardSelector).mapNotNull {
                    val link = it.attr("href").takeIf { h -> h.contains("/anime/") } ?: return@mapNotNull null
                    val img = it.selectFirst(imageSelector)?.let { el ->
                        el.attr("data-setbg").ifEmpty { el.attr("src") }
                    } ?: ""
                    val title = it.selectFirst(titleSelector)?.text()?.trim() ?: "Unknown"
                    
                    ContentItem(
                        id = link,
                        title = title,
                        source = sourceName,
                        imageUrl = img,
                        type = ContentType.ANIME,
                        sourceId = sourceId
                    )
                }.distinctBy { it.id }
            }
            emit(items)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        try {
            val doc = fetchDocument(itemId)
            val extracted = AnimeDomExtractor.extractDetail(doc, sourceId, sourceName, itemId, currentBaseUrl())
            if (extracted != null && extracted.title != "Unknown" && extracted.imageUrl.isNotBlank()) {
                return extracted
            }
            val title = doc.selectFirst(".anime__details__title h3, h1.entry-title, .title")?.text()?.trim() ?: "Unknown"
            val img = doc.selectFirst(".anime__details__pic, .thumb img, .set-bg")?.let { el ->
                el.attr("data-setbg").ifEmpty { el.attr("src") }
            } ?: ""
            val desc = doc.selectFirst(".anime__details__text p, .entry-content p, .synopsis")?.text()?.trim() ?: ""
            val genres = doc.select(".anime__details__widget ul li:contains(Genre) a, .genre-info a, .genres a").map { it.text().trim() }
            
            return ContentItem(
                id = itemId,
                title = title,
                source = sourceName,
                imageUrl = img,
                description = desc,
                genres = genres,
                type = ContentType.ANIME,
                sourceId = sourceId
            )
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            return null
        }
    }
    
    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        try {
            val doc = fetchDocument(itemId)
            val chapters = mutableListOf<Chapter>()

            // 1. Check Bootstrap popover or data-content
            val episodeListsContent = doc.selectFirst("a#episodeLists, #episodeLists, [data-content*='episode']")?.attr("data-content")
            if (!episodeListsContent.isNullOrBlank()) {
                val popoverDoc = Jsoup.parseBodyFragment(episodeListsContent)
                popoverDoc.select("a").forEach { a ->
                    val href = a.attr("href")
                    if (href.isNotBlank()) {
                        val epNum = href.substringAfterLast("/").substringBefore("?")
                        chapters.add(Chapter(id = href, title = "Episode $epNum", url = href, date = ""))
                    }
                }
            }

            // 2. Direct episode buttons in document
            if (chapters.isEmpty()) {
                doc.select("a.ep-button, .ep-list a, .episodelist li a, #episode-list a, a.btn-primary:contains(Episode), a.btn-danger[href*=/episode/]").forEach { it ->
                    val href = it.attr("href")
                    if (href.isNotBlank()) {
                        val epText = it.text().trim()
                        val title = if (epText.contains("Episode", ignoreCase = true)) epText else {
                            val epNum = href.substringAfterLast("/").substringBefore("?")
                            "Episode $epNum"
                        }
                        chapters.add(Chapter(id = href, title = title, url = href, date = ""))
                    }
                }
            }

            // 3. Reverse engineering API fetch for episodes if DOM is empty
            if (chapters.isEmpty()) {
                val animeIdMatch = Regex("""/anime/(\d+)""").find(itemId)
                if (animeIdMatch != null) {
                    val animeId = animeIdMatch.groupValues[1]
                    val apiEpUrl = "$baseUrl/api/v1/anime/$animeId/episodes"
                    try {
                        val req = Request.Builder()
                            .url(apiEpUrl)
                            .header("Referer", itemId)
                            .header("X-Requested-With", "XMLHttpRequest")
                            .build()
                        val resp = NetworkModule.okHttpClient.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: ""
                            val parsedUrls = ApiResponseParser.extractStreamUrls(apiEpUrl, body)
                            parsedUrls.forEachIndexed { idx, url ->
                                chapters.add(Chapter(id = url, title = "Episode ${idx + 1}", url = url))
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            if (chapters.isNotEmpty()) {
                emit(chapters.reversed().distinctBy { it.url })
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    /**
     * Reverse Engineering API call: bypasses visual DOM to fetch pure JSON/stream payload
     */
    suspend fun fetchInternalApi(
        targetUrl: String,
        referer: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): String? {
        return try {
            val customUa = SourceHeaderStore.getUserAgent(context, sourceId)
            val customCookie = SourceHeaderStore.getCookie(context, sourceId)
            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .header("Referer", referer)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
            
            if (!customUa.isNullOrBlank()) reqBuilder.header("User-Agent", customUa)
            if (!customCookie.isNullOrBlank()) reqBuilder.header("Cookie", customCookie)
            for ((k, v) in extraHeaders) {
                reqBuilder.header(k, v)
            }
            
            val resp = NetworkModule.okHttpClient.newCall(reqBuilder.build()).execute()
            if (resp.isSuccessful) resp.body?.string() else null
        } catch (_: Exception) {
            null
        }
    }

    override fun getVideoStream(episodeId: String): Flow<VideoStreamInfo> = flow {
        try {
            val headers = mutableMapOf<String, String>("Referer" to episodeId)
            val customUa = SourceHeaderStore.getUserAgent(context, sourceId)
            val customCookie = SourceHeaderStore.getCookie(context, sourceId)
            if (!customUa.isNullOrBlank()) headers["User-Agent"] = customUa
            if (!customCookie.isNullOrBlank()) headers["Cookie"] = customCookie

            val interceptor = com.example.network.interceptor.VideoUrlInterceptor(context)

            // 1. Headless Network Sniffing directly on episodeId
            val directInterception = interceptor.interceptVideo(episodeId, episodeId, timeoutMs = 15000L, headers = headers)
            if (directInterception != null && directInterception.qualities.isNotEmpty()) {
                emit(directInterception)
                return@flow
            }

            val doc = fetchDocument(episodeId)
            val html = doc.html()
            val qualities = mutableListOf<VideoQuality>()
            val embedUrls = mutableSetOf<String>()

            // 2. CryptoJS decryption on page payloads (e.g. key "kurama")
            val cryptoRegex = Regex("""(?:CryptoJS\.AES\.decrypt\s*\(\s*["']([^"']+)["']|data-encrypted=["']([^"']+)["']|["']Salted__([A-Za-z0-9+/=]+)["'])""")
            for (match in cryptoRegex.findAll(html)) {
                val ciphertext = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: continue
                val payload = if (!ciphertext.startsWith("Salted__") && match.value.contains("Salted__")) "Salted__$ciphertext" else ciphertext
                val decrypted = com.example.data.sources.anime.extractors.CryptoJsDecryptor.decrypt(payload, "kurama")
                    ?: com.example.data.sources.anime.extractors.CryptoJsDecryptor.decrypt(payload, "secret")
                if (!decrypted.isNullOrBlank()) {
                    val parsed = com.example.data.sources.anime.extractors.CryptoJsDecryptor.parseDecryptedPayload(decrypted, episodeId)
                    qualities.addAll(parsed.qualities)
                    if (parsed.embedUrl.isNotBlank()) embedUrls.add(parsed.embedUrl)
                }
            }

            // 3. Reverse Engineering Internal AJAX Endpoints (Bypass Visual DOM -> Tembak API Internal)
            val serverElements = doc.select("[data-server], [data-id], .server-item, option[data-server]")
            for (elem in serverElements) {
                val serverId = elem.attr("data-server").ifEmpty { elem.attr("data-id") }.ifEmpty { elem.attr("value") }
                if (serverId.isNotBlank()) {
                    val ajaxEndpoint = "$baseUrl/ajax/episode/server?id=$serverId"
                    val jsonBody = fetchInternalApi(ajaxEndpoint, episodeId)
                    if (!jsonBody.isNullOrBlank()) {
                        val streams = ApiResponseParser.extractStreamUrls(ajaxEndpoint, jsonBody)
                        for (stream in streams) {
                            if (!embedUrls.contains(stream)) {
                                val name = elem.text().trim().ifEmpty { "Server ${embedUrls.size + 1}" }
                                qualities.add(VideoQuality(name, stream, isM3U8 = stream.contains(".m3u8"), headers = headers))
                                embedUrls.add(stream)
                            }
                        }
                    }
                }
            }

            // 4. Direct decrypt CryptoJS / eval packed JS in HTML
            val directStreamsFromHtml = ApiResponseParser.extractStreamUrls(episodeId, html)
            for (stream in directStreamsFromHtml) {
                if (!embedUrls.contains(stream)) {
                    qualities.add(VideoQuality("Direct Stream ${qualities.size + 1}", stream, isM3U8 = stream.contains(".m3u8"), headers = headers))
                    embedUrls.add(stream)
                }
            }

            // 5. Fallback: Parse select option base64 values
            doc.select("select option, select.mirror option").forEach { option ->
                val v = option.attr("value")
                val text = option.text().trim().ifEmpty { "Server" }
                if (v.isNotBlank() && v.startsWith("http") && !v.contains("kuramanime")) {
                    if (!embedUrls.contains(v)) {
                        qualities.add(VideoQuality(text, v, isM3U8 = v.contains(".m3u8"), headers = headers))
                        embedUrls.add(v)
                    }
                } else if (v.length > 20 && !v.contains(" ")) {
                    try {
                        val decoded = String(Base64.decode(v, Base64.DEFAULT))
                        val streams = ApiResponseParser.extractStreamUrls(episodeId, decoded)
                        for (s in streams) {
                            if (!embedUrls.contains(s)) {
                                qualities.add(VideoQuality(text, s, isM3U8 = s.contains(".m3u8"), headers = headers))
                                embedUrls.add(s)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // 6. Check iframes in HTML
            doc.select("iframe[src], iframe[data-src]").forEach { iframe ->
                val src = iframe.absUrl("src").ifEmpty { iframe.absUrl("data-src") }
                if (src.isNotBlank() && !src.contains("kuramachat") && !embedUrls.contains(src)) {
                    embedUrls.add(src)
                }
            }

            if (qualities.isNotEmpty()) {
                val best = qualities.firstOrNull { it.isM3U8 } ?: qualities.first()
                emit(VideoStreamInfo(episodeId = episodeId, embedUrl = best.url, qualities = qualities.distinctBy { it.url }, headers = headers))
                return@flow
            }

            // 7. Headless Sniffing on discovered embed/iframe URLs
            for (embedUrl in embedUrls) {
                val iframeInfo = interceptor.interceptVideo(embedUrl, episodeId, timeoutMs = 12000L, headers = headers)
                if (iframeInfo != null && iframeInfo.qualities.isNotEmpty()) {
                    emit(iframeInfo)
                    return@flow
                }
            }

            if (embedUrls.isNotEmpty()) {
                emit(VideoStreamInfo(episodeId = episodeId, embedUrl = embedUrls.first(), headers = headers))
                return@flow
            }

            throw java.io.IOException("Tidak ditemukan tautan video/embed stream aktif pada sumber $sourceName.")
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }
}

