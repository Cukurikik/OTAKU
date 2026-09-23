package com.example.data.sources.drama.extractors

import android.net.Uri
import com.example.data.NetworkModule
import com.example.data.sources.VideoQuality
import com.example.data.sources.anime.extractors.JsPackerUnpacker
import com.example.data.sources.anime.extractors.ServerExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Dedicated Extractor for HxFile (hxfile.co, hxfile.xyz, hxfile.cc).
 *
 * Mechanism:
 * 1. Fetch embed / share page.
 * 2. Unpack JS packed scripts or extract video element / stream sources.
 * 3. Fallback: Parse POST form for direct download stream.
 */
class HxFileExtractor : ServerExtractor {
    override val name = "HxFile"
    override val mainUrl = "hxfile"

    override suspend fun extract(url: String): List<VideoQuality> = withContext(Dispatchers.IO) {
        val qualities = mutableListOf<VideoQuality>()
        try {
            val client = NetworkModule.okHttpClient
            val parsedUri = Uri.parse(url)
            val host = parsedUri.host ?: "hxfile.co"

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

            // 1. Direct regex for video source or source tags
            val doc = Jsoup.parse(html, url)
            val videoSrc = doc.selectFirst("video source, video")?.attr("src")
            if (!videoSrc.isNullOrBlank()) {
                val isM3u8 = videoSrc.contains(".m3u8")
                qualities.add(VideoQuality(if (isM3u8) "HLS (HxFile)" else "Direct (HxFile)", videoSrc, isM3u8, streamHeaders))
            }

            // 2. Direct regex for file: "..." or sources: [{file: "..."}]
            val fileRegex = Regex("""(?:file|src)\s*:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""", RegexOption.IGNORE_CASE)
            for (match in fileRegex.findAll(html)) {
                val streamUrl = match.groupValues[1].replace("\\/", "/")
                val isM3u8 = streamUrl.contains(".m3u8")
                qualities.add(VideoQuality(if (isM3u8) "HLS (HxFile)" else "Direct (HxFile)", streamUrl, isM3u8, streamHeaders))
            }

            // 3. Unpack Dean Edwards packed JavaScript
            if (qualities.isEmpty()) {
                val evalRegex = Regex("""eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k[\s\S]*?\)\s*\)""")
                for (evalMatch in evalRegex.findAll(html)) {
                    val unpacked = JsPackerUnpacker.unpack(evalMatch.value) ?: continue
                    for (m in fileRegex.findAll(unpacked)) {
                        val streamUrl = m.groupValues[1].replace("\\/", "/")
                        val isM3u8 = streamUrl.contains(".m3u8")
                        qualities.add(VideoQuality(if (isM3u8) "HLS (HxFile)" else "Direct (HxFile)", streamUrl, isM3u8, streamHeaders))
                    }
                    if (qualities.isNotEmpty()) break
                }
            }

            // 4. Fallback: Parse form download
            if (qualities.isEmpty()) {
                val form = doc.selectFirst("form[action*='hxfile'], form")
                if (form != null) {
                    val formAction = form.absUrl("action").ifBlank { url }
                    val formBuilder = FormBody.Builder()
                    form.select("input[name]").forEach { input ->
                        formBuilder.add(input.attr("name"), input.attr("value"))
                    }
                    val postReq = Request.Builder()
                        .url(formAction)
                        .post(formBuilder.build())
                        .header("User-Agent", NetworkModule.userAgent)
                        .header("Referer", url)
                        .build()

                    val postRes = client.newCall(postReq).execute()
                    if (postRes.isSuccessful) {
                        val postHtml = postRes.body?.string().orEmpty()
                        for (m in fileRegex.findAll(postHtml)) {
                            val streamUrl = m.groupValues[1].replace("\\/", "/")
                            val isM3u8 = streamUrl.contains(".m3u8")
                            qualities.add(VideoQuality(if (isM3u8) "HLS (HxFile)" else "Direct (HxFile)", streamUrl, isM3u8, streamHeaders))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        qualities.distinctBy { it.url }
    }
}
