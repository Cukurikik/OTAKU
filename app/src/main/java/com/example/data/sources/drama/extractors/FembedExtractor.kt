package com.example.data.sources.drama.extractors

import android.net.Uri
import com.example.data.NetworkModule
import com.example.data.sources.VideoQuality
import com.example.data.sources.anime.extractors.ServerExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

/**
 * Dedicated Extractor for Fembed / Feurl / Dutrag / Fcdn clones.
 *
 * Mechanism:
 * 1. Extract video ID from URL.
 * 2. POST to /api/source/{id}.
 * 3. Parse JSON qualities array.
 */
class FembedExtractor : ServerExtractor {
    override val name = "Fembed"
    override val mainUrl = "fembed"

    override suspend fun extract(url: String): List<VideoQuality> = withContext(Dispatchers.IO) {
        val qualities = mutableListOf<VideoQuality>()
        try {
            val client = NetworkModule.okHttpClient
            val parsedUri = Uri.parse(url)
            val host = parsedUri.host ?: "fembed.com"
            val videoId = parsedUri.lastPathSegment ?: return@withContext emptyList()

            val apiUrl = "https://$host/api/source/$videoId"
            val streamHeaders = mapOf(
                "User-Agent" to NetworkModule.userAgent,
                "Referer" to url,
                "Origin" to "https://$host"
            )

            val postReq = Request.Builder()
                .url(apiUrl)
                .post(FormBody.Builder().add("r", "").add("d", host).build())
                .header("User-Agent", NetworkModule.userAgent)
                .header("Referer", url)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val response = client.newCall(postReq).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val jsonStr = response.body?.string().orEmpty()

            val json = JSONObject(jsonStr)
            if (json.optBoolean("success", false)) {
                val dataArr = json.optJSONArray("data")
                if (dataArr != null) {
                    for (i in 0 until dataArr.length()) {
                        val obj = dataArr.optJSONObject(i) ?: continue
                        val file = obj.optString("file")
                        if (file.isNotBlank()) {
                            val label = obj.optString("label", "720p")
                            val isM3u8 = file.contains(".m3u8")
                            qualities.add(VideoQuality("$label (Fembed)", file, isM3u8, streamHeaders))
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
