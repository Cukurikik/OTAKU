package com.example.data.sources.base

import android.content.Context
import android.util.Base64
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.models.Page
import com.example.data.sources.ConfigurableSource
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import com.example.data.sources.VideoSource
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.WebViewFetcher
import com.example.data.sources.VideoExtractorEngine
import com.example.network.interceptor.VideoUrlInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

abstract class BaseAnimeSource(protected val context: Context) : VideoSource, ConfigurableSource {
    /** @deprecated kept for source compatibility; use [currentBaseUrl] for requests. */
    abstract val baseUrl: String
    override val defaultBaseUrl: String get() = baseUrl
    protected val fetcher by lazy { WebViewFetcher(context) }
    protected val videoExtractorEngine: VideoExtractorEngine by lazy { VideoExtractorEngine(context) }
    protected val videoUrlInterceptor: VideoUrlInterceptor by lazy { VideoUrlInterceptor(context) }
    override val language: String = "id"

    /** The URL to actually use for requests: user override (if set) or [baseUrl]. */
    protected fun currentBaseUrl(): String = effectiveBaseUrl(context)

    protected val healthProbe by lazy { com.example.data.sources.registry.SourceHealthProbe() }

    /**
     * Checks if the given URL belongs to an outdated mirror and remaps it to [currentBaseUrl].
     */
    protected fun adjustUrlToActiveDomain(url: String): String {
        val activeBase = currentBaseUrl()
        val allMirrors = com.example.data.sources.registry.SourceEndpointRegistry.getAllMirrors(sourceId)
        val urlHost = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: ""
        val activeHost = runCatching { java.net.URI(activeBase).host?.lowercase() }.getOrNull() ?: ""

        if (urlHost.isNotBlank() && activeHost.isNotBlank() && urlHost != activeHost) {
            val isKnownMirror = allMirrors.any { mirror ->
                val mHost = runCatching { java.net.URI(mirror).host?.lowercase() }.getOrNull() ?: ""
                mHost.isNotBlank() && (urlHost.contains(mHost) || mHost.contains(urlHost))
            }
            if (isKnownMirror) {
                return com.example.data.sources.registry.SourceHealthProbe.rewriteUrl(url, activeBase)
            }
        }
        return url
    }

    protected suspend fun fetchDocument(url: String): org.jsoup.nodes.Document {
        val targetUrl = adjustUrlToActiveDomain(url)
        return try {
            fetchDocumentInternal(targetUrl)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (shouldFailover(e)) {
                val backupDomain = healthProbe.probeAndFailover(context, sourceId, targetUrl)
                if (!backupDomain.isNullOrBlank()) {
                    val rewritten = com.example.data.sources.registry.SourceHealthProbe.rewriteUrl(targetUrl, backupDomain)
                    return fetchDocumentInternal(rewritten)
                }
            }
            throw e
        }
    }

    private fun shouldFailover(e: Exception): Boolean {
        if (e is java.net.UnknownHostException ||
            e is java.net.SocketTimeoutException ||
            e is java.net.ConnectException ||
            e is java.net.NoRouteToHostException ||
            e is javax.net.ssl.SSLException) {
            return true
        }
        val msg = e.message.orEmpty().lowercase()
        return msg.contains("failed to fetch") ||
               msg.contains("timeout") ||
               msg.contains("cloudflare bypass worker failed") ||
               msg.contains("http 5") ||
               msg.contains("connect") ||
               msg.contains("resolve")
    }

    protected suspend fun fetchDocumentInternal(url: String): org.jsoup.nodes.Document {
        var doc: org.jsoup.nodes.Document? = null
        var isCfChallenge = false
        try {
            doc = fetcher.fetchHtml(url, this.requestHeaders)
            val title = doc.title().lowercase()
            isCfChallenge = title.contains("just a moment") || 
                title.contains("attention required") || 
                title.contains("security check") || 
                title.contains("access denied") ||
                (doc.selectFirst("#challenge-running, #cf-challenge-running, #challenge-form, #turnstile-wrapper") != null)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("HTTP 403") || msg.contains("HTTP 503") || msg.contains("Cloudflare") || msg.contains("Just a moment")) {
                isCfChallenge = true
            } else {
                throw e
            }
        }

