package com.example.data.sources.anime.extractors

import com.example.data.NetworkModule
import com.example.data.sources.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BloggerExtractor : ServerExtractor {
    override val name = "Blogger"
    override val mainUrl = "blogger.com"

    override suspend fun extract(url: String): List<VideoQuality> = withContext(Dispatchers.IO) {
        val qualities = mutableListOf<VideoQuality>()
        try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .build()
            
            val response = NetworkModule.okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                
                // Try to find the VIDEO_CONFIG JSON in Blogger
                // "streams":[{"play_url":"https://...","format_id":18}...]
                val streamsRegex = "\"streams\":\\[(.*?)\\]".toRegex()
                val match = streamsRegex.find(body)
                
                if (match != null) {
                    val streamsJson = match.groupValues[1]
                    val urlRegex = "\"play_url\":\"(.*?)\"".toRegex()
                    val formatRegex = "\"format_id\":(\\d+)".toRegex()
                    
                    val urls = urlRegex.findAll(streamsJson).map { it.groupValues[1] }.toList()
                    val formats = formatRegex.findAll(streamsJson).map { it.groupValues[1] }.toList()
                    
                    for (i in urls.indices) {
                        val playUrl = urls[i].replace("\\u0026", "&")
                        val formatId = if (i < formats.size) formats[i] else ""
                        
                        val resolution = when (formatId) {
                            "18" -> "360p"
                            "22" -> "720p"
                            "37" -> "1080p"
                            else -> "Lokal/Raw"
                        }
                        
                        qualities.add(VideoQuality(resolution, playUrl, false, com.example.data.sources.VideoExtractorEngine.createHeaders(url)))
                    }
                } else {
                    // Fallback to simple regex for mp4
                    val urlRegex = "(https?:[^\"]+\\.mp4[^\"]*)".toRegex()
                    val fallbackMatch = urlRegex.find(body)
                    if (fallbackMatch != null) {
                        qualities.add(VideoQuality("Lokal/Raw", fallbackMatch.groupValues[1].replace("\\u0026", "&"), false, com.example.data.sources.VideoExtractorEngine.createHeaders(url)))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        qualities.reversed() // 1080p, 720p, 360p
    }
}
