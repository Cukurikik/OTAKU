package com.example.data.sources.anime.base

import android.content.Context
import android.util.Base64
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.base.detectProviderName
import com.example.data.sources.base.extractQualityLabel
import com.example.data.sources.base.isBase64Encoded
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jsoup.Jsoup

/**
 * Scraper Base khusus untuk Anime bertema OtakuPress / Custom Theme:
 * Menangani situs: Anime154 (154.203.167.17), NontonAnimeID, Otakupoi.
 * Pola DOM: `div.mirror-stream`, `data-content` Base64 decoding, `#player-embed`.
 */
open class OtakuPressThemeScraper(
    context: Context,
    override val sourceId: String,
    override val sourceName: String,
    override val baseUrl: String
) : AnimeStreamThemeScraper(context, sourceId, sourceName, baseUrl) {

    override val cardSelector = ".item, .box, article, .animepost, .bs, .content-item"
    override val episodeSelector = ".list-eps li, .episodelist li, .eps-list a, .mirror-stream a, .eplister li"

    override fun getVideoStream(episodeId: String): Flow<VideoStreamInfo> = flow {
        try {
            val customHeaders = this@OtakuPressThemeScraper.requestHeaders.toMutableMap()
            if (!customHeaders.containsKey("Referer")) {
                customHeaders["Referer"] = episodeId
            }

            // 1. Direct Interception
            val directInterception = videoUrlInterceptor.interceptVideo(episodeId, episodeId, timeoutMs = 15000L, headers = customHeaders)
            if (directInterception != null && directInterception.qualities.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(directInterception)
                return@flow
            }

            // 2. Multi-Server Extraction
            val doc = fetchDocument(episodeId)
            val pageHtml = doc.html()
            val multiStreams = extractMultiServerStreams(pageHtml, episodeId)
            if (multiStreams.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                val allQualities = multiStreams.map {
                    VideoQuality(it.quality, it.url, it.url.contains(".m3u8"), customHeaders)
                }.distinctBy { it.url }

                val allServers = multiStreams.map {
                    VideoServer(
                        name = it.provider.ifBlank { it.quality },
                        embedUrl = it.embedUrl.ifBlank { it.url },
                        qualities = listOf(VideoQuality(it.quality, it.url, it.url.contains(".m3u8"), customHeaders)),
                        headers = customHeaders
                    )
                }.distinctBy { it.embedUrl }

                emit(
                    VideoStreamInfo(
                        episodeId = episodeId,
                        embedUrl = multiStreams.first().url,
                        qualities = allQualities,
                        servers = allServers,
                        headers = customHeaders
                    )
                )
                return@flow
            }

            // 3. OtakuPress specific mirrors & base64 resolution
            val embedUrls = mutableListOf<Pair<String, String>>() // (serverName, url)

            // Extract div.mirror-stream, .player-embed, #player-embed
            doc.select(".mirror-stream a, .mirror-stream li, .server-item, .player-servers li").forEach { el ->
                val serverName = el.text().trim()
                val rawVal = el.attr("data-content")
                    .ifBlank { el.attr("data-post") }
                    .ifBlank { el.attr("data-video") }
                    .ifBlank { el.attr("href") }

                if (rawVal.isBase64Encoded()) {
                    runCatching {
                        val decoded = String(Base64.decode(rawVal, Base64.DEFAULT))
                        val iframeSrc = Jsoup.parseBodyFragment(decoded).selectFirst("iframe")?.attr("src")
                        val target = iframeSrc ?: if (decoded.startsWith("http")) decoded else null
                        if (!target.isNullOrBlank()) {
                            embedUrls.add(serverName to target)
                        }
                    }
                } else if (rawVal.startsWith("http")) {
                    embedUrls.add(serverName to rawVal)
                }
            }

            doc.select("iframe#embed-player, div.player-embed iframe, #pembed iframe, .responsive-embed iframe").forEach { iframe ->
                val src = iframe.absUrl("src").ifBlank { iframe.absUrl("data-src") }
                if (src.isNotBlank() && !src.contains("disqus") && !src.contains("facebook")) {
                    embedUrls.add("Main Player" to src)
                }
            }

            val resolvedQualities = mutableListOf<VideoQuality>()
            val resolvedServers = mutableListOf<VideoServer>()

            for ((serverName, embedUrl) in embedUrls.distinctBy { it.second }) {
                val engineExtracted = runCatching { videoExtractorEngine.extractVideo(embedUrl, episodeId, customHeaders = customHeaders) }.getOrNull()
                if (engineExtracted != null && engineExtracted.qualities.isNotEmpty()) {
                    resolvedQualities.addAll(engineExtracted.qualities)
                    resolvedServers.add(
                        VideoServer(
                            name = serverName.ifBlank { embedUrl.detectProviderName() },
                            embedUrl = embedUrl,
                            qualities = engineExtracted.qualities,
                            headers = customHeaders
                        )
                    )
                    continue
                }

                val streamUrl = videoUrlInterceptor.interceptStreamUrl(embedUrl, timeoutMs = 8000L, headers = customHeaders)
                if (!streamUrl.isNullOrBlank()) {
                    val qLabel = serverName.extractQualityLabel()
                    val q = VideoQuality(qLabel, streamUrl, streamUrl.contains(".m3u8"), customHeaders)
                    resolvedQualities.add(q)
                    resolvedServers.add(
                        VideoServer(
                            name = serverName.ifBlank { embedUrl.detectProviderName() },
                            embedUrl = embedUrl,
                            qualities = listOf(q),
                            headers = customHeaders
                        )
                    )
                }
            }

            if (resolvedQualities.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(
                    VideoStreamInfo(
                        episodeId = episodeId,
                        embedUrl = resolvedServers.firstOrNull()?.embedUrl ?: resolvedQualities.first().url,
                        qualities = resolvedQualities.distinctBy { it.url },
                        servers = resolvedServers,
                        headers = customHeaders
                    )
                )
                return@flow
            }

            super.getVideoStream(episodeId).collect { emit(it) }
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }
}