        if (isCfChallenge) {
            try {
                val host = java.net.URL(url).host
                com.example.data.NetworkModule.markAsBlocked(host)
                doc = fetcher.fetchHtml(url, this.requestHeaders)
            } catch (e: Exception) {
                // Secondary error ignored
            }

            val titleAfterWorker = doc?.title()?.lowercase() ?: ""
            val stillChallenged = doc == null ||
                titleAfterWorker.contains("just a moment") ||
                titleAfterWorker.contains("access denied") ||
                titleAfterWorker.contains("attention required")

            if (stillChallenged) {
                doc = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    kotlinx.coroutines.withTimeoutOrNull(25000L) {
                        kotlin.runCatching { fetcher.fetchHtmlRendered(url, 12000L, headers = this@BaseAnimeSource.requestHeaders) }.getOrNull()
                    }
                }
            }
        }
        return doc ?: throw Exception("Failed to fetch $url or Cloudflare bypass failed.")
    }

    /**
     * Mengambil daftar episode dari halaman detail anime.
     * Di Aniyomi/Tachiyomi, ini setara dengan getChapterList() tapi untuk episode.
     */
    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        try {
            val doc = fetchDocument(itemId)
            // Selector umum untuk episode list di situs anime Indonesia
            val episodes = doc.select(
                ".episode-list li, .episodelist li, #episode_page li, .eplister ul li"
            ).mapNotNull { el ->
                val a = el.selectFirst("a") ?: return@mapNotNull null
                val href = a.absUrl("href")
                val title = el.selectFirst(".epinum, .epNum, span:first-child")?.text()
                    ?: a.text().ifEmpty { "Episode" }
                val date = el.selectFirst(".date, .epdate")?.text() ?: ""
                Chapter(id = href, title = title, url = href, date = date)
            }.reversed() // Episode 1 di atas
            SourceStatusBus.clear(sourceId)
            emit(episodes)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            emit(emptyList())
        }
    }

    /**
     * Mengambil URL stream video dari halaman episode.
     * Menggunakan VideoUrlInterceptor dengan sniffing headless network, dekripsi payload otomatis,
     * serta ekstraksi iframe / mirror dinamis.
     */
    override fun getVideoStream(episodeId: String): Flow<VideoStreamInfo> = flow {
        try {
            val interceptor = com.example.network.interceptor.VideoUrlInterceptor(context)
            val customHeaders = this@BaseAnimeSource.requestHeaders.toMutableMap()
            if (!customHeaders.containsKey("Referer")) {
                customHeaders["Referer"] = episodeId
            }

            // 1. Sniffing langsung via VideoUrlInterceptor pada URL episode
            val directInterception = videoUrlInterceptor.interceptVideo(
                targetUrl = episodeId,
                episodeId = episodeId,
                timeoutMs = 15000L,
                headers = customHeaders
            )
            if (directInterception != null && directInterception.qualities.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(directInterception)
                return@flow
            }

            // 2. Fetch DOM untuk ekstraksi multi-server, CryptoJS AES, iframe embeds, atau daftar server
            val doc = fetchDocument(episodeId)
            val pageHtml = doc.html()

            // Coba ekstraksi multi-server streams terlebih dahulu (Katsu, AnimeStream, OtakuPress, dll.)
            val multiServerStreams = extractMultiServerStreams(pageHtml, episodeId)
            if (multiServerStreams.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                val allQualities = multiServerStreams.flatMap { it.qualities }.ifEmpty {
                    multiServerStreams.map {
                        VideoQuality(it.quality, it.url, it.url.contains(".m3u8"), customHeaders)
                    }
                }.distinctBy { it.url }

                val allServers = multiServerStreams.map {
                    VideoServer(
                        name = it.provider.ifBlank { it.quality },
                        embedUrl = it.embedUrl.ifBlank { it.url },
                        qualities = if (it.qualities.isNotEmpty()) it.qualities else listOf(VideoQuality(it.quality, it.url, it.url.contains(".m3u8"), customHeaders)),
                        headers = customHeaders
                    )
                }.distinctBy { it.embedUrl }

                emit(VideoStreamInfo(
                    episodeId = episodeId,
                    embedUrl = multiServerStreams.first().url,
                    qualities = allQualities,
                    servers = allServers,
                    headers = customHeaders
                ))
                return@flow
            }

            val qualities = mutableListOf<VideoQuality>()
            val embedUrls = mutableSetOf<String>()

            // Auto-decrypt CryptoJS AES payloads jika ada pada DOM
            val aesMatches = Regex("""(?:CryptoJS\.AES\.decrypt\s*\(\s*["']([^"']+)["']|data-encrypted=["']([^"']+)["']|["'](U2FsdGVkX1[a-zA-Z0-9+/=]{16,})["'])""").findAll(pageHtml)
            val candidateKeys = listOf(sourceId, "kurama", "animasu", "nimegami", "otakudesu", "anichin", "kazefuri", "secret", "stream")
            for (match in aesMatches) {
                val ciphertext = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: continue
                for (key in candidateKeys) {
                    val decrypted = com.example.data.sources.anime.extractors.CryptoJsDecryptor.decrypt(ciphertext, key)
                    if (!decrypted.isNullOrBlank()) {
                        val parsed = com.example.data.sources.anime.extractors.CryptoJsDecryptor.parseDecryptedPayload(decrypted, episodeId)
                        qualities.addAll(parsed.qualities)
                        if (parsed.embedUrl.isNotBlank()) embedUrls.add(parsed.embedUrl)
                    }
                }
            }

            // Cari semua iframe dan player embed
            doc.select("iframe[src], iframe[data-src], div.player-embed iframe, #pembed iframe").forEach { iframe ->
                val src = iframe.absUrl("src").ifEmpty { iframe.absUrl("data-src") }
                if (src.isNotBlank() && !src.contains("facebook") && !src.contains("disqus") && !src.contains("twitter")) {
                    embedUrls.add(src)
                }
            }

            // Cari server options / select mirrors
            doc.select("select#server option, select.mirror option, select option, ul.servers li, .server-item, .server-list li, ul.server-list li").forEach { option ->
                val v = option.attr("value").ifEmpty { option.attr("data-server") }.ifEmpty { option.attr("data-id") }.ifEmpty { option.attr("data-post") }
                if (v.startsWith("http")) {
                    embedUrls.add(v)
                } else if (v.length > 20 && !v.contains(" ")) {
                    runCatching {
                        val decoded = String(Base64.decode(v, Base64.DEFAULT))
                        val iframeSrc = Jsoup.parseBodyFragment(decoded).selectFirst("iframe")?.let {
                            it.attr("src").ifEmpty { it.attr("data-src") }
                        }
                        if (!iframeSrc.isNullOrBlank()) {
                            val resolved = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else iframeSrc
                            embedUrls.add(resolved)
                        }
                    }
                }
            }

            // Jika dari dekripsi AES sudah ditemukan stream m3u8/mp4 langsung
            if (qualities.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                val best = qualities.firstOrNull { it.isM3U8 } ?: qualities.first()
                emit(VideoStreamInfo(
                    episodeId = episodeId,
                    embedUrl = best.url,
                    qualities = qualities.distinctBy { it.url },
                    headers = customHeaders
                ))
                return@flow
            }

            // 3. Sniff & Deep Extract tiap embed URL yang ditemukan menggunakan VideoExtractorEngine & VideoUrlInterceptor
            val resolvedServers = mutableListOf<VideoServer>()
            for (embedUrl in embedUrls) {
                // Try VideoExtractorEngine first for known hosts (VidHide, Streamwish, FileLions, Dood, Blogger, etc.)
                val engineExtracted = runCatching { videoExtractorEngine.extractVideo(embedUrl, episodeId, customHeaders = customHeaders) }.getOrNull()
                if (engineExtracted != null && engineExtracted.qualities.isNotEmpty()) {
                    qualities.addAll(engineExtracted.qualities)
                    resolvedServers.add(VideoServer(
                        name = embedUrl.detectProviderName(),
                        embedUrl = embedUrl,
                        qualities = engineExtracted.qualities,
                        headers = customHeaders
                    ))
                    continue
                }

                val iframeInfo = videoUrlInterceptor.interceptVideo(embedUrl, episodeId, timeoutMs = 12000L, headers = customHeaders)
                if (iframeInfo != null && iframeInfo.qualities.isNotEmpty()) {
                    qualities.addAll(iframeInfo.qualities)
                    resolvedServers.add(VideoServer(
                        name = embedUrl.detectProviderName(),
                        embedUrl = embedUrl,
                        qualities = iframeInfo.qualities,
                        headers = customHeaders
                    ))
                    continue
                }

                val streamUrl = videoUrlInterceptor.interceptStreamUrl(embedUrl, timeoutMs = 10000L, headers = customHeaders)
                if (!streamUrl.isNullOrBlank()) {
                    val q = VideoQuality("Auto", streamUrl, isM3U8 = streamUrl.contains(".m3u8"), headers = customHeaders)
                    qualities.add(q)
                    resolvedServers.add(VideoServer(
                        name = embedUrl.detectProviderName(),
                        embedUrl = embedUrl,
                        qualities = listOf(q),
                        headers = customHeaders
                    ))
                }
            }

            if (qualities.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(VideoStreamInfo(
                    episodeId = episodeId,
                    embedUrl = resolvedServers.firstOrNull()?.embedUrl ?: qualities.first().url,
                    qualities = qualities.distinctBy { it.url },
                    servers = resolvedServers,
                    headers = customHeaders
                ))
                return@flow
            }

            // Fallback Extractor Chain: Unpack packed JS, CryptoJS, VidHide, StreamWish, etc. from pageHtml
            val fallbackChain = com.example.data.sources.anime.extractors.FallbackExtractorChain()
            val chainQualities = fallbackChain.extractQualities(pageHtml, episodeId, customHeaders)
            if (chainQualities.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(VideoStreamInfo(
                    episodeId = episodeId,
                    embedUrl = chainQualities.first().url,
                    qualities = chainQualities.distinctBy { it.url },
                    headers = customHeaders
                ))
                return@flow
            }

            // Fallback: embed URL pertama jika ada
            if (embedUrls.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(VideoStreamInfo(episodeId = episodeId, embedUrl = embedUrls.first(), headers = customHeaders))
                return@flow
            }

            throw java.io.IOException("Tidak ditemukan tautan video/embed stream aktif pada sumber $sourceName.")
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }

    /**
     * Ekstraksi Multi-Server & Quality Parsing secara komprehensif.
     * Mengumpulkan seluruh node option[value], li[data-post], iframe, mirror-stream untuk mengumpulkan
     * semua kombinasi provider dan resolusi (360p - 1080p).
     */
    open suspend fun extractMultiServerStreams(pageHtml: String, episodeUrl: String = ""): List<VideoStreamInfo> {
        val document = Jsoup.parse(pageHtml)
        val streamList = mutableListOf<VideoStreamInfo>()

        // 1. Ekstraksi Elemen Select / Option (Tema Katsu & Generic), iframes, dan server list items
        val serverOptions = document.select(
            "select#server option, div.player-embed iframe, ul.server-list li, select.mirror option, " +
            "div.mirror-stream a, div.mirror-stream li, .mirror-stream a, .mirror-stream li, " +
            ".player-embed iframe, select#changeServer option, .mirrorstream a, .mirror-item a"
        )

        serverOptions.forEach { element ->
            val embedUrl = extractEmbedUrlFromElement(element)
            if (embedUrl.isNotBlank() && !embedUrl.startsWith("#") && !embedUrl.startsWith("javascript:")) {
                val resolvedStream = videoUrlInterceptor.interceptStreamUrl(embedUrl, timeoutMs = 8000L, headers = this.requestHeaders)
                val finalUrl = resolvedStream ?: embedUrl
                val quality = element.text().extractQualityLabel()
                val provider = embedUrl.detectProviderName()
                streamList.add(
                    VideoStreamInfo(
                        url = finalUrl,
                        quality = quality,
                        provider = provider,
                        episodeId = episodeUrl,
                        embedUrl = embedUrl,
                        headers = this.requestHeaders
                    )
                )
            }
        }

        return streamList.distinctBy { it.embedUrl.ifBlank { it.url } }
    }

    protected open fun extractEmbedUrlFromElement(element: Element): String {
        val rawData = element.attr("value")
            .ifBlank { element.attr("data-post") }
            .ifBlank { element.attr("data-content") }
            .ifBlank { element.attr("data-url") }
            .ifBlank { element.attr("data-embed") }
            .ifBlank { element.attr("data-video") }
            .ifBlank { element.attr("data-src") }
            .ifBlank { if (element.`is`("a")) element.attr("href") else "" }

        return if (rawData.isBase64Encoded()) {
            try {
                val decoded = String(Base64.decode(rawData, Base64.DEFAULT))
                if (decoded.contains("<iframe", ignoreCase = true)) {
                    val iframeDoc = Jsoup.parseBodyFragment(decoded)
                    iframeDoc.selectFirst("iframe")?.let { it.attr("src").ifEmpty { it.attr("data-src") } } ?: decoded
                } else {
                    decoded
                }
            } catch (e: Exception) {
                rawData
            }
        } else {
            element.attr("src").ifBlank { element.attr("data-src") }.ifBlank { rawData }
        }
    }
}

