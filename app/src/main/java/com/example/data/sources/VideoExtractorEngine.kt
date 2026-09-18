package com.example.data.sources

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.data.NetworkModule
import com.example.data.sources.anime.extractors.ApiResponseParser
import com.example.data.sources.anime.extractors.BloggerExtractor
import com.example.data.sources.anime.extractors.CryptoJsDecryptor
import com.example.data.sources.anime.extractors.DoodExtractor
import com.example.data.sources.anime.extractors.FileLionsExtractor
import com.example.data.sources.anime.extractors.GofileExtractor
import com.example.data.sources.anime.extractors.JsPackerUnpacker
import com.example.data.sources.anime.extractors.KrakenfilesExtractor
import com.example.data.sources.anime.extractors.Mp4UploadExtractor
import com.example.data.sources.anime.extractors.ServerExtractor
import com.example.data.sources.anime.extractors.StreamtapeExtractor
import com.example.data.sources.anime.extractors.VidHideExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Consolidated Video Extractor Engine: Single Source of Truth
 * Mengimplementasikan Chain of Responsibility untuk mengekstrak video stream
 * dari berbagai server host, link terenkripsi (AES/Packer), hingga dynamic headless interception.
 */
class VideoExtractorEngine(private val context: Context? = null) {

    private val client: OkHttpClient
        get() = NetworkModule.okHttpClient

    private val registeredExtractors = listOf<ServerExtractor>(
        BloggerExtractor(),
        StreamtapeExtractor(),
        DoodExtractor(),
        FileLionsExtractor(),
        VidHideExtractor(),
        Mp4UploadExtractor(),
        KrakenfilesExtractor(),
        GofileExtractor()
    )

