package com.example.data.sources.anime.extractors

import com.example.data.NetworkModule
import com.example.data.sources.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StreamtapeExtractor : ServerExtractor {
    override val name = "Streamtape"
    override val mainUrl = "streamtape.com"

    override suspend fun extract(url: String): List<VideoQuality> = withContext(Dispatchers.IO) {
        val qualities = mutableListOf<VideoQuality>()
        try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .build()
            
            val response = NetworkModule.okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                
                // Streamtape obfuscates the URL in script tags. It looks something like:
                // document.getElementById('norobotlink').innerHTML = '//streamtape.com/get_video?...'
                val scriptRegex = "document\\.getElementById\\('norobotlink'\\)\\.innerHTML\\s*=\\s*'(.*?)'\\s*\\+\\s*'(.*?)'".toRegex()
                val match = scriptRegex.find(body)
                
                if (match != null) {
                    var streamUrl = match.groupValues[1] + match.groupValues[2]
                    if (streamUrl.startsWith("//")) {
                        streamUrl = "https:$streamUrl"
                    }
                    qualities.add(VideoQuality("Lokal/Raw", streamUrl, false, com.example.data.sources.VideoExtractorEngine.createHeaders(url)))
                } else {
                    // Alternative regex
                    val altRegex = "document\\.getElementById\\('ideoolink'\\)\\.innerHTML\\s*=\\s*\"(.*?)\"".toRegex()
                    val altMatch = altRegex.find(body)
                    if (altMatch != null) {
                        var streamUrl = altMatch.groupValues[1]
                        if (streamUrl.startsWith("//")) {
                            streamUrl = "https:$streamUrl"
                        }
                        qualities.add(VideoQuality("Lokal/Raw", streamUrl, false, com.example.data.sources.VideoExtractorEngine.createHeaders(url)))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        qualities
    }
}
