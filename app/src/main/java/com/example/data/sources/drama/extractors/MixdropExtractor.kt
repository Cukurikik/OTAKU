package com.example.data.sources.drama.extractors

import android.net.Uri
import com.example.data.NetworkModule
import com.example.data.sources.VideoQuality
import com.example.data.sources.anime.extractors.JsPackerUnpacker
import com.example.data.sources.anime.extractors.ServerExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Dedicated Extractor for Mixdrop (mixdrop.co, mixdrop.to, mixdrop.sx, mixdrop.bz, etc.).
 *
 * Mechanism:
 * 1. Convert standard /f/ link to /e/ embed link if necessary.
 * 2. Unpack MDCore JavaScript payload.
 * 3. Extract MDCore.wurl / MDCore.vurl direct video stream.
 */
class MixdropExtractor : ServerExtractor {
    override val name = "Mixdrop"
    override val mainUrl = "mixdrop"

    override suspend fun extract(url: String): List<VideoQuality> = withContext(Dispatchers.IO) {
        val qualities = mutableListOf<VideoQuality>()
        try {
            val client = NetworkModule.okHttpClient
            val embedUrl = if (url.contains("/f/")) {
                url.replace("/f/", "/e/")
            } else url

            val parsedUri = Uri.parse(embedUrl)
            val host = parsedUri.host ?: "mixdrop.co"

            val streamHeaders = mapOf(
                "User-Agent" to NetworkModule.userAgent,
                "Referer" to "https://$host/",
                "Origin" to "https://$host"
            )

            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", NetworkModule.userAgent)
                .header("Referer", embedUrl)
                .build()

            val response = client.newCall(req).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val html = response.body?.string().orEmpty()

            // 1. Unpack Dean Edwards MDCore packed code
            val evalRegex = Regex("""eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k[\s\S]*?\)\s*\)""")
            for (evalMatch in evalRegex.findAll(html)) {
                val unpacked = JsPackerUnpacker.unpack(evalMatch.value) ?: continue
                val vurlRegex = Regex("""(?:MDCore\.(?:vurl|wurl|url)|wurl|vurl)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                val match = vurlRegex.find(unpacked)
                if (match != null) {
                    var streamUrl = match.groupValues[1]
                    if (streamUrl.startsWith("//")) {
                        streamUrl = "https:$streamUrl"
                    }
                    val isM3u8 = streamUrl.contains(".m3u8")
                    qualities.add(VideoQuality(if (isM3u8) "HLS (Mixdrop)" else "Direct 720p (Mixdrop)", streamUrl, isM3u8, streamHeaders))
                    break
                }
            }

            // 2. Direct regex fallback
            if (qualities.isEmpty()) {
                val directRegex = Regex("""(?:MDCore\.(?:vurl|wurl)|wurl)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                val directMatch = directRegex.find(html)
                if (directMatch != null) {
                    var streamUrl = directMatch.groupValues[1]
                    if (streamUrl.startsWith("//")) {
                        streamUrl = "https:$streamUrl"
                    }
                    val isM3u8 = streamUrl.contains(".m3u8")
                    qualities.add(VideoQuality(if (isM3u8) "HLS (Mixdrop)" else "Direct (Mixdrop)", streamUrl, isM3u8, streamHeaders))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        qualities.distinctBy { it.url }
    }
}
