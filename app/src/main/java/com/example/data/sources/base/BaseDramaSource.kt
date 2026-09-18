package com.example.data.sources.base

import android.content.Context
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.models.Page
import com.example.data.sources.ConfigurableSource
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.VideoSource
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.WebViewFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

abstract class BaseDramaSource(protected val context: Context) : VideoSource, ConfigurableSource {
    open val contentType: ContentType = ContentType.DRAMA
    override val type: ContentType get() = contentType
    /** @deprecated kept for source compatibility; use [currentBaseUrl] for requests. */
    abstract val baseUrl: String
    override val defaultBaseUrl: String get() = baseUrl
    protected val fetcher by lazy { WebViewFetcher(context) }
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

    open protected suspend fun fetchDocument(url: String): org.jsoup.nodes.Document {
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
        return msg.contains("gagal memuat") ||
               msg.contains("timeout") ||
               msg.contains("failed to fetch") ||
               msg.contains("http 5") ||
               msg.contains("connect") ||
               msg.contains("resolve")
    }

    open protected suspend fun fetchDocumentInternal(url: String): org.jsoup.nodes.Document {
        var doc = kotlin.runCatching { fetcher.fetchHtml(url, this.requestHeaders) }.getOrNull()
        val title = doc?.title()?.lowercase() ?: ""
        val isCfChallenge = doc == null || 
            title.contains("just a moment") || 
            title.contains("cloudflare") || 
            title.contains("access denied") || 
            title.contains("attention required") || 
            title.contains("security check") ||
            (doc?.selectFirst("#cf-challenge-running, #cf-wrapper, .cf-browser-verification, #challenge-form, [name='cf-turnstile-response']") != null)

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
                        kotlin.runCatching { fetcher.fetchHtmlRendered(url, 12000L, headers = this@BaseDramaSource.requestHeaders) }.getOrNull()
                    }
                }
            }
        }
        return doc ?: throw Exception("Gagal memuat halaman ($url). Periksa koneksi internet.")
    }


    protected val videoUrlInterceptor by lazy { com.example.network.interceptor.VideoUrlInterceptor(context) }

    /**
     * Parses multi-season structure and episode list from drama details page.
     * Supports season tabs, dropdowns, season containers, and flat episode lists.
     */
    open suspend fun fetchSeasonsAndEpisodes(dramaUrl: String): List<DramaSeason> {
        val doc = fetchDocument(dramaUrl)
        val seasons = mutableListOf<DramaSeason>()

        // 1. Check for season tabs/containers (.tab-pane, .season-list, [data-season], etc.)
        val seasonContainers = doc.select(".tab-pane[id*='season'], .seasons-holder .season, .season-tab-content, .season-list .season-item, .hal-season")
        if (seasonContainers.isNotEmpty()) {
            seasonContainers.forEachIndexed { index, container ->
                val sNum = container.attr("data-season").toIntOrNull()
                    ?: container.id().filter { it.isDigit() }.toIntOrNull()
                    ?: (index + 1)
                val sTitle = container.selectFirst("h2, h3, .season-title")?.text()?.trim() ?: "Season $sNum"
                val epElements = container.select(".episodelist li, .eps-list li, .episode-list li, .ep-list a, .gmr-listseries a, .hal-episodes li a, a")
                val episodes = epElements.mapNotNull { el ->
                    val a = if (el.tagName() == "a") el else el.selectFirst("a") ?: return@mapNotNull null
                    val href = a.absUrl("href").ifBlank { return@mapNotNull null }
                    val rawNum = el.selectFirst(".num, .ep-num, span:first-child")?.text()?.filter { it.isDigit() }
                    val epNum = rawNum?.toIntOrNull() ?: href.filter { it.isDigit() }.takeLast(3).toIntOrNull() ?: 1
                    val epTitle = el.selectFirst(".num, .ep-num, span:first-child")?.text() ?: a.text()
                    DramaEpisode(
                        episodeNumber = epNum,
                        url = href,
                        title = epTitle.ifBlank { "Episode $epNum" }
                    )
                }.distinctBy { it.url }

                if (episodes.isNotEmpty()) {
                    seasons.add(DramaSeason(seasonNumber = sNum, title = sTitle, episodes = episodes))
                }
            }
        }

        // 2. If no season tabs found, check for flat episode lists
        if (seasons.isEmpty()) {
            val epElements = doc.select(
                ".episode-list li, .eps-list li, .episodelist li, .ep-list a, .gmr-listseries a, .hal-episodes li a, .episodes a, li[data-video-source]"
            )
            val episodes = epElements.mapNotNull { el ->
                val a = if (el.tagName() == "a") el else el.selectFirst("a")
                val href = a?.absUrl("href") ?: el.attr("data-video-source").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val rawNum = el.selectFirst(".num, .ep-num, span:first-child, .fwduvp-thumbnail-title")?.text()?.filter { it.isDigit() }
                val epNum = rawNum?.toIntOrNull() ?: 1
                val rawTitle = el.selectFirst(".num, .ep-num, span:first-child, .fwduvp-thumbnail-title")?.text() ?: a?.text() ?: ""
                val title = if (rawTitle.lowercase().contains("episode") || rawTitle.lowercase().contains("ep")) rawTitle else if (rawTitle.isNotBlank()) "Episode $rawTitle" else "Episode $epNum"
                DramaEpisode(
                    episodeNumber = epNum,
                    url = href,
                    title = title
                )
            }.distinctBy { it.url }

            if (episodes.isNotEmpty()) {
                seasons.add(DramaSeason(seasonNumber = 1, title = "Season 1", episodes = episodes))
            }
        }

        return seasons
    }

    // Untuk Drama, chapters = episodes
    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        try {
            val seasons = fetchSeasonsAndEpisodes(itemId)
            val isMultiSeason = seasons.size > 1

            val chapters = mutableListOf<Chapter>()
            for (season in seasons) {
                for (ep in season.episodes) {
                    val displayTitle = if (isMultiSeason) {
                        "S${season.seasonNumber} ${ep.title}"
                    } else {
                        ep.title
                    }
                    chapters.add(Chapter(id = ep.url, title = displayTitle, url = ep.url, date = ep.date))
                }
            }

            if (chapters.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(chapters)
            } else {
                // Check if it is a single movie page
                val doc = fetchDocument(itemId)
                val hasVideo = doc.selectFirst("iframe, .gmr-watch-btn, .cmn-btn, video, .player, #embed_holder, li[data-video-source]") != null
                if (hasVideo) {
                    SourceStatusBus.clear(sourceId)
                    emit(listOf(Chapter(id = itemId, title = "Tonton Film", url = itemId)))
                } else {
                    throw java.io.IOException("Tidak ditemukan episode atau pemutar film aktif pada $sourceName.")
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }

    open suspend fun fetchVideoStream(episodeUrl: String): Result<VideoStreamInfo> {
        return runCatching {
            val customHeaders = this@BaseDramaSource.requestHeaders.toMutableMap()
            if (!customHeaders.containsKey("Referer")) {
                customHeaders["Referer"] = episodeUrl
            }
            if (!customHeaders.containsKey("User-Agent")) {
                customHeaders["User-Agent"] = com.example.data.NetworkModule.userAgent
            }

            // 1. Direct Interception pada episodeUrl via headless network sniffer (bypasses fake play overlays & redirect chains)
            val directInterception = videoUrlInterceptor.interceptVideo(episodeUrl, episodeUrl, timeoutMs = 15000L, headers = customHeaders)
            if (directInterception != null && directInterception.qualities.isNotEmpty()) {
                return@runCatching directInterception
            }

            // Direct stream resolution via network sniffer
            val directStreamUrl = videoUrlInterceptor.interceptStreamUrl(episodeUrl, timeoutMs = 12000L, headers = customHeaders)
            if (!directStreamUrl.isNullOrBlank()) {
                return@runCatching VideoStreamInfo(
                    episodeId = episodeUrl,
                    embedUrl = directStreamUrl,
                    qualities = listOf(com.example.data.sources.VideoQuality("Auto", directStreamUrl, isM3U8 = directStreamUrl.contains(".m3u8"), headers = customHeaders)),
                    headers = customHeaders
                )
            }

            val doc = fetchDocument(episodeUrl)
            val iframes = doc.select("iframe, .video-embed iframe, .gmr-embed-responsive iframe, .player iframe, #player iframe, #embed_holder iframe")
            val embedUrls = iframes.map { it.absUrl("src").ifEmpty { it.absUrl("data-src") } }
                .filter { it.isNotEmpty() }

            // 2. Sniff discovered iframe embeds
            for (src in embedUrls) {
                val iframeInfo = videoUrlInterceptor.interceptVideo(src, episodeUrl, timeoutMs = 12000L, headers = customHeaders)
                if (iframeInfo != null && iframeInfo.qualities.isNotEmpty()) {
                    return@runCatching iframeInfo
                }
                val streamUrl = videoUrlInterceptor.interceptStreamUrl(src, timeoutMs = 10000L, headers = customHeaders)
                if (!streamUrl.isNullOrBlank()) {
                    return@runCatching VideoStreamInfo(
                        episodeId = episodeUrl,
                        embedUrl = streamUrl,
                        qualities = listOf(com.example.data.sources.VideoQuality("Auto", streamUrl, isM3U8 = streamUrl.contains(".m3u8"), headers = customHeaders)),
                        headers = customHeaders
                    )
                }
            }

            // Direct video tag check
            val videoTag = doc.selectFirst("video source[src], video[src], li[data-video-source]")
            val directSrc = videoTag?.absUrl("src")?.ifEmpty { videoTag.attr("data-video-source") } ?: ""
            if (directSrc.isNotBlank()) {
                return@runCatching VideoStreamInfo(
                    episodeId = episodeUrl,
                    embedUrl = directSrc,
                    qualities = listOf(com.example.data.sources.VideoQuality("Auto", directSrc, isM3U8 = directSrc.contains(".m3u8"), headers = customHeaders)),
                    headers = customHeaders
                )
            }

            // Fallback
            if (embedUrls.isNotEmpty()) {
                VideoStreamInfo(episodeId = episodeUrl, embedUrl = embedUrls.first(), headers = customHeaders)
            } else {
                throw java.io.IOException("Tidak ditemukan tautan video/embed stream aktif pada sumber $sourceName.")
            }
        }
    }

    override fun getVideoStream(episodeId: String): Flow<VideoStreamInfo> = flow {
        try {
            val result = fetchVideoStream(episodeId)
            val streamInfo = result.getOrThrow()
            SourceStatusBus.clear(sourceId)
            emit(streamInfo)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }
}

data class DramaSeason(
    val seasonNumber: Int,
    val title: String = "Season $seasonNumber",
    val episodes: List<DramaEpisode>
)

data class DramaEpisode(
    val episodeNumber: Int,
    val url: String,
    val title: String = "Episode $episodeNumber",
    val date: String = ""
)