fun String.isBase64Encoded(): Boolean {
    val clean = this.trim()
    if (clean.length < 8) return false
    if (clean.startsWith("http://") || clean.startsWith("https://") || clean.startsWith("/")) return false
    if (clean.contains(" ") || clean.contains("<") || clean.contains(">")) return false
    return try {
        val decoded = Base64.decode(clean, Base64.DEFAULT)
        decoded.isNotEmpty() && clean.matches(Regex("^[A-Za-z0-9+/=]+$"))
    } catch (e: Exception) {
        false
    }
}

fun String.detectProviderName(): String {
    val lower = this.lowercase()
    return when {
        lower.contains("streamwish") || lower.contains("wish") -> "Streamwish"
        lower.contains("vidhide") || lower.contains("callister") -> "VidHide"
        lower.contains("filelions") || lower.contains("lion") -> "FileLions"
        lower.contains("dood") || lower.contains("ds2play") || lower.contains("do0od") -> "Doodstream"
        lower.contains("streamtape") || lower.contains("tape") -> "Streamtape"
        lower.contains("kraken") -> "Krakenfiles"
        lower.contains("gofile") -> "Gofile"
        lower.contains("mp4upload") -> "Mp4Upload"
        lower.contains("blogger") || lower.contains("bp.blogspot") -> "Blogger"
        lower.contains("drive.google") || lower.contains("googleusercontent") -> "GoogleDrive"
        lower.contains("acefile") -> "AceFile"
        lower.contains("ok.ru") || lower.contains("odnoklassniki") -> "OkRu"
        lower.contains("fembed") || lower.contains("vidiocdn") -> "Fembed"
        lower.contains("kurama") -> "KuramaStream"
        lower.contains("animasu") -> "AnimasuStream"
        lower.contains("nimegami") -> "NimegamiStream"
        else -> runCatching { java.net.URI(this).host?.removePrefix("www.") }.getOrNull() ?: "Server"
    }
}

fun String.extractQualityLabel(): String {
    val match = Regex("""(1080p?|720p?|480p?|360p?|HD|FHD|SD)""", RegexOption.IGNORE_CASE).find(this)
    val label = match?.groupValues?.get(1)?.uppercase()
    return when (label) {
        "1080", "1080P", "FHD" -> "1080p"
        "720", "720P", "HD" -> "720p"
        "480", "480P", "SD" -> "480p"
        "360", "360P" -> "360p"
        else -> if (this.isNotBlank() && this.length < 25) this.trim() else "Auto"
    }
}
