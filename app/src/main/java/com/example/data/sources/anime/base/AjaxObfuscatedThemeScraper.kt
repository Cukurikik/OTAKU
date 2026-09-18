package com.example.data.sources.anime.base

import android.content.Context
import android.util.Base64
import com.example.data.NetworkModule
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.anime.extractors.JsPackerUnpacker
import com.example.data.sources.base.detectProviderName
import com.example.data.sources.base.extractQualityLabel
import com.example.data.sources.base.isBase64Encoded
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Scraper Base khusus untuk Anime bertema AJAX Obfuscated & JS Packed:
 * Menangani situs: AnimeKuInfo, Miranime, Gomunime, Alqanime, NimeIndo, Subnime.
 * Pola Payload: `action=get_video_url`, `eval(function(p,a,c,k,e,d)...)`, AJAX player admin-ajax.php.
 */
open class AjaxObfuscatedThemeScraper(
    context: Context,
    override val sourceId: String,
    override val sourceName: String,
    override val baseUrl: String
) : AnimeStreamThemeScraper(context, sourceId, sourceName, baseUrl) {

    override val cardSelector = ".bsx, .bixbox .bs, .listupd .bs, article, .item, .post-show ul li, .animepost, .film-list .item"
    override val episodeSelector = ".episodelist li, .eplister li, .epslist li, .server-list li, ul.servers li, .lsteps li, .box-list-episode a, a.ep-item"

    override fun getVideoStream(episodeId: String): Flow<VideoStreamInfo> = flow {
        try {
            val customHeaders = this@AjaxObfuscatedThemeScraper.requestHeaders.toMutableMap()
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

            // 2. Fetch page HTML
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

            val resolvedQualities = mutableListOf<VideoQuality>()
            val resolvedServers = mutableListOf<VideoServer>()
            val embedUrls = mutableListOf<Pair<String, String>>() // (serverName, url)

            // A. Deobfuscate Packed JS: eval(function(p,a,c,k,e,d)...)
            val evalRegex = Regex("""eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k[\s\S]*?\)\s*\)""")
            for (match in evalRegex.findAll(pageHtml)) {
                val unpacked = JsPackerUnpacker.unpack(match.value) ?: continue
                val srcRegex = Regex("""(?:src|file)\s*[:=]\s*["']([^"']+)["']""")
                for (srcMatch in srcRegex.findAll(unpacked)) {
                    val streamOrEmbed = srcMatch.groupValues[1].replace("\\/", "/")
                    if (streamOrEmbed.startsWith("http")) {
                        embedUrls.add("JS Unpacked" to streamOrEmbed)
                    }
                }
            }

            // B. Check for admin-ajax.php actions (action=get_video_url)
            val ajaxActionMatches = Regex("""action\s*:\s*["']([^"']*(?:get_video_url|player_ajax|ajax_stream)[^"']*)["']""").findAll(pageHtml)
            for (actionMatch in ajaxActionMatches) {
                val actionName = actionMatch.groupValues[1]
                val ajaxUrl = "${currentBaseUrl().trimEnd('/')}/wp-admin/admin-ajax.php"
                val nonceMatch = Regex("""["']?nonce["']?\s*:\s*["']([a-zA-Z0-9]+)["']""").find(pageHtml)
                val nonce = nonceMatch?.groupValues?.get(1).orEmpty()

                val serverItems = doc.select(".server-list li, ul.servers li, select#server option, div.mirror-stream")
                for (serverItem in serverItems) {
                    val serverId = serverItem.attr("data-id").ifBlank { serverItem.attr("value") }.ifBlank { serverItem.attr("data-post") }
                    if (serverId.isNotBlank()) {
                        runCatching {
                            val form = FormBody.Builder()
                                .add("action", actionName)
                                .add("id", serverId)
                            if (nonce.isNotBlank()) form.add("nonce", nonce)
                            val req = Request.Builder()
                                .url(ajaxUrl)
                                .post(form.build())
                                .header("User-Agent", NetworkModule.userAgent)
                                .header("Referer", episodeId)
                                .header("X-Requested-With", "XMLHttpRequest")
                                .build()
                            val resp = NetworkModule.okHttpClient.newCall(req).execute()
                            if (resp.isSuccessful) {
                                val body = resp.body?.string().orEmpty()
                                val iframeInResp = Jsoup.parseBodyFragment(body).selectFirst("iframe")?.attr("src")
                                val target = iframeInResp ?: if (body.startsWith("http")) body else null
                                if (!target.isNullOrBlank()) {
                                    embedUrls.add(serverItem.text().trim() to target)
                                }
                            }
                        }
                    }
                }
            }

            // C. Extract standard iframes & options
            doc.select("iframe[src], iframe[data-src], div.player-embed iframe, #pembed iframe").forEach { iframe ->
                val src = iframe.absUrl("src").ifBlank { iframe.absUrl("data-src") }
                if (src.isNotBlank() && !src.contains("facebook") && !src.contains("disqus") && !src.contains("twitter")) {
                    embedUrls.add("Main Player" to src)
                }
            }

            doc.select("select#server option, select.mirror option, ul.servers li, .server-item, .server-list li").forEach { opt ->
                val label = opt.text().trim()
                val rawVal = opt.attr("value").ifBlank { opt.attr("data-post") }.ifBlank { opt.attr("data-content") }
                if (rawVal.isBase64Encoded()) {
                    runCatching {
                        val decoded = String(Base64.decode(rawVal, Base64.DEFAULT))
                        val iframe = Jsoup.parseBodyFragment(decoded).selectFirst("iframe")?.attr("src")
                        val target = iframe ?: if (decoded.startsWith("http")) decoded else null
                        if (!target.isNullOrBlank()) {
                            embedUrls.add(label to target)
                        }
                    }
                } else if (rawVal.startsWith("http")) {
                    embedUrls.add(label to rawVal)
                }
            }

            // D. Deep extraction
            for ((label, embedUrl) in embedUrls.distinctBy { it.second }) {
                val engineExtracted = runCatching { videoExtractorEngine.extractVideo(embedUrl, episodeId, customHeaders = customHeaders) }.getOrNull()
                if (engineExtracted != null && engineExtracted.qualities.isNotEmpty()) {
                    resolvedQualities.addAll(engineExtracted.qualities)
                    resolvedServers.add(
                        VideoServer(
                            name = label.ifBlank { embedUrl.detectProviderName() },
                            embedUrl = embedUrl,
                            qualities = engineExtracted.qualities,
                            headers = customHeaders
                        )
                    )
                    continue
                }

                val streamUrl = videoUrlInterceptor.interceptStreamUrl(embedUrl, timeoutMs = 8000L, headers = customHeaders)
                if (!streamUrl.isNullOrBlank()) {
                    val qLabel = label.extractQualityLabel()
                    val q = VideoQuality(qLabel, streamUrl, streamUrl.contains(".m3u8"), customHeaders)
                    resolvedQualities.add(q)
                    resolvedServers.add(
                        VideoServer(
                            name = label.ifBlank { embedUrl.detectProviderName() },
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
