package com.example.network.resolver

import android.content.Context
import com.example.data.sources.VideoExtractorEngine
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.base.detectProviderName
import com.example.data.sources.base.extractQualityLabel
import com.example.data.sources.base.isBase64Encoded
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance Multi-Server Embed & Direct Stream Resolver for Anime and Drama.
 * Complies with Proposal 4 Technical Specification:
 * - Multi-server extraction (Streamwish, FileLions, VidHide, DoodStream, Mp4Upload, etc.)
 * - Reliability-based ranking and health tracking
 * - Silent Background Switch & Auto-Resolution Multi-Server Failover
 */
class AnimeMultiServerResolver(private val context: Context? = null) {

    private val extractorEngine by lazy {
        context?.let { VideoExtractorEngine(it) } ?: VideoExtractorEngine(com.example.OtakuHubApp.instance)
    }

    private val failedServerUrls = ConcurrentHashMap.newKeySet<String>()

    /**
     * Provider reliability score (higher = more stable/faster response).
     */
    private val providerReliability = mapOf(
        "Streamwish" to 95,
        "VidHide" to 92,
        "FileLions" to 90,
        "Mp4Upload" to 88,
        "DoodStream" to 85,
        "Streamtape" to 80,
        "Mixdrop" to 78,
        "Gofile" to 85,
        "Pixeldrain" to 84,
        "Blogger" to 90,
        "GoogleDrive" to 86,
        "Direct" to 98
    )