    companion object {
        private val client: OkHttpClient
            get() = NetworkModule.okHttpClient

        fun createHeaders(url: String, referer: String = ""): Map<String, String> {
            val embedTarget = if (referer.isNotBlank() && referer.startsWith("http")) referer else url
            val embedHost = try {
                val uri = Uri.parse(embedTarget)
                if (!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) {
                    "${uri.scheme}://${uri.host}/"
                } else embedTarget
            } catch (e: Exception) {
                embedTarget
            }

            val origin = try {
                val uri = Uri.parse(embedTarget)
                if (!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) {
                    "${uri.scheme}://${uri.host}"
                } else ""
            } catch (e: Exception) {
                ""
            }

            val ref = if (referer.isNotBlank() && referer.startsWith("http")) referer else embedHost
            val headers = mutableMapOf(
                "Referer" to ref,
                "User-Agent" to NetworkModule.userAgent
            )
            if (origin.isNotBlank()) {
                headers["Origin"] = origin
            }
            return headers
        }

        fun parseM3u8Qualities(
            masterPlaylist: String,
            masterUrl: String,
            headers: Map<String, String> = emptyMap()
        ): List<VideoQuality> {
            val qualities = mutableListOf<VideoQuality>()
            val lines = masterPlaylist.split("\n")
            var currentResolution = "Auto"

            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val resMatch = Regex("RESOLUTION=\\d+x(\\d+)").find(line)
                    if (resMatch != null) {
                        currentResolution = "${resMatch.groupValues[1]}p"
                    }
                } else if (line.isNotEmpty() && !line.startsWith("#")) {
                    val streamUrl = if (line.startsWith("http")) {
                        line
                    } else {
                        val basePath = masterUrl.substringBeforeLast("/")
                        "$basePath/$line"
                    }
                    qualities.add(VideoQuality(currentResolution, streamUrl, true, headers))
                    currentResolution = "Auto"
                }
            }
            return qualities
        }
    }

    suspend fun extractVideo(
        episodeUrl: String,
        episodeId: String = episodeUrl,
        timeoutMs: Long = 18000L,
        customHeaders: Map<String, String> = emptyMap()
    ): VideoStreamInfo? = withContext(Dispatchers.IO) {
        val defaultHeaders = createHeaders(episodeUrl, episodeId).toMutableMap()
        defaultHeaders.putAll(customHeaders)
        val activeUa = defaultHeaders["User-Agent"] ?: defaultHeaders["user-agent"] ?: NetworkModule.userAgent
        val activeCookie = defaultHeaders["Cookie"] ?: defaultHeaders["cookie"]
        val activeReferer = defaultHeaders["Referer"] ?: defaultHeaders["referer"] ?: episodeUrl

        // Bypass / Fallback flag
        if (episodeUrl.contains("fallback-")) {
            return@withContext VideoStreamInfo(
                episodeId = episodeId,
                embedUrl = episodeUrl,
                headers = defaultHeaders,
                qualities = listOf(VideoQuality("auto", "", false, defaultHeaders))
            )
        }

        // ==========================================
        // STAGE 1: Direct Media Detection (MP4 / M3U8)
        // ==========================================
        if (episodeUrl.contains(".mp4", ignoreCase = true)) {
            return@withContext VideoStreamInfo(
                episodeId = episodeId,
                embedUrl = episodeUrl,
                headers = defaultHeaders,
                qualities = listOf(VideoQuality("Lokal/Raw", episodeUrl, false, defaultHeaders))
            )
        }

        if (episodeUrl.contains(".m3u8", ignoreCase = true)) {
            try {
                val requestBuilder = Request.Builder()
                    .url(episodeUrl)
                    .header("User-Agent", activeUa)
                    .header("Referer", activeReferer)
                if (!activeCookie.isNullOrBlank()) {
                    requestBuilder.header("Cookie", activeCookie)
                }
                val response = client.newCall(requestBuilder.build()).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val parsedQualities = parseM3u8Qualities(body, episodeUrl, defaultHeaders)
                    if (parsedQualities.isNotEmpty()) {
                        return@withContext VideoStreamInfo(
                            episodeId = episodeId,
                            embedUrl = episodeUrl,
                            headers = defaultHeaders,
                            qualities = parsedQualities
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore and proceed
            }
            return@withContext VideoStreamInfo(
                episodeId = episodeId,
                embedUrl = episodeUrl,
                headers = defaultHeaders,
                qualities = listOf(VideoQuality("Auto", episodeUrl, true, defaultHeaders))
            )
        }

        // ==========================================
        // STAGE 2: Dedicated FileHost Extractor Match
        // ==========================================
        val matchedExtractor = registeredExtractors.find { episodeUrl.contains(it.mainUrl, ignoreCase = true) }
        if (matchedExtractor != null) {
            try {
                val qualities = matchedExtractor.extract(episodeUrl)
                if (qualities.isNotEmpty()) {
                    val qualitiesWithHeaders = qualities.map { q ->
                        if (q.headers.isEmpty()) q.copy(headers = defaultHeaders) else q
                    }
                    return@withContext VideoStreamInfo(
                        episodeId = episodeId,
                        embedUrl = episodeUrl,
                        headers = defaultHeaders,
                        qualities = qualitiesWithHeaders
                    )
                }
            } catch (_: Exception) {}
        }

        // Domain-specific static handlers
        val domainMatchedInfo = when {
            episodeUrl.contains("drive.google.com") -> extractGoogleDrive(episodeUrl, defaultHeaders)
            episodeUrl.contains("ok.ru") || episodeUrl.contains("odnoklassniki.ru") -> extractOkRu(episodeUrl, defaultHeaders)
            episodeUrl.contains("fembed") || episodeUrl.contains("femax") -> extractFembed(episodeUrl, episodeId, defaultHeaders)
            episodeUrl.contains("filemoon") || episodeUrl.contains("fmcdn") -> extractFilemoon(episodeUrl, episodeId, defaultHeaders)
            episodeUrl.contains("streamtape") || episodeUrl.contains("strcloud") -> extractStreamtape(episodeUrl, defaultHeaders)
            episodeUrl.contains("mixdrop") || episodeUrl.contains("mixfile") -> extractMixdrop(episodeUrl, defaultHeaders)
            episodeUrl.contains("blogger.com") || episodeUrl.contains("kuradrive") -> extractBlogger(episodeUrl, defaultHeaders)
            episodeUrl.contains("desudrive") -> extractDesuDrive(episodeUrl, episodeId, defaultHeaders)
            episodeUrl.contains("bilibili") -> extractBilibili(episodeUrl, defaultHeaders)
            else -> null
        }

        if (domainMatchedInfo != null && domainMatchedInfo.qualities.isNotEmpty()) {
            return@withContext domainMatchedInfo
        }

        // ==========================================
        // STAGE 3: Fast HTML / DOM / Script Regex & AES Decryptor
        // ==========================================
        val genericInfo = extractGenericHtml(episodeUrl, episodeId, defaultHeaders)
        if (genericInfo != null && genericInfo.qualities.isNotEmpty()) {
            return@withContext genericInfo
        }

        // ==========================================
        // STAGE 4: Dynamic Headless Video Interception (WebView)
        // ==========================================
        if (context != null) {
            try {
                val videoInterceptor = com.example.data.sources.anime.VideoUrlInterceptor(context)
                val interceptedInfo = videoInterceptor.interceptVideo(episodeUrl, episodeId, timeoutMs, defaultHeaders)
                if (interceptedInfo != null && interceptedInfo.qualities.isNotEmpty()) {
                    val finalHeaders = if (interceptedInfo.headers.isNotEmpty()) interceptedInfo.headers else defaultHeaders
                    val qualitiesWithHeaders = interceptedInfo.qualities.map { q ->
                        if (q.headers.isEmpty()) q.copy(headers = finalHeaders) else q
                    }
                    return@withContext interceptedInfo.copy(headers = finalHeaders, qualities = qualitiesWithHeaders)
                }
            } catch (_: Exception) {}

            try {
                val webViewFetcher = WebViewFetcher(context)
                val streamResult = webViewFetcher.interceptMediaStreamAdvanced(episodeUrl, timeoutMs, defaultHeaders)
                if (streamResult != null && streamResult.url.isNotBlank()) {
                    val finalHeaders = if (streamResult.headers.isNotEmpty()) streamResult.headers else defaultHeaders
                    val streamUrl = streamResult.url
                    var qualities = streamResult.directQualities
                    if (qualities.isEmpty()) {
                        if (streamResult.isM3U8 || streamUrl.contains(".m3u8", ignoreCase = true)) {
                            try {
                                val requestBuilder = Request.Builder().url(streamUrl)
                                finalHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }
                                val response = client.newCall(requestBuilder.build()).execute()
                                if (response.isSuccessful) {
                                    val body = response.body?.string() ?: ""
                                    qualities = parseM3u8Qualities(body, streamUrl, finalHeaders)
                                }
                            } catch (_: Exception) {}
                            if (qualities.isEmpty()) {
                                qualities = listOf(VideoQuality("Auto", streamUrl, true, finalHeaders))
                            }
                        } else {
                            qualities = listOf(VideoQuality("Auto", streamUrl, false, finalHeaders))
                        }
                    }

                    if (qualities.isNotEmpty()) {
                        return@withContext VideoStreamInfo(
                            episodeId = episodeId,
                            embedUrl = streamUrl,
                            headers = finalHeaders,
                            qualities = qualities,
                            subtitles = streamResult.subtitles
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        // ==========================================
        // STAGE 5: Cloudflare Worker Proxy Fallback
        // ==========================================
        try {
            val proxyHtml = NetworkModule.fetchViaCloudflareWorker(episodeUrl, defaultHeaders)
            if (!proxyHtml.isNullOrBlank()) {
                val streams = ApiResponseParser.extractStreamUrls(episodeUrl, proxyHtml)
                if (streams.isNotEmpty()) {
                    val qualities = streams.map { s ->
                        VideoQuality("HD", s, isM3U8 = s.contains(".m3u8", ignoreCase = true), headers = defaultHeaders)
                    }
                    return@withContext VideoStreamInfo(
                        episodeId = episodeId,
                        embedUrl = episodeUrl,
                        headers = defaultHeaders,
                        qualities = qualities
                    )
                }
            }
        } catch (_: Exception) {}

        // ==========================================
        // STAGE 6: Ultimate Fallback VideoStreamInfo
        // ==========================================
        return@withContext VideoStreamInfo(
            episodeId = episodeId,
            embedUrl = episodeUrl,
            headers = defaultHeaders,
            qualities = emptyList()
        )
    }

    private suspend fun fetchHtml(url: String, headers: Map<String, String> = emptyMap()): String? = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder().url(url)
                .header("User-Agent", NetworkModule.userAgent)
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            val response = client.newCall(reqBuilder.build()).execute()
            var body = response.body?.string()

            if (body != null && (body.contains("<title>Just a moment...</title>") || body.contains("Cloudflare") || body.contains("cf-browser-verification"))) {
                val proxyHtml = NetworkModule.fetchViaCloudflareWorker(url, headers)
                if (!proxyHtml.isNullOrBlank()) {
                    body = proxyHtml
                }
            }
            body
        } catch (_: Exception) {
            null
        }
    }

    private fun extractGoogleDrive(url: String, headers: Map<String, String>): VideoStreamInfo? {
        val fileId = Regex("[-\\w]{25,}").find(url)?.value ?: return null
        val directUrl = "https://drive.google.com/uc?export=download&id=$fileId"
        return VideoStreamInfo(
            episodeId = url,
            embedUrl = url,
            headers = headers,
            qualities = listOf(VideoQuality("auto", directUrl, false, headers))
        )
    }

    private suspend fun extractOkRu(url: String, headers: Map<String, String>): VideoStreamInfo? {
        val html = fetchHtml(url) ?: return null
        val pattern = Regex(""""hls":\s*"([^"]+)"""")
        val m3u8 = pattern.find(html)?.groupValues?.get(1)?.replace("\\\\", "")?.replace("\\", "") ?: return null
        return VideoStreamInfo(
            episodeId = url,
            embedUrl = url,
            headers = headers,
            qualities = listOf(VideoQuality("auto", m3u8, true, headers))
        )
    }

    private suspend fun extractFembed(url: String, referer: String, headers: Map<String, String>): VideoStreamInfo? {
        val videoId = url.substringAfterLast("/").substringBefore("?")
        val apiUrl = url.substringBefore("/v/") + "/api/source/$videoId"
        val html = fetchHtml(apiUrl, mapOf("Referer" to referer)) ?: return null
        val qualities = Regex(""""file":"([^"]+)","label":"([^"]+)"""")
            .findAll(html).map { m ->
                VideoQuality(m.groupValues[2], m.groupValues[1].replace("\\/", "/"), m.groupValues[1].contains(".m3u8"), headers)
            }.toList()
        return VideoStreamInfo(episodeId = url, embedUrl = url, headers = headers, qualities = qualities)
    }

    private suspend fun extractFilemoon(url: String, referer: String, headers: Map<String, String>): VideoStreamInfo? {
        val html = fetchHtml(url, mapOf("Referer" to referer)) ?: return null
        val m3u8 = Regex("""sources:\s*\[\{file:"([^"]+\.m3u8[^"]*)" """).find(html)?.groupValues?.get(1) ?: return null

        val subtitleTracks = mutableListOf<Subtitle>()
        val tracksRegex = Regex("""tracks:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        val tracksMatch = tracksRegex.find(html)
        if (tracksMatch != null) {
            val trackBlocks = Regex("""\{([^}]*)\}""").findAll(tracksMatch.groupValues[1])
            for (block in trackBlocks) {
                val blockHtml = block.groupValues[1]
                val file = Regex("""file:\s*["']([^"']+)["']""").find(blockHtml)?.groupValues?.get(1)
                val label = Regex("""label:\s*["']([^"']+)["']""").find(blockHtml)?.groupValues?.get(1) ?: "Indonesian"
                val kind = Regex("""kind:\s*["']([^"']+)["']""").find(blockHtml)?.groupValues?.get(1)
                if (file != null && kind == "captions") {
                    subtitleTracks.add(Subtitle(language = label, url = file, format = file.substringAfterLast(".", "vtt")))
                }
            }
        }

        return VideoStreamInfo(
            episodeId = url,
            embedUrl = url,
            headers = headers,
            qualities = listOf(VideoQuality("auto", m3u8, true, headers)),
            subtitles = subtitleTracks
        )
    }

    private suspend fun extractStreamtape(url: String, headers: Map<String, String>): VideoStreamInfo? {
        val html = fetchHtml(url) ?: return null
        var videoUrl = ""
        val linkRegex = Regex("""document\.getElementById\('robotlink'\)\.innerHTML = '([^']+)'\+ \('xcd([^']+)'\)""")
        val match = linkRegex.find(html)
        if (match != null) {
            videoUrl = "https:" + match.groupValues[1] + match.groupValues[2].substring(3)
        } else {
            val evalRegex = Regex("""eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k[\s\S]*?\)\s*\)""")
            val evalMatch = evalRegex.find(html)
            if (evalMatch != null) {
                val unpacked = JsPackerUnpacker.unpack(evalMatch.value)
                if (unpacked != null) {
                    val srcRegex = Regex("""src=["']([^"']+\.mp4[^"']*)["']""")
                    val srcMatch = srcRegex.find(unpacked)
                    if (srcMatch != null) {
                        videoUrl = srcMatch.groupValues[1]
                    }
                }
            }
        }
        if (videoUrl.isEmpty()) return null
        return VideoStreamInfo(
            episodeId = url,
            embedUrl = url,
            headers = headers,
            qualities = listOf(VideoQuality("auto", videoUrl, false, headers))
        )
    }

    private suspend fun extractMixdrop(url: String, headers: Map<String, String>): VideoStreamInfo? {
        val html = fetchHtml(url) ?: return null
        val evalRegex = Regex("""eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k[\s\S]*?\)\s*\)""")
        val match = evalRegex.find(html) ?: return null
        val unpacked = JsPackerUnpacker.unpack(match.value) ?: return null

        val wurlRegex = Regex("""wurl=["']([^"']+)["']""")
        val wurlMatch = wurlRegex.find(unpacked) ?: return null
        var videoUrl = wurlMatch.groupValues[1]
        if (videoUrl.startsWith("//")) videoUrl = "https:$videoUrl"

        return VideoStreamInfo(
            episodeId = url,
            embedUrl = url,
            headers = headers,
            qualities = listOf(VideoQuality("auto", videoUrl, false, headers))
        )
    }

    private suspend fun extractBlogger(url: String, headers: Map<String, String>): VideoStreamInfo? {
        val html = fetchHtml(url) ?: return null
        val qualities = mutableListOf<VideoQuality>()

        val configMatch = Regex("""var\s+VIDEO_CONFIG\s*=\s*(\{.*?\});""").find(html)
        if (configMatch != null) {
            val config = configMatch.groupValues[1]
            val streamsMatch = Regex(""""streams"\s*:\s*\[(.*?)\]""").find(config)
            if (streamsMatch != null) {
                val streams = streamsMatch.groupValues[1]
                val streamBlocks = Regex("""\{([^}]+)\}""").findAll(streams)
                for (block in streamBlocks) {
                    val playUrl = Regex(""""play_url"\s*:\s*"([^"]+)"""").find(block.value)?.groupValues?.get(1)
                    val formatId = Regex(""""format_id"\s*:\s*(\d+)""").find(block.value)?.groupValues?.get(1)?.toIntOrNull()
                    if (playUrl != null) {
                        val qualityName = when (formatId) {
                            18 -> "360p"
                            22 -> "720p"
                            37 -> "1080p"
                            else -> "Auto ($formatId)"
                        }
                        qualities.add(VideoQuality(qualityName, playUrl, false, headers))
                    }
                }
            }
        }

        if (qualities.isNotEmpty()) {
            return VideoStreamInfo(episodeId = url, embedUrl = url, headers = headers, qualities = qualities)
        }
        return null
    }

    private suspend fun extractDesuDrive(url: String, referer: String, headers: Map<String, String>): VideoStreamInfo? {
        val html = fetchHtml(url, mapOf("Referer" to referer)) ?: return null
        var match = Regex("""sources:\s*\[\s*\{\s*file:\s*['"]([^'"]+)['"]""").find(html)
        if (match == null) {
            match = Regex("""src:\s*['"]([^'"]+\.mp4[^'"]*)['"]""").find(html)
        }

        if (match != null) {
            val videoUrl = match.groupValues[1]
            return VideoStreamInfo(
                episodeId = url,
                embedUrl = url,
                headers = headers,
                qualities = listOf(VideoQuality("auto", videoUrl, videoUrl.contains(".m3u8"), headers))
            )
        }
        return null
    }

    private suspend fun extractBilibili(url: String, headers: Map<String, String>): VideoStreamInfo? {
        val html = fetchHtml(url, mapOf("Referer" to url)) ?: return null
        val playInfoMatch = Regex("""window\.__playinfo__\s*=\s*(\{.*?\})\s*</script>""").find(html)
        if (playInfoMatch != null) {
            val playInfo = playInfoMatch.groupValues[1]
            val urlMatches = Regex(""""url"\s*:\s*"([^"]+)"""").findAll(playInfo)
            val qualities = mutableListOf<VideoQuality>()
            for (match in urlMatches) {
                val videoUrl = match.groupValues[1].replace("\\u0026", "&").replace("\\/", "/")
                if (videoUrl.contains(".mp4") || videoUrl.contains(".m3u8")) {
                    qualities.add(VideoQuality("Auto", videoUrl, videoUrl.contains(".m3u8"), headers))
                    break
                }
            }
            if (qualities.isNotEmpty()) {
                return VideoStreamInfo(episodeId = url, embedUrl = url, headers = headers, qualities = qualities)
            }
        }
        return null
    }

    private suspend fun extractGenericHtml(url: String, referer: String, streamHeaders: Map<String, String>): VideoStreamInfo? {
        val html = fetchHtml(url, mapOf("Referer" to if (referer.isNotBlank()) referer else url)) ?: return null
        val qualities = mutableListOf<VideoQuality>()
        val subtitleTracks = mutableListOf<Subtitle>()

        fun addQuality(label: String, streamUrl: String) {
            val cleanUrl = streamUrl.replace("\\/", "/").trim().removeSurrounding("\"").removeSurrounding("'")
            if (cleanUrl.isNotBlank() && (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://"))) {
                if (qualities.none { it.url == cleanUrl }) {
                    val isM3u8 = cleanUrl.contains(".m3u8", ignoreCase = true)
                    qualities.add(VideoQuality(label, cleanUrl, isM3u8, streamHeaders))
                }
            }
        }

        fun extractSubtitlesFromText(text: String) {
            val tracksRegex = Regex("""tracks:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            tracksRegex.find(text)?.let { m ->
                val trackBlocks = Regex("""\{([^}]*)\}""").findAll(m.groupValues[1])
                for (block in trackBlocks) {
                    val blockHtml = block.groupValues[1]
                    val file = Regex("""file:\s*["']([^"']+)["']""").find(blockHtml)?.groupValues?.get(1)?.replace("\\/", "/")
                    val label = Regex("""label:\s*["']([^"']+)["']""").find(blockHtml)?.groupValues?.get(1) ?: "Indonesian"
                    if (file != null && (file.contains(".vtt") || file.contains(".srt") || file.contains("caption"))) {
                        if (subtitleTracks.none { it.url == file }) {
                            subtitleTracks.add(Subtitle(language = label, url = file, format = file.substringAfterLast(".", "vtt")))
                        }
                    }
                }
            }
        }

        val sourceRegexes = listOf(
            Regex("""(?:source|file|src)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""(?:source|file|src)\s*:\s*["']([^"']+\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE)
        )
        for (regex in sourceRegexes) {
            regex.findAll(html).forEach { m ->
                addQuality("auto", m.groupValues[1])
            }
        }

        val jwRegex = Regex("""sources\s*:\s*(\[\s*\{.*?\}\s*\])""", RegexOption.DOT_MATCHES_ALL)
        val jwMatch = jwRegex.find(html)
        if (jwMatch != null) {
            val sourcesJson = jwMatch.groupValues[1]
            val fileRegex = Regex("""(?:file|src)\s*:\s*["']([^"']+)["']""")
            val labelRegex = Regex("""label\s*:\s*["']([^"']+)["']""")
            val blocks = Regex("""\{([^}]+)\}""").findAll(sourcesJson)
            for (block in blocks) {
                val bHtml = block.groupValues[1]
                val fMatch = fileRegex.find(bHtml)
                if (fMatch != null) {
                    val src = fMatch.groupValues[1]
                    val label = labelRegex.find(bHtml)?.groupValues?.get(1) ?: "auto"
                    addQuality(label, src)
                }
            }
        }
        extractSubtitlesFromText(html)

        // Packed JS unpacking (Dean Edwards packer)
        val evalRegex = Regex("""eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k[\s\S]*?\)\s*\)""")
        for (evalMatch in evalRegex.findAll(html)) {
            val unpacked = JsPackerUnpacker.unpack(evalMatch.value) ?: continue
            for (regex in sourceRegexes) {
                regex.findAll(unpacked).forEach { m ->
                    addQuality("auto", m.groupValues[1])
                }
            }
            val jwInUnpacked = jwRegex.find(unpacked)
            if (jwInUnpacked != null) {
                val fileRegex = Regex("""(?:file|src)\s*:\s*["']([^"']+)["']""")
                val labelRegex = Regex("""label\s*:\s*["']([^"']+)["']""")
                val blocks = Regex("""\{([^}]+)\}""").findAll(jwInUnpacked.groupValues[1])
                for (block in blocks) {
                    val bHtml = block.groupValues[1]
                    val fMatch = fileRegex.find(bHtml)
                    if (fMatch != null) {
                        val src = fMatch.groupValues[1]
                        val label = labelRegex.find(bHtml)?.groupValues?.get(1) ?: "auto"
                        addQuality(label, src)
                    }
                }
            }
            extractSubtitlesFromText(unpacked)
        }

        // Native AES Decryption
        val aesInfo = extractAesPayload(html, url, referer)
        if (aesInfo != null && aesInfo.qualities.isNotEmpty()) {
            val aesQualitiesWithHeaders = aesInfo.qualities.map {
                if (it.headers.isEmpty()) it.copy(headers = streamHeaders) else it
            }
            return aesInfo.copy(
                headers = if (aesInfo.headers.isEmpty()) streamHeaders else aesInfo.headers,
                qualities = aesQualitiesWithHeaders
            )
        }

        if (qualities.isNotEmpty()) {
            return VideoStreamInfo(
                episodeId = url,
                embedUrl = url,
                headers = streamHeaders,
                qualities = qualities,
                subtitles = subtitleTracks
            )
        }

        return null
    }

    private suspend fun extractAesPayload(html: String, url: String, referer: String): VideoStreamInfo? {
        val encryptedPayloads = linkedSetOf<String>()
        val aesPayloadRegex = Regex("""["'](U2FsdGVkX1[a-zA-Z0-9+/=]{16,})["']""")
        for (m in aesPayloadRegex.findAll(html)) {
            encryptedPayloads.add(m.groupValues[1])
        }

        val dataPayloadRegex = Regex("""data-(?:payload|encrypted|video|src|stream)=["'](U2FsdGVkX1[a-zA-Z0-9+/=]{16,})["']""", RegexOption.IGNORE_CASE)
        for (m in dataPayloadRegex.findAll(html)) {
            encryptedPayloads.add(m.groupValues[1])
        }

        val rawPayloadRegex = Regex("""\b(U2FsdGVkX1[a-zA-Z0-9+/=]{24,})\b""")
        for (m in rawPayloadRegex.findAll(html)) {
            encryptedPayloads.add(m.groupValues[1])
        }

        if (encryptedPayloads.isEmpty()) return null

        val candidateKeys = linkedSetOf<String>()

        val scriptVars = mutableMapOf<String, String>()
        val varRegex = Regex("""(?:var|let|const)\s+([a-zA-Z0-9_$]+)\s*=\s*["']([^"']{3,128})["']""")
        for (m in varRegex.findAll(html)) {
            val varName = m.groupValues[1]
            val value = m.groupValues[2]
            scriptVars[varName] = value
            if (varName.contains("pass", ignoreCase = true) ||
                varName.contains("key", ignoreCase = true) ||
                varName.contains("secret", ignoreCase = true) ||
                varName.contains("token", ignoreCase = true) ||
                varName.contains("auth", ignoreCase = true)
            ) {
                candidateKeys.add(value)
            }
        }

        val decryptLiteralRegex = Regex("""CryptoJS\.AES\.decrypt\s*\(\s*[^,]+,\s*["']([^"']+)["']""")
        for (m in decryptLiteralRegex.findAll(html)) {
            candidateKeys.add(m.groupValues[1])
        }

        val decryptVarRegex = Regex("""CryptoJS\.AES\.decrypt\s*\(\s*[^,]+,\s*([a-zA-Z0-9_$]+)\s*[\),:]""")
        for (m in decryptVarRegex.findAll(html)) {
            val varName = m.groupValues[1]
            scriptVars[varName]?.let { candidateKeys.add(it) }
        }

        val keyAttrRegex = Regex("""(?:pass(?:phrase|key)?|secret|token|crypto_?key|aes_?key|password|auth_?key|app_?key|salt|iv)\s*[:=]\s*["']([^"']{3,128})["']""", RegexOption.IGNORE_CASE)
        for (m in keyAttrRegex.findAll(html)) {
            candidateKeys.add(m.groupValues[1])
        }

        val dataAttrRegex = Regex("""data-(?:pass(?:phrase|key)?|secret|token|key|salt|iv)=["']([^"']{3,128})["']""", RegexOption.IGNORE_CASE)
        for (m in dataAttrRegex.findAll(html)) {
            candidateKeys.add(m.groupValues[1])
        }

        val scriptTagRegex = Regex("""<script[^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)
        for (scriptMatch in scriptTagRegex.findAll(html)) {
            val scriptContent = scriptMatch.groupValues[1]
            if (scriptContent.contains("CryptoJS") || scriptContent.contains("U2FsdGVkX1")) {
                val literalRegex = Regex("""["']([a-zA-Z0-9_-]{4,64})["']""")
                for (literalMatch in literalRegex.findAll(scriptContent)) {
                    val lit = literalMatch.groupValues[1]
                    if (!lit.startsWith("U2FsdGVkX1") && !lit.startsWith("http")) {
                        candidateKeys.add(lit)
                    }
                }
            }
        }

        try {
            val host = Uri.parse(url).host.orEmpty()
            if (host.isNotBlank()) {
                candidateKeys.add(host)
                val cleanHost = host.removePrefix("www.").substringBefore(":")
                candidateKeys.add(cleanHost)
                val domainName = cleanHost.substringBeforeLast(".").substringAfterLast(".")
                if (domainName.isNotBlank()) candidateKeys.add(domainName)
                val firstPart = cleanHost.substringBefore(".")
                if (firstPart.isNotBlank()) candidateKeys.add(firstPart)
            }
        } catch (_: Exception) {}

        candidateKeys.add("kuramanime")
        candidateKeys.add("animeindo")
        candidateKeys.add("animasu")
        candidateKeys.add("otakudesu")
        candidateKeys.add("samehadaku")
        candidateKeys.add("oploverz")
        candidateKeys.add("anichin")
        candidateKeys.add("nimegami")
        candidateKeys.add("kazefuri")
        candidateKeys.add("kusonime")

        for (encrypted in encryptedPayloads) {
            for (key in candidateKeys) {
                val decrypted = CryptoJsDecryptor.decrypt(encrypted, key) ?: continue
                if (decrypted.isNotBlank()) {
                    val streamResult = CryptoJsDecryptor.parseDecryptedPayload(decrypted, referer.ifEmpty { url })
                    if (streamResult.qualities.isNotEmpty()) {
                        return VideoStreamInfo(
                            episodeId = url,
                            qualities = streamResult.qualities,
                            subtitles = streamResult.subtitles,
                            headers = mapOf("Referer" to (if (referer.isNotBlank()) referer else url))
                        )
                    }
                    if (streamResult.embedUrl.isNotBlank()) {
                        val nested = extractVideo(streamResult.embedUrl, url)
                        if (nested != null && nested.qualities.isNotEmpty()) {
                            return nested
                        }
                        return VideoStreamInfo(
                            episodeId = url,
                            embedUrl = streamResult.embedUrl,
                            subtitles = streamResult.subtitles
                        )
                    }
                }
            }
        }

        return null
    }
}
