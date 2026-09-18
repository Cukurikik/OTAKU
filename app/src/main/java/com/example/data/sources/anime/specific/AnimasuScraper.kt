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
import com.example.data.sources.base.BaseAnimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

class AnimasuScraper(context: Context) : BaseAnimeSource(context) {
    override val sourceId = "animasu"
    override val sourceName = "Animasu"
    override val baseUrl = "https://v2.animasu.work"

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val url = if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/"
            val doc = fetchDocument(url)
            val items = AnimeDomExtractor.extractCards(doc, sourceId, sourceName, currentBaseUrl(), ".bsx, .bixbox .bs")
            emit(items)
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            emit(emptyList())
        }
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = if (page == 1) "$baseUrl/?s=$q" else "$baseUrl/page/$page/?s=$q"
            val doc = fetchDocument(url)
            val items = AnimeDomExtractor.extractCards(doc, sourceId, sourceName, currentBaseUrl(), ".bsx, .bixbox .bs")
            emit(items)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        try {
            val doc = fetchDocument(itemId)
            val item = AnimeDomExtractor.extractDetail(doc, sourceId, sourceName, itemId, currentBaseUrl())
            if (item != null && item.title != "Unknown") return item
            
            val title = doc.selectFirst(".entry-title")?.text()?.trim() ?: "Unknown"
            val img = doc.selectFirst(".thumb img, .kover img")?.let {
                it.attr("src").ifBlank { it.attr("data-src") }
            } ?: ""
            val desc = doc.selectFirst(".entry-content, .desc, .mindes")?.text()?.trim() ?: ""
            val genres = doc.select(".genxed a, .info a[rel='tag']").map { it.text().trim() }
            
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
            val eps = AnimeDomExtractor.extractEpisodes(doc, currentBaseUrl(), ".lchx a, .eplister ul li a, .eplister li a, .episodelist li a")
            
            if (eps.isNotEmpty()) {
                emit(eps)
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

    suspend fun fetchInternalPostApi(
        targetUrl: String,
        referer: String,
        formData: Map<String, String>,
        extraHeaders: Map<String, String> = emptyMap()
    ): String? {
        return try {
            val customUa = SourceHeaderStore.getUserAgent(context, sourceId)
            val customCookie = SourceHeaderStore.getCookie(context, sourceId)
            val formBodyBuilder = FormBody.Builder()
            for ((k, v) in formData) {
                formBodyBuilder.add(k, v)
            }
            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .header("Referer", referer)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .post(formBodyBuilder.build())
            
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

            // 2. CryptoJS decryption with passphrase "animasu"
            val cryptoRegex = Regex("""(?:CryptoJS\.AES\.decrypt\s*\(\s*["']([^"']+)["']|data-encrypted=["']([^"']+)["']|["']Salted__([A-Za-z0-9+/=]+)["'])""")
            for (match in cryptoRegex.findAll(html)) {
                val ciphertext = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: continue
                val payload = if (!ciphertext.startsWith("Salted__") && match.value.contains("Salted__")) "Salted__$ciphertext" else ciphertext
                val decrypted = com.example.data.sources.anime.extractors.CryptoJsDecryptor.decrypt(payload, "animasu")
                    ?: com.example.data.sources.anime.extractors.CryptoJsDecryptor.decrypt(payload, "kurama")
                    ?: com.example.data.sources.anime.extractors.CryptoJsDecryptor.decrypt(payload, "secret")
                if (!decrypted.isNullOrBlank()) {
                    val parsed = com.example.data.sources.anime.extractors.CryptoJsDecryptor.parseDecryptedPayload(decrypted, episodeId)
                    qualities.addAll(parsed.qualities)
                    if (parsed.embedUrl.isNotBlank()) embedUrls.add(parsed.embedUrl)
                }
            }

            // 3. Reverse Engineering Internal AJAX Endpoints (Bypass Visual DOM -> Tembak API Internal)
            val ajaxVars = doc.select("script:containsData(admin-ajax.php), script:containsData(action)").toString()
            if (ajaxVars.isNotBlank()) {
                val ajaxUrlMatch = Regex("""(https?://[^"'\s]+admin-ajax\.php)""").find(ajaxVars)
                val ajaxUrl = ajaxUrlMatch?.groupValues?.get(1) ?: "$baseUrl/wp-admin/admin-ajax.php"
                
                doc.select(".server, .mobius, .mirrors option, [data-post]").forEach { elem ->
                    val postId = elem.attr("data-post").ifEmpty { elem.attr("value") }
                    val numId = elem.attr("data-nume").ifEmpty { elem.attr("data-index") }
                    val type = elem.attr("data-type")
                    
                    if (postId.isNotBlank() && numId.isNotBlank()) {
                        val formData = mapOf(
                            "action" to "player_ajax",
                            "post" to postId,
                            "nume" to numId,
                            "type" to type
                        )
                        val jsonBody = fetchInternalPostApi(ajaxUrl, episodeId, formData)
                        if (!jsonBody.isNullOrBlank()) {
                            val streams = ApiResponseParser.extractStreamUrls(ajaxUrl, jsonBody)
                            for (stream in streams) {
                                if (!embedUrls.contains(stream)) {
                                    val name = elem.text().trim().ifEmpty { "Server ${embedUrls.size + 1}" }
                                    qualities.add(VideoQuality(name, stream, isM3U8 = stream.contains(".m3u8"), headers = headers))
                                    embedUrls.add(stream)
                                }
                            }
                            
                            // In case the AJAX response returns an iframe source directly instead of JSON:
                            if (jsonBody.contains("<iframe")) {
                                val srcMatch = Regex("""src=["']([^"']+)["']""").find(jsonBody)
                                val iframeSrc = srcMatch?.groups?.get(1)?.value
                                if (!iframeSrc.isNullOrBlank() && !embedUrls.contains(iframeSrc)) {
                                    embedUrls.add(iframeSrc)
                                }
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

            // 5. Fallback: Parse select option base64 values for iframes
            doc.select("select.mirror option, option[value]").forEach { option ->
                val base64 = option.attr("value")
                val text = option.text().trim().takeIf { it.isNotBlank() && !it.contains("Pilih", true) } ?: "Server ${embedUrls.size + 1}"
                if (base64.isNotBlank() && base64.length > 20 && !base64.contains(" ")) {
                    try {
                        val decodedHtml = String(Base64.decode(base64, Base64.DEFAULT))
                        val streams = ApiResponseParser.extractStreamUrls(episodeId, decodedHtml)
                        for (s in streams) {
                            if (!embedUrls.contains(s)) {
                                qualities.add(VideoQuality(text, s, isM3U8 = s.contains(".m3u8"), headers = headers))
                                embedUrls.add(s)
                            }
                        }
                        
                        val iframe = Jsoup.parseBodyFragment(decodedHtml).selectFirst("iframe")
                        val src = iframe?.attr("src") ?: iframe?.attr("data-src")
                        if (!src.isNullOrBlank() && !embedUrls.contains(src)) {
                            embedUrls.add(src)
                        }
                    } catch (_: Exception) {}
                } else if (base64.startsWith("http") && !embedUrls.contains(base64)) {
                     embedUrls.add(base64)
                }
            }

            // 6. Check for direct iframes in HTML
            doc.select("iframe[src], iframe[data-src]").forEach { iframe ->
                val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
                if (src.isNotBlank() && !embedUrls.contains(src) && !src.contains("facebook.com")) {
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

            throw java.io.IOException("Gagal mengekstrak tautan video dari halaman $episodeId")
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }
}

