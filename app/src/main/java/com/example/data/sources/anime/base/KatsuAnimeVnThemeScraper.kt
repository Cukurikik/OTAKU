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
import java.net.URLEncoder

/**
 * Scraper Base khusus untuk Anime bertema Katsu / AnimeVn:
 * Menangani situs: Nimegami, Stucknime, Ylnime, Wibuku.
 * Pola DOM: `select#server option`, `div.player-embed`, `div#embed_holder`.
 */
open class KatsuAnimeVnThemeScraper(
    context: Context,
    override val sourceId: String,
    override val sourceName: String,
    override val baseUrl: String,
    protected val customPageFormat: String = "page/%d/"
) : AnimeStreamThemeScraper(context, sourceId, sourceName, baseUrl) {

    override fun getPopularUrl(page: Int): String {
        val base = currentBaseUrl().trimEnd('/')
        return when {
            page <= 1 -> base
            customPageFormat.contains("?") -> "$base/${String.format(customPageFormat, page)}"
            else -> "$base/${String.format(customPageFormat, page)}"
        }
    }

    override fun getVideoStream(episodeId: String): Flow<VideoStreamInfo> = flow {
        try {
            val customHeaders = this@KatsuAnimeVnThemeScraper.requestHeaders.toMutableMap()
            if (!customHeaders.containsKey("Referer")) {
                customHeaders["Referer"] = episodeId
            }

            // 1. Sniff episode page langsung
            val directInterception = videoUrlInterceptor.interceptVideo(episodeId, episodeId, timeoutMs = 15000L, headers = customHeaders)
            if (directInterception != null && directInterception.qualities.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(directInterception)
                return@flow
            }

            // 2. Fetch page HTML & Ekstraksi server dropdown select#server
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

            // 3. Ekstraksi spesifik Katsu: select#server option, div.player-embed iframe, div#embed_holder
            val embedUrls = mutableListOf<Pair<String, String>>() // (serverName, url)
            doc.select("select#server option, select#changeServer option, select.mirror option").forEach { opt ->
                val serverName = opt.text().trim()
                val rawVal = opt.attr("value").ifBlank { opt.attr("data-post") }.ifBlank { opt.attr("data-content") }
                val embedUrl = if (rawVal.isBase64Encoded()) {
                    runCatching {
                        val decoded = String(Base64.decode(rawVal, Base64.DEFAULT))
                        val iframeSrc = Jsoup.parseBodyFragment(decoded).selectFirst("iframe")?.attr("src")
                        iframeSrc ?: decoded
                    }.getOrDefault(rawVal)
                } else {
                    rawVal
                }
                if (embedUrl.isNotBlank() && !embedUrl.startsWith("#")) {
                    embedUrls.add(serverName to embedUrl)
                }
            }

            doc.select("div.player-embed iframe, div#embed_holder iframe, .player-embed iframe").forEach { iframe ->
                val src = iframe.absUrl("src").ifBlank { iframe.absUrl("data-src") }
                if (src.isNotBlank()) {
                    embedUrls.add("Default Server" to src)
                }
            }

            val resolvedQualities = mutableListOf<VideoQuality>()
            val resolvedServers = mutableListOf<VideoServer>()

            for ((serverName, embedUrl) in embedUrls.distinctBy { it.second }) {
                // Check VideoExtractorEngine
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

                // Intercept
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

            // Fallback to super
            super.getVideoStream(episodeId).collect { emit(it) }
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }
}
