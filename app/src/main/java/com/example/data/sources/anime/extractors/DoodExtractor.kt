package com.example.data.sources.anime.extractors

import android.net.Uri
import com.example.data.NetworkModule
import com.example.data.sources.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Dedicated Extractor for DoodStream and its mirrors:
 * doodstream.com, dood.to, dood.so, dood.ws, dood.sh, dood.la, ds2play.com, dood.li, dood.pm, etc.
 *
 * Mechanism:
 * 1. Fetch iframe page.
 * 2. Find pass_md5 endpoint: /pass_md5/...
 * 3. Fetch token from /pass_md5/... with Referer.
 * 4. Generate random string and append ?token=...&expiry=...
 */
class DoodExtractor : ServerExtractor {
    override val name = "DoodStream"
    override val mainUrl = "dood"

    override suspend fun extract(url: String): List<VideoQuality> = withContext(Dispatchers.IO) {
        val qualities = mutableListOf<VideoQuality>()
        try {
            val client = NetworkModule.okHttpClient
            val parsedUri = Uri.parse(url)
            val host = parsedUri.host ?: "doodstream.com"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkModule.userAgent)
                .header("Referer", url)
                .build()

            val response = client.newCall(req).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val html = response.body?.string().orEmpty()

            val passRegex = Regex("""/pass_md5/[^"'\]+""")
            val pass = passRegex.find(html)?.value ?: return@withContext emptyList()
            val md5Url = "https://$host$pass"

            val md5Req = Request.Builder()
                .url(md5Url)
                .header("User-Agent", NetworkModule.userAgent)
                .header("Referer", url)
                .build()

            val md5Resp = client.newCall(md5Req).execute()
            if (!md5Resp.isSuccessful) return@withContext emptyList()
            val md5Result = md5Resp.body?.string().orEmpty().trim()
            if (md5Result.isBlank()) return@withContext emptyList()

            val tokenRegex = Regex("""[?&]token=([^&"']+)""")
            val token = tokenRegex.find(html)?.groupValues?.get(1)
                ?: Regex(""""token"[ :]+"([^"]+)"""").find(html)?.groupValues?.get(1)
                ?: ""

            val timestamp = System.currentTimeMillis() / 1000
            val randomString = (1..10).map { ('a'..'z').random() }.joinToString("")
            val directStreamUrl = "$md5Result$randomString?token=$token&expiry=$timestamp"

            val headers = mapOf(
                "Referer" to "https://$host/",
                "Origin" to "https://$host",
                "User-Agent" to NetworkModule.userAgent
            )

            qualities.add(VideoQuality("Auto (DoodStream)", directStreamUrl, false, headers))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        qualities
    }
}
