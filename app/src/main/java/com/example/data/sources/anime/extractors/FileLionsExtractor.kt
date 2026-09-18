package com.example.data.sources.anime.extractors

import android.net.Uri
import com.example.data.NetworkModule
import com.example.data.sources.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Dedicated Extractor for FileLions and related StreamWish clones (vidhidepro, filelions.to, etc.).
 *
 * Mechanism:
 * 1. Fetch embed page with proper headers.
 * 2. Unpack Dean Edwards packed JavaScript: eval(function(p,a,c,k,e,d)...).
 * 3. Extract m3u8 sources or file: "..." definitions.
 * 4. Build VideoQuality list with proper stream referer.
 */
class FileLionsExtractor : ServerExtractor {
    override val name = "FileLions"
    override val mainUrl = "filelions"

    override suspend fun extract(url: String): List<VideoQuality> = withContext(Dispatchers.IO) {
        val qualities = mutableListOf<VideoQuality>()
        try {
            val client = NetworkModule.okHttpClient
            val parsedUri = Uri.parse(url)
            val host = parsedUri.host ?: "filelions.to"

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

            // 1. Direct regex for sources:[{file:"..."}]
            val fileRegex = Regex("""(?:file|src)\s*:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""")
            for (match in fileRegex.findAll(html)) {
                val streamUrl = match.groupValues[1].replace("\\/", "/")
                val isM3u8 = streamUrl.contains(".m3u8")
                qualities.add(VideoQuality(if (isM3u8) "HLS (FileLions)" else "Direct (FileLions)", streamUrl, isM3u8, streamHeaders))
            }

            // 2. Unpack eval JS
            if (qualities.isEmpty()) {
                val evalRegex = Regex("""eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k[\s\S]*?\)\s*\)""")
                for (evalMatch in evalRegex.findAll(html)) {
                    val unpacked = JsPackerUnpacker.unpack(evalMatch.value) ?: continue
                    for (m in fileRegex.findAll(unpacked)) {
                        val streamUrl = m.groupValues[1].replace("\\/", "/")
                        val isM3u8 = streamUrl.contains(".m3u8")
                        qualities.add(VideoQuality(if (isM3u8) "HLS (FileLions)" else "Direct (FileLions)", streamUrl, isM3u8, streamHeaders))
                    }
                    if (qualities.isNotEmpty()) break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        qualities.distinctBy { it.url }
    }
}
