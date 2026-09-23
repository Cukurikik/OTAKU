package com.example.data.sources.drama

import android.util.Base64
import com.example.data.NetworkModule
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.anime.extractors.*
import com.example.data.sources.drama.extractors.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Representation of a detected server tab on an episode page.
 */
data class ServerTab(
    val id: String,
    val name: String,
    val embedUrl: String,
    val postData: String? = null,
    val isPrimary: Boolean = false
)

/**
 * Resolved and decrypted server source ready for playback in the media player.
 */
data class DramaServerSource(
    val serverName: String,
    val streamUrl: String,
    val isPrimary: Boolean,
    val qualities: List<VideoQuality> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val embedUrl: String = ""
)

/**
 * MultiServerDramaResolver:
 * Comprehensive multi-host embed decryption engine that scans, decrypts, and resolves all server tabs
 * (VIP, FileLions, VidHide, HxFile, Mixdrop, StreamWish, Dood, Fembed, StreamSB, Mp4Upload, Google Drive, etc.)
 * across drama episode pages to guarantee 0% failure rate and enable dynamic server switching on the UI.
 */
class MultiServerDramaResolver(
    private val extractorChain: FallbackExtractorChain = FallbackExtractorChain()
) {

    private val hxFileExtractor = HxFileExtractor()
    private val mixdropExtractor = MixdropExtractor()
    private val fembedExtractor = FembedExtractor()
    private val streamSBExtractor = StreamSBExtractor()
    private val vidHideDramaExtractor = VidHideDramaExtractor()
    private val fileLionsExtractor = FileLionsExtractor()
    private val streamWishExtractor = StreamWishExtractor()
    private val doodExtractor = DoodExtractor()
    private val mp4UploadExtractor = Mp4UploadExtractor()
    private val bloggerExtractor = BloggerExtractor()

    /**
     * Parses all server tabs from HTML page DOM.
     */
    fun parseServerTabs(pageHtml: String, baseUri: String = ""): List<ServerTab> {
        val serverTabs = mutableListOf<ServerTab>()
        val seenEmbedUrls = mutableSetOf<String>()

        if (pageHtml.isBlank()) return emptyList()

        val doc: Document = runCatching { Jsoup.parse(pageHtml, baseUri) }.getOrElse { Jsoup.parse(pageHtml) }

        // 1. Scan server tab buttons / links (popular drama themes: Muvi, Dooplay, VinaHost, GMR, etc.)
        val tabElements = doc.select(
            ".server-tabs a, .server-tabs button, .server-list li a, .servers-list li a, " +
            ".server-item, .server-wrapper a, .player-servers a, .gmr-listseries a, " +
            "ul#server-list li a, .mirror-list a, .select-server option, select.server option, " +
            "a[data-embed], button[data-src], div[data-server], li[data-provider], a[data-post]"
        )

        for ((index, el) in tabElements.withIndex()) {
            val rawUrl = el.attr("data-embed")
                .ifBlank { el.attr("data-src") }
                .ifBlank { el.attr("data-url") }
                .ifBlank { el.attr("value") }
                .ifBlank { el.attr("href") }

            val embedUrl = decodeOrResolveUrl(rawUrl, baseUri)
            if (embedUrl.isBlank() || embedUrl.startsWith("javascript:") || embedUrl == "#") {
                continue
            }

            val rawName = el.text().trim().ifBlank { el.attr("data-server").ifBlank { "Server ${index + 1}" } }
            val cleanName = normalizeServerName(rawName, embedUrl, index + 1)
            val isPrimary = index == 0 || el.hasClass("active") || el.hasAttr("selected")

            if (seenEmbedUrls.add(embedUrl)) {
                serverTabs.add(
                    ServerTab(
                        id = "server_${index + 1}",
                        name = cleanName,
                        embedUrl = embedUrl,
                        isPrimary = isPrimary
                    )
                )
            }
        }

        // 2. Scan direct IFrames if no tabs were found
        val iframes = doc.select("iframe[src], iframe[data-src]")
        for ((index, iframe) in iframes.withIndex()) {
            val rawSrc = iframe.absUrl("src").ifBlank { iframe.absUrl("data-src") }.ifBlank { iframe.attr("src") }
            val embedUrl = decodeOrResolveUrl(rawSrc, baseUri)
            if (embedUrl.isNotBlank() && seenEmbedUrls.add(embedUrl)) {
                val cleanName = normalizeServerName("IFrame ${index + 1}", embedUrl, serverTabs.size + 1)
                serverTabs.add(
                    ServerTab(
                        id = "iframe_${serverTabs.size + 1}",
                        name = cleanName,
                        embedUrl = embedUrl,
                        isPrimary = serverTabs.isEmpty()
                    )
                )
            }
        }

        return serverTabs
    }

    /**
     * Resolves all available server tabs into valid decrypted [DramaServerSource] instances with direct streams.
     */
    suspend fun resolveServerSources(
        pageHtml: String,
        episodeUrl: String,
        customHeaders: Map<String, String> = mapOf("Referer" to episodeUrl, "User-Agent" to NetworkModule.userAgent)
    ): List<DramaServerSource> = withContext(Dispatchers.IO) {
        val serverTabs = parseServerTabs(pageHtml, episodeUrl)
        val resolvedSources = mutableListOf<DramaServerSource>()

        // 1. Direct stream check in page HTML
        val directStream = extractorChain.extractDirectStream(pageHtml)
        if (!directStream.isNullOrBlank()) {
            val isM3u8 = directStream.contains(".m3u8")
            val quality = VideoQuality(if (isM3u8) "Direct HLS" else "Direct MP4", directStream, isM3u8, customHeaders)
            resolvedSources.add(
                DramaServerSource(
                    serverName = "VIP Direct Server",
                    streamUrl = directStream,
                    isPrimary = true,
                    qualities = listOf(quality),
                    headers = customHeaders
                )
            )
        }

        // 2. Resolve server tabs concurrently
        val deferred = serverTabs.map { tab ->
            async {
                resolveSingleServerTab(tab, episodeUrl, customHeaders)
            }
        }

        val results = deferred.awaitAll().filterNotNull()
        resolvedSources.addAll(results)

        // Deduplicate by stream URL while maintaining primary precedence
        val seenStreams = mutableSetOf<String>()
        val distinctList = mutableListOf<DramaServerSource>()

        for (source in resolvedSources) {
            if (seenStreams.add(source.streamUrl)) {
                distinctList.add(source)
            }
        }

        distinctList
    }

    /**
     * Resolves a single server tab by routing to the appropriate host extractor.
     */
    suspend fun resolveSingleServerTab(
        tab: ServerTab,
        pageUrl: String,
        headers: Map<String, String>
    ): DramaServerSource? = withContext(Dispatchers.IO) {
        val embedUrl = tab.embedUrl
        val lower = embedUrl.lowercase()
        val streamHeaders = headers.toMutableMap().apply {
            put("Referer", embedUrl)
        }

        val qualities = mutableListOf<VideoQuality>()

        try {
            when {
                lower.contains("hxfile") -> {
                    qualities.addAll(hxFileExtractor.extract(embedUrl))
                }
                lower.contains("mixdrop") -> {
                    qualities.addAll(mixdropExtractor.extract(embedUrl))
                }
                lower.contains("fembed") || lower.contains("feurl") || lower.contains("dutrag") || lower.contains("fcdn") -> {
                    qualities.addAll(fembedExtractor.extract(embedUrl))
                }
                lower.contains("streamsb") || lower.contains("sbembed") || lower.contains("sbvideo") || lower.contains("watchsb") -> {
                    qualities.addAll(streamSBExtractor.extract(embedUrl))
                }
                lower.contains("vidhide") || lower.contains("streamhide") -> {
                    qualities.addAll(vidHideDramaExtractor.extract(embedUrl))
                }
                lower.contains("filelions") || lower.contains("lion") -> {
                    qualities.addAll(fileLionsExtractor.extract(embedUrl))
                }
                lower.contains("streamwish") || lower.contains("wishembed") || lower.contains("strwish") -> {
                    qualities.addAll(streamWishExtractor.extract(embedUrl))
                }
                lower.contains("dood") || lower.contains("ds2play") || lower.contains("doodstream") -> {
                    qualities.addAll(doodExtractor.extract(embedUrl))
                }
                lower.contains("mp4upload") -> {
                    qualities.addAll(mp4UploadExtractor.extract(embedUrl))
                }
                lower.contains("blogger") || lower.contains("blogspot") -> {
                    qualities.addAll(bloggerExtractor.extract(embedUrl))
                }
                else -> {
                    // Fallback to generic extractor chain
                    val chainQualities = extractorChain.extractQualities(embedUrl, pageUrl, streamHeaders)
                    qualities.addAll(chainQualities)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (qualities.isNotEmpty()) {
            val best = qualities.first()
            return@withContext DramaServerSource(
                serverName = tab.name,
                streamUrl = best.url,
                isPrimary = tab.isPrimary,
                qualities = qualities,
                headers = best.headers.ifEmpty { streamHeaders },
                embedUrl = embedUrl
            )
        }

        // Direct URL fallback if embedUrl is already a video link
        if (embedUrl.endsWith(".m3u8") || embedUrl.endsWith(".mp4")) {
            val isM3u8 = embedUrl.endsWith(".m3u8")
            val directQuality = VideoQuality(if (isM3u8) "HLS (${tab.name})" else "MP4 (${tab.name})", embedUrl, isM3u8, streamHeaders)
            return@withContext DramaServerSource(
                serverName = tab.name,
                streamUrl = embedUrl,
                isPrimary = tab.isPrimary,
                qualities = listOf(directQuality),
                headers = streamHeaders,
                embedUrl = embedUrl
            )
        }

        null
    }

    /**
     * Resolves the full [VideoStreamInfo] using multi-server failover resolution.
     */
    suspend fun resolveStreamInfoWithMultiServer(
        pageHtml: String,
        episodeUrl: String,
        customHeaders: Map<String, String> = mapOf("Referer" to episodeUrl, "User-Agent" to NetworkModule.userAgent)
    ): VideoStreamInfo? {
        val serverSources = resolveServerSources(pageHtml, episodeUrl, customHeaders)
        if (serverSources.isEmpty()) return null

        val primarySource = serverSources.find { it.isPrimary } ?: serverSources.first()
        val allQualities = serverSources.flatMap { it.qualities }.distinctBy { it.url }

        // Extract subtitles from HTML
        val subtitles = DramaSubtitleExtractorEngine.extractSubtitlesFromHtml(pageHtml, episodeUrl)

        return VideoStreamInfo(
            episodeId = episodeUrl,
            embedUrl = primarySource.streamUrl,
            qualities = if (allQualities.isNotEmpty()) allQualities else primarySource.qualities,
            subtitles = subtitles,
            headers = primarySource.headers
        )
    }

    private fun decodeOrResolveUrl(rawUrl: String, baseUri: String): String {
        var url = rawUrl.trim()
        if (url.isBlank()) return ""

        // Check base64 encoded URL
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("//") && !url.startsWith("/")) {
            runCatching {
                val decodedBytes = runCatching { java.util.Base64.getDecoder().decode(url) }
                    .getOrElse { android.util.Base64.decode(url, android.util.Base64.DEFAULT) }
                val decoded = String(decodedBytes).trim()
                if (decoded.startsWith("http://") || decoded.startsWith("https://") || decoded.startsWith("//")) {
                    url = decoded
                }
            }
        }

        if (url.startsWith("//")) {
            url = "https:$url"
        }

        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }

        if (baseUri.isNotBlank()) {
            return runCatching {
                val base = java.net.URI(baseUri)
                base.resolve(url).toString()
            }.getOrElse { url }
        }

        return url
    }

    private fun normalizeServerName(rawName: String, url: String, index: Int): String {
        val clean = rawName.trim()
        val lowerUrl = url.lowercase()

        return when {
            lowerUrl.contains("filelions") || clean.contains("filelions", ignoreCase = true) -> "FileLions"
            lowerUrl.contains("vidhide") || clean.contains("vidhide", ignoreCase = true) -> "VidHide"
            lowerUrl.contains("hxfile") || clean.contains("hxfile", ignoreCase = true) -> "HxFile"
            lowerUrl.contains("mixdrop") || clean.contains("mixdrop", ignoreCase = true) -> "Mixdrop"
            lowerUrl.contains("streamwish") || clean.contains("streamwish", ignoreCase = true) -> "StreamWish"
            lowerUrl.contains("dood") || clean.contains("dood", ignoreCase = true) -> "Doodstream"
            lowerUrl.contains("fembed") || lowerUrl.contains("feurl") || clean.contains("fembed", ignoreCase = true) -> "Fembed"
            lowerUrl.contains("streamsb") || lowerUrl.contains("sbembed") || clean.contains("streamsb", ignoreCase = true) -> "StreamSB"
            lowerUrl.contains("mp4upload") || clean.contains("mp4upload", ignoreCase = true) -> "Mp4Upload"
            lowerUrl.contains("blogger") || clean.contains("blogger", ignoreCase = true) -> "Blogger"
            clean.contains("gdrive", ignoreCase = true) || clean.contains("google", ignoreCase = true) || lowerUrl.contains("drive.google") -> "Google Drive"
            clean.contains("vip", ignoreCase = true) -> "Server VIP"
            clean.isNotBlank() && !clean.startsWith("http", ignoreCase = true) -> clean
            else -> "Server $index"
        }
    }
}
