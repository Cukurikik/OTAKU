package com.example.data.sources.anime.extractors

import android.net.Uri
import com.example.data.NetworkModule
import com.example.data.sources.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Dedicated Extractor for Mp4Upload (mp4upload.com).
 *
 * Characteristics:
 * - Domain: mp4upload.com
 * - Direct MP4 player embedded inside script or player tag.
 * - Pattern: src: "https://...mp4" or player.src("https://...mp4")
 * - Stream requires Referer to https://www.mp4upload.com/
 */
class Mp4UploadExtractor : ServerExtractor {
    override val name = "Mp4Upload"
    override val mainUrl = "mp4upload.com"

    override suspend fun extract(url: String): List<VideoQuality> = withContext(Dispatchers.IO) {
        val qualities = mutableListOf<VideoQuality>()
        try {
            val client = NetworkModule.okHttpClient
            val parsedUri = Uri.parse(url)
            val host = parsedUri.host ?: "www.mp4upload.com"

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

            val srcPatterns = listOf(
                Regex("""(?:src|file)\s*:\s*["']([^"']+\.mp4[^"']*)["']"""),
                Regex("""player\.src\(\s*["']([^"']+\.mp4[^"']*)["']"""),
                Regex("""<source[^>]+src=["']([^"']+\.mp4[^"']*)["']""")
            )

            for (p in srcPatterns) {
                val match = p.find(html)
                if (match != null) {
                    val streamUrl = match.groupValues[1]
                    qualities.add(VideoQuality("Direct (Mp4Upload)", streamUrl, false, streamHeaders))
                    break
                }
            }

            if (qualities.isEmpty()) {
                val evalRegex = Regex("""eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k[\s\S]*?\)\s*\)""")
                for (evalMatch in evalRegex.findAll(html)) {
                    val unpacked = JsPackerUnpacker.unpack(evalMatch.value) ?: continue
                    for (p in srcPatterns) {
                        val match = p.find(unpacked)
                        if (match != null) {
                            val streamUrl = match.groupValues[1]
                            qualities.add(VideoQuality("Direct (Mp4Upload)", streamUrl, false, streamHeaders))
                            break
                        }
                    }
                    if (qualities.isNotEmpty()) break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        qualities
    }
}