    /**
     * Extracts and ranks available server options from raw HTML source page.
     */
    suspend fun extractServersFromHtml(html: String, pageUrl: String = ""): List<VideoServer> = withContext(Dispatchers.Default) {
        val servers = mutableListOf<VideoServer>()
        val seenUrls = mutableSetOf<String>()

        try {
            val doc = Jsoup.parse(html, pageUrl)

            // 1. Parse select/option server pickers
            val options = doc.select("select#server option, select.server option, select[name*='server'] option, .mirror option, #mirror option")
            for (opt in options) {
                var rawValue = opt.attr("value").trim()
                if (rawValue.isBlank() || rawValue == "0" || rawValue == "#") continue

                // Check Base64 encoded payload
                if (rawValue.isBase64Encoded()) {
                    try {
                        val decoded = String(Base64.decode(rawValue, Base64.DEFAULT)).trim()
                        if (decoded.contains("http") || decoded.contains("<iframe")) {
                            rawValue = if (decoded.contains("<iframe")) {
                                Jsoup.parse(decoded).selectFirst("iframe")?.attr("src") ?: decoded
                            } else {
                                decoded
                            }
                        }
                    } catch (_: Exception) {}
                }

                val optionText = opt.text().trim()
                val provider = optionText.detectProviderName().ifBlank { rawValue.detectProviderName() }
                val quality = optionText.extractQualityLabel()

                if (rawValue.isNotBlank() && seenUrls.add(rawValue)) {
                    servers.add(
                        VideoServer(
                            name = if (optionText.isNotBlank()) optionText else "$provider $quality",
                            embedUrl = rawValue,
                            qualities = if (rawValue.endsWith(".mp4") || rawValue.endsWith(".m3u8")) {
                                listOf(VideoQuality(quality, rawValue))
                            } else emptyList()
                        )
                    )
                }
            }

            // 2. Parse anchor & list elements (e.g. .mirror-stream, .server-list, .east_player)
            val elements = doc.select(
                ".mirror-stream a, .server-list li, .servers-list li, .server-item a, " +
                ".east_player_option, [data-content], [data-post], [data-embed], [data-url], .server-button"
            )
            for (el in elements) {
                var rawUrl = el.attr("data-content").ifBlank {
                    el.attr("data-post").ifBlank {
                        el.attr("data-embed").ifBlank {
                            el.attr("data-url").ifBlank {
                                el.attr("href")
                            }
                        }
                    }
                }.trim()

                if (rawUrl.isBlank() || rawUrl == "#" || rawUrl.startsWith("javascript:")) continue

                if (rawUrl.isBase64Encoded()) {
                    try {
                        val decoded = String(Base64.decode(rawUrl, Base64.DEFAULT)).trim()
                        if (decoded.contains("http")) {
                            rawUrl = decoded
                        }
                    } catch (_: Exception) {}
                }

                val label = el.text().trim()
                val provider = label.detectProviderName().ifBlank { rawUrl.detectProviderName() }
                val quality = label.extractQualityLabel()

                if (rawUrl.isNotBlank() && seenUrls.add(rawUrl)) {
                    servers.add(
                        VideoServer(
                            name = if (label.isNotBlank()) label else "$provider $quality",
                            embedUrl = rawUrl,
                            qualities = if (rawUrl.endsWith(".mp4") || rawUrl.endsWith(".m3u8")) {
                                listOf(VideoQuality(quality, rawUrl))
                            } else emptyList()
                        )
                    )
                }
            }

            // 3. Fallback: Parse direct iframe embed
            val iframes = doc.select("iframe[src*='http'], iframe[data-src*='http']")
            for (iframe in iframes) {
                val src = iframe.absUrl("src").ifBlank { iframe.absUrl("data-src") }.trim()
                if (src.isNotBlank() && !src.contains("googleads") && !src.contains("facebook") && seenUrls.add(src)) {
                    val provider = src.detectProviderName()
                    servers.add(
                        VideoServer(
                            name = "$provider Embed",
                            embedUrl = src
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        sortServersByReliability(servers)
    }

    /**
     * Sorts servers based on historical provider stability, resolution quality, and error history.
     */
    fun sortServersByReliability(servers: List<VideoServer>): List<VideoServer> {
        return servers.sortedWith(
            compareByDescending<VideoServer> { server ->
                // Higher score for known stable providers
                val providerFromUrl = server.embedUrl.detectProviderName()
                val providerFromName = server.name.detectProviderName()
                val provider = if (providerFromUrl != "Server" && providerFromUrl.isNotBlank()) {
                    providerFromUrl
                } else {
                    providerFromName
                }
                providerReliability[provider] ?: 70
            }.thenByDescending { server ->
                // Higher score if direct MP4/HLS streams already extracted
                if (server.qualities.isNotEmpty()) 100 else 50
            }.thenBy { server ->
                // Deprioritize servers that previously failed
                if (failedServerUrls.contains(server.embedUrl)) 1 else 0
            }
        )
    }

    /**
     * Selects the next viable mirror server when current server fails.
     * Complies with formulation: NextServer = AvailableServers[i + 1]
     */
    fun getNextAvailableServer(
        currentServer: VideoServer?,
        availableServers: List<VideoServer>,
        failedUrl: String = ""
    ): VideoServer? {
        if (failedUrl.isNotBlank()) {
            failedServerUrls.add(failedUrl)
        }
        if (currentServer != null) {
            failedServerUrls.add(currentServer.embedUrl)
            currentServer.qualities.forEach { failedServerUrls.add(it.url) }
        }

        val candidates = availableServers.filter { server ->
            server != currentServer &&
            !failedServerUrls.contains(server.embedUrl) &&
            server.qualities.none { failedServerUrls.contains(it.url) }
        }

        return sortServersByReliability(candidates).firstOrNull()
            ?: availableServers.firstOrNull { it != currentServer }
    }

    /**
     * Resolves direct playable streams for the given server using VideoExtractorEngine.
     */
    suspend fun resolveServerStream(server: VideoServer, headers: Map<String, String> = emptyMap()): VideoServer = withContext(Dispatchers.IO) {
        if (server.qualities.isNotEmpty()) return@withContext server
        if (server.embedUrl.isBlank()) return@withContext server

        try {
            val extracted = extractorEngine.extractVideo(server.embedUrl, customHeaders = headers)
            if (extracted != null && (extracted.qualities.isNotEmpty() || extracted.subtitles.isNotEmpty())) {
                val effectiveHeaders = if (extracted.headers.isNotEmpty()) extracted.headers else headers
                return@withContext server.copy(
                    qualities = extracted.qualities,
                    subtitles = extracted.subtitles,
                    headers = effectiveHeaders,
                    drmLicenseUrl = extracted.drmLicenseUrl,
                    drmHeaders = extracted.drmHeaders
                )
            }
        } catch (_: Exception) {}

        server
    }

    /**
     * Clears cached failure history.
     */
    fun resetFailures() {
        failedServerUrls.clear()
    }
}
