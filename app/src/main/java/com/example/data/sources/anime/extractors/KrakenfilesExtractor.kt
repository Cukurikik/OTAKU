package com.example.data.sources.anime.extractors

import android.net.Uri
import com.example.data.NetworkModule
import com.example.data.sources.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

/**
 * Dedicated Extractor for KrakenFiles (krakenfiles.com).
 *
 * Characteristics:
 * - Domain: krakenfiles.com
 * - Form/token submission: requires fetching token and hash from page and calling /download/{id}
 * - Direct video stream response containing direct URL.
 */
class KrakenfilesExtractor : ServerExtractor {
    override val name = "KrakenFiles"
    override val mainUrl = "krakenfiles.com"

    override suspend fun extract(url: String): List<VideoQuality> = withContext(Dispatchers.IO) {
        val qualities = mutableListOf<VideoQuality>()
        try {
            val client = NetworkModule.okHttpClient
            val parsedUri = Uri.parse(url)
            val host = parsedUri.host ?: "krakenfiles.com"

            val streamHeaders = mapOf(
                "User-Agent" to NetworkModule.userAgent,
                "Referer" to url
            )

            // Step 1: Fetch download page
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkModule.userAgent)
                .header("Referer", url)
                .build()

            val response = client.newCall(req).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val html = response.body?.string().orEmpty()

            // Check if there is an in-page <video src="..."> or <source src="...">
            val videoSrcMatch = Regex("""<video[^>]+src=["']([^"']+)["']""").find(html)
                ?: Regex("""<source[^>]+src=["']([^"']+)["']""").find(html)

            if (videoSrcMatch != null) {
                var streamUrl = videoSrcMatch.groupValues[1]
                if (streamUrl.startsWith("//")) streamUrl = "https:$streamUrl"
                qualities.add(VideoQuality("Direct (KrakenFiles)", streamUrl, false, streamHeaders))
                return@withContext qualities
            }

            // Step 2: Extract download hash/token for /download/ endpoint
            val tokenMatch = Regex("""name=["']token["']\s+value=["']([^"']+)["']""").find(html)
                ?: Regex("""data-token=["']([^"']+)["']""").find(html)
            val hashMatch = Regex("""/download/([a-zA-Z0-9]+)""").find(html)

            val token = tokenMatch?.groupValues?.get(1)
            val hash = hashMatch?.groupValues?.get(1) ?: parsedUri.lastPathSegment

            if (!token.isNullOrBlank() && !hash.isNullOrBlank()) {
                val postUrl = "https://$host/download/$hash"
                val formBody = FormBody.Builder()
                    .add("token", token)
                    .build()

                val downloadReq = Request.Builder()
                    .url(postUrl)
                    .post(formBody)
                    .header("User-Agent", NetworkModule.userAgent)
                    .header("Referer", url)
                    .build()

                val dlResp = client.newCall(downloadReq).execute()
                if (dlResp.isSuccessful) {
                    val respBody = dlResp.body?.string().orEmpty()
                    runCatching {
                        val json = JSONObject(respBody)
                        val dlUrl = json.optString("url")
                        if (dlUrl.isNotBlank()) {
                            qualities.add(VideoQuality("Direct (KrakenFiles)", dlUrl, false, streamHeaders))
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
