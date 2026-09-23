package com.example.data.sources.anime.extractors

import android.net.Uri
import com.example.data.NetworkModule
import com.example.data.sources.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Dedicated Extractor for StreamWish / StreamWish Clones.
 *
 * Characteristics:
 * - Domains: streamwish.to, streamwish.com, wishembed.pro, swishsrv.com, strwish.com,
 *   dwish.org, wishfast.top, streamwish.top, embedwish.com, ajcontent.com
 * - Obfuscation: Uses Dean Edwards' Packer eval(function(p,a,c,k,e,d)...) or embedded JWPlayer sources / m3u8 playlists.
 * - Streams: Served via master.m3u8 or direct mp4 sources requiring Referer and Origin headers.
 */
class StreamWishExtractor : ServerExtractor {
    override val name = "StreamWish"
    override val mainUrl = "streamwish"

    fun canExtract(urlOrPayload: String): Boolean {
        val lower = urlOrPayload.lowercase()
        return lower.contains("streamwish") ||
               lower.contains("wishembed") ||
               lower.contains("swishsrv") ||
               lower.contains("strwish") ||
               lower.contains("dwish") ||
               lower.contains("wishfast") ||
               lower.contains("embedwish") ||
               lower.contains("ajcontent")
    }

    override suspend fun extract(url: String): List<VideoQuality> = withContext(Dispatchers.IO) {
        val qualities = mutableListOf<VideoQuality>()
        try {
            val client = NetworkModule.okHttpClient
            val parsedUri = Uri.parse(url)
            val host = parsedUri.host ?: "streamwish.to"

            val streamHeaders = mapOf(
                "User-Agent" to NetworkModule.userAgent,
                "Referer" to "https://$host/",
                "Origin" to "https://$host"
            )

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkModule.userAgent)
                .header("Referer", url)
                .build()

            val response = client.newCall(req).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val html = response.body?.string().orEmpty()

            qualities.addAll(extractFromHtml(html, host, streamHeaders))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        qualities.distinctBy { it.url }
    }

    fun extractFromHtml(
        html: String,
        host: String = "streamwish.to",
        streamHeaders: Map<String, String> = mapOf(
            "User-Agent" to NetworkModule.userAgent,
            "Referer" to "https://$host/",
            "Origin" to "https://$host"
        )
    ): List<VideoQuality> {
        val qualities = mutableListOf<VideoQuality>()

        val streamRegexes = listOf(
            Regex("""(?:file|source|src)\s*:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""["'](https?://[^"']+\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""sources\s*:\s*\[\s*\{\s*file\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        )

        for (regex in streamRegexes) {
            for (match in regex.findAll(html)) {
                val streamUrl = match.groupValues[1].replace("\\/", "/")
                if (streamUrl.isNotBlank() && !streamUrl.endsWith(".jpg") && !streamUrl.endsWith(".png")) {
                    val isM3u8 = streamUrl.contains(".m3u8")
                    qualities.add(VideoQuality(if (isM3u8) "HLS (StreamWish)" else "Direct (StreamWish)", streamUrl, isM3u8, streamHeaders))
                }
            }
        }

        // Check if packed with JS Packer
        if (qualities.isEmpty()) {
            val evalRegex = Regex("""eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k[\s\S]*?\)\s*\)""")
            for (evalMatch in evalRegex.findAll(html)) {
                val unpacked = JsPackerUnpacker.unpack(evalMatch.value) ?: continue
                for (regex in streamRegexes) {
                    for (m in regex.findAll(unpacked)) {
                        val streamUrl = m.groupValues[1].replace("\\/", "/")
                        if (streamUrl.isNotBlank() && !streamUrl.endsWith(".jpg") && !streamUrl.endsWith(".png")) {
                            val isM3u8 = streamUrl.contains(".m3u8")
                            qualities.add(VideoQuality(if (isM3u8) "HLS (StreamWish)" else "Direct (StreamWish)", streamUrl, isM3u8, streamHeaders))
                        }
                    }
                }
                if (qualities.isNotEmpty()) break
            }
        }

        return qualities
    }
}
