package com.example.data.sources.anime.extractors

import com.example.data.sources.VideoQuality

interface ServerExtractor {
    val name: String
    val mainUrl: String
    
    suspend fun extract(url: String): List<VideoQuality>
}
