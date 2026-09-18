package com.example.data.sources.anime.extractors

import android.net.Uri
import com.example.data.NetworkModule
import com.example.data.sources.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Dedicated Extractor for Gofile (gofile.io).
 *
 * Characteristics:
 * - Domain: gofile.io
 * - API-based: /contents/{contentId}?wt=4fd6sg89d7s6
 * - Returns direct download link in data.children[...].link
 */
class GofileExtractor : ServerExtractor {
    override val name = "Gofile"
    override val mainUrl = "gofile.io"

    override suspend fun extract(url: String): List<VideoQuality> = withContext(Dispatchers.IO) {
        val qualities = mutableListOf<VideoQuality>()
        try {
            val client = NetworkModule.okHttpClient
            val parsedUri = Uri.parse(url)
            val contentId = parsedUri.lastPathSegment ?: return@withContext emptyList()

            // Gofile contents API endpoint
            val apiUrl = "https://api.gofile.io/contents/$contentId?wt=4fd6sg89d7s6"
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", NetworkModule.userAgent)
                .header("Referer", url)
                .build()

            val response = client.newCall(req).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val status = json.optString("status")
                if (status == "ok") {
                    val data = json.optJSONObject("data")
                    val children = data?.optJSONObject("children")
                    if (children != null) {
                        val keys = children.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val child = children.optJSONObject(key)
                            val link = child?.optString("link")
                            val name = child?.optString("name") ?: "Video"
                            if (!link.isNullOrBlank()) {
                                qualities.add(
                                    VideoQuality(
                                        qualityLabel = "Gofile ($name)",
                                        url = link,
                                        isM3U8 = link.contains(".m3u8"),
                                        headers = mapOf("User-Agent" to NetworkModule.userAgent)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        qualities
    }
}
