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
import androidx.compose.runtime.Immutable
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
    protected val multiServerDramaResolver by lazy { com.example.data.sources.drama.MultiServerDramaResolver() }

    /**
     * Parses multi-season structure and episode list from drama details page using [DramaSeasonResolver].
     * Supports season tabs, dropdowns, season containers, range unrolling, and flat episode lists.
     */
    open suspend fun fetchDramaSeries(dramaUrl: String): com.example.data.sources.drama.DramaSeries {
        val doc = fetchDocument(dramaUrl)
        return com.example.data.sources.drama.DramaSeasonResolver.parseSeasons(doc)
    }

    open suspend fun fetchSeasonsAndEpisodes(dramaUrl: String): List<DramaSeason> {
        val series = fetchDramaSeries(dramaUrl)
        return series.seasons.map { s ->
            DramaSeason(
                seasonNumber = s.seasonNumber,
                title = s.seasonName,
                episodes = s.episodes.map { ep ->
                    DramaEpisode(
                        episodeNumber = ep.episodeNumberInSeason,
                        url = ep.streamUrl.ifBlank { ep.episodeId },
                        title = ep.title,
                        date = ep.date
                    )
                }
            )
        }
    }

    // Untuk Drama, chapters = episodes
    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        try {
            val series = fetchDramaSeries(itemId)
            val chapters = com.example.data.sources.drama.DramaSeasonResolver.toChapters(series)

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
            val docSubtitles = com.example.data.sources.drama.DramaSubtitleExtractorEngine.extractSubtitlesFromHtml(doc.html(), episodeUrl)

            // Multi-Server Embed Decryption & Resolution
            val multiServerInfo = multiServerDramaResolver.resolveStreamInfoWithMultiServer(doc.html(), episodeUrl, customHeaders)
            if (multiServerInfo != null && multiServerInfo.qualities.isNotEmpty()) {
                val mergedSubs = (multiServerInfo.subtitles + docSubtitles).distinctBy { it.url }
                return@runCatching multiServerInfo.copy(subtitles = mergedSubs)
            }

            val iframes = doc.select("iframe, .video-embed iframe, .gmr-embed-responsive iframe, .player iframe, #player iframe, #embed_holder iframe")
            val embedUrls = iframes.map { it.absUrl("src").ifEmpty { it.absUrl("data-src") } }
                .filter { it.isNotEmpty() }

            // 2. Sniff discovered iframe embeds
            for (src in embedUrls) {
                val iframeInfo = videoUrlInterceptor.interceptVideo(src, episodeUrl, timeoutMs = 12000L, headers = customHeaders)
                if (iframeInfo != null && iframeInfo.qualities.isNotEmpty()) {
                    val mergedSubs = (iframeInfo.subtitles + docSubtitles).distinctBy { it.url }
                    return@runCatching iframeInfo.copy(subtitles = mergedSubs)
                }
                val streamUrl = videoUrlInterceptor.interceptStreamUrl(src, timeoutMs = 10000L, headers = customHeaders)
                if (!streamUrl.isNullOrBlank()) {
                    return@runCatching VideoStreamInfo(
                        episodeId = episodeUrl,
                        embedUrl = streamUrl,
                        qualities = listOf(com.example.data.sources.VideoQuality("Auto", streamUrl, isM3U8 = streamUrl.contains(".m3u8"), headers = customHeaders)),
                        subtitles = docSubtitles,
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
                    subtitles = docSubtitles,
                    headers = customHeaders
                )
            }

            // Fallback
            if (embedUrls.isNotEmpty()) {
                VideoStreamInfo(episodeId = episodeUrl, embedUrl = embedUrls.first(), subtitles = docSubtitles, headers = customHeaders)
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

    /**
     * Extracts standardized Drama metadata (Cast list, Subtitle status, Season number) from DOM.
     */
    open fun extractDramaMetadata(document: org.jsoup.nodes.Document, titleFallback: String = ""): DramaMetadata {
        val castList = extractCastList(document)
        val subtitleStatus = extractSubtitleStatus(document, titleFallback)
        val seasonNumber = parseSeasonNumber(titleFallback.ifBlank { document.title() }, document)
        return DramaMetadata(
            castList = castList,
            subtitleStatus = subtitleStatus,
            seasonNumber = seasonNumber
        )
    }

    /**
     * Standardized DOM selector parsing for Drama Cast / Actor list.
     * Supports WordPress drama themes, GMR movie data, schema.org microdata, and OpenGraph metadata.
     */
    open fun extractCastList(document: org.jsoup.nodes.Document): List<String> {
        val castElements = document.select(
            ".cast a, .casts a, .cast-item a, .cast-item, .gmr-moviedata a, [itemprop='actor'], [itemprop='actors'] a, [itemprop='actors'], " +
            ".starring a, .starring, .actors a, .actors, .actor a, .actor, .s-actors a, .info-cast a, .cast-list li, .cast-list a, " +
            ".director-cast a, .f-cast a, .entry-content .cast, .entry-content .casts, .content-cast a, .item-cast a, .gmr-cast a"
        )
        
        val casts = mutableListOf<String>()
        for (el in castElements) {
            val text = el.text().trim()
            if (text.isNotBlank() && text.length in 2..50) {
                val clean = text.replace(Regex("(?i)^(Cast|Casts|Pemeran|Bintang|Actors|Actor|Starring|Stars|Artis|Sutradara|Director)\\s*:\\s*"), "").trim()
                if (clean.isNotBlank() && !clean.contains(":") && !clean.equals("cast", ignoreCase = true) && !clean.equals("actors", ignoreCase = true)) {
                    val parts = if (clean.contains(",")) clean.split(",") else listOf(clean)
                    for (p in parts) {
                        val trimmed = p.trim()
                        if (trimmed.length in 2..40 && !trimmed.matches(Regex("^[0-9]+$"))) {
                            casts.add(trimmed)
                        }
                    }
                }
            }
        }

        if (casts.isEmpty()) {
            val metaCasts = document.select("meta[property='video:actor'], meta[name='actor']")
            for (meta in metaCasts) {
                val content = meta.attr("content").trim()
                if (content.isNotBlank() && !content.equals("video.movie", ignoreCase = true) && !content.equals("video.tv_show", ignoreCase = true)) {
                    casts.add(content)
                }
            }
        }

        if (casts.isEmpty()) {
            val castRows = document.select("tr:has(th:matches((?i)cast|pemeran|bintang|actor)), tr:has(td:matches((?i)cast|pemeran|bintang|actor)), .info-item:matches((?i)cast|pemeran|bintang|actor), p:matches((?i)cast|pemeran|bintang|actor)")
            for (row in castRows) {
                val links = row.select("a")
                if (links.isNotEmpty()) {
                    casts.addAll(links.map { it.text().trim() }.filter { it.length in 2..40 })
                } else {
                    val textAfterColon = row.text().substringAfter(":", "").trim()
                    if (textAfterColon.isNotBlank()) {
                        casts.addAll(textAfterColon.split(",").map { it.trim() }.filter { it.length in 2..40 })
                    }
                }
            }
        }

        return casts.distinct().take(15)
    }

    /**
     * Standardized subtitle status extractor from DOM badges, labels, and title strings.
     */
    open fun extractSubtitleStatus(document: org.jsoup.nodes.Document, titleFallback: String = ""): String {
        val subBadges = document.select(
            ".sub-status, [class*='sub-status'], .sub, .quality, .status-sub, .label-sub, .badge-sub, .gmr-sub, " +
            ".badge-quality, .mep_badge, .gmr-quality-item, .post-status, span.sub, .episode-sub, .ep-sub, span.quality, .sub-title"
        )
        for (badge in subBadges) {
            val text = badge.text().trim()
            val parsed = parseSubtitleStatusFromText(text)
            if (parsed.isNotBlank()) {
                return parsed
            }
        }

        val fullText = "${document.title()} $titleFallback"
        return parseSubtitleStatusFromText(fullText).ifBlank { "Sub Indo" }
    }

    /**
     * Standardized helper to parse subtitle status keyword from raw text.
     */
    open fun parseSubtitleStatusFromText(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("hardsub") -> "Hardsub"
            lower.contains("softsub") -> "Softsub"
            lower.contains("raw") || lower.contains("[raw]") -> "Raw"
            lower.contains("eng sub") || lower.contains("english sub") || lower.contains("engsub") -> "Eng Sub"
            lower.contains("sub indo") || lower.contains("subtitle indonesia") || lower.contains("sub ind") || lower.contains("indonesian") -> "Sub Indo"
            else -> ""
        }
    }

    /**
     * Standardized season number parser from DOM metadata and title strings.
     */
    open fun parseSeasonNumber(title: String, document: org.jsoup.nodes.Document? = null): Int? {
        if (document != null) {
            val metaSeason = document.selectFirst("meta[name='season'], meta[property='video:season']")?.attr("content")?.filter { it.isDigit() }?.toIntOrNull()
            if (metaSeason != null) return metaSeason

            val seasonTag = document.selectFirst(".season, .season-title, .s-title, [data-season], .gmr-season, .hal-season")
            val seasonAttr = seasonTag?.attr("data-season")?.toIntOrNull()
            if (seasonAttr != null) return seasonAttr

            val tagText = seasonTag?.text()?.trim() ?: ""
            val parsedFromTag = extractSeasonFromRegex(tagText)
            if (parsedFromTag != null) return parsedFromTag
        }

        return extractSeasonFromRegex(title)
    }

    private fun extractSeasonFromRegex(text: String): Int? {
        val patterns = listOf(
            Regex("""(?i)Season\s*0*(\d+)"""),
            Regex("""(?i)\bS0*(\d+)\b"""),
            Regex("""(?i)Season-(\d+)"""),
            Regex("""(?i)S-(\d+)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val num = match.groupValues[1].toIntOrNull()
                if (num != null && num in 1..99) return num
            }
        }
        return null
    }
}

@Immutable
data class DramaMetadata(
    val castList: List<String> = emptyList(),
    val subtitleStatus: String = "Sub Indo",
    val seasonNumber: Int? = null
)

@Immutable
data class DramaSeason(
    val seasonNumber: Int,
    val title: String = "Season $seasonNumber",
    val episodes: List<DramaEpisode>
)

@Immutable
data class DramaEpisode(
    val episodeNumber: Int,
    val url: String,
    val title: String = "Episode $episodeNumber",
    val date: String = ""
)
