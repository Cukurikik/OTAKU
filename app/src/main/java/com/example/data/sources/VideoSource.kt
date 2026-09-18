package com.example.data.sources

import com.example.data.models.Page
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Interface khusus untuk source video (anime/drama).
 * Extends ContentSource dengan kemampuan video streaming.
 */
interface VideoSource : ContentSource {
    override val supportsVideo: Boolean get() = true
    
    /**
     * Ambil URL stream video (M3U8, MP4, atau embed URL)
     * @param episodeId URL atau ID episode
     * @return VideoStreamInfo dengan semua kualitas yang tersedia
     */
    fun getVideoStream(episodeId: String): Flow<VideoStreamInfo>
    
    // Default impl untuk kompatibilitas
    override fun getVideoStreamUrl(episodeId: String): Flow<String> =
        getVideoStream(episodeId).map { it.bestQuality?.url ?: it.embedUrl }
    
    // Anime tidak punya "pages" dalam arti komik
    override fun getPages(chapterId: String): Flow<List<Page>> = flowOf(emptyList())
}

data class VideoStreamInfo(
    val episodeId: String = "",
    val title: String = "",
    val qualities: List<VideoQuality> = emptyList(),
    val subtitles: List<Subtitle> = emptyList(),
    val embedUrl: String = "",
    val headers: Map<String, String> = emptyMap(),
    val servers: List<VideoServer> = emptyList(),
    val drmLicenseUrl: String? = null,
    val drmHeaders: Map<String, String> = emptyMap()
) {
    val url: String get() = bestQuality?.url ?: embedUrl
    val quality: String get() = bestQuality?.qualityLabel ?: "Auto"
    val provider: String get() = servers.firstOrNull()?.name ?: ""

    constructor(
        url: String,
        quality: String = "Auto",
        provider: String = "",
        episodeId: String = "",
        embedUrl: String = "",
        headers: Map<String, String> = emptyMap()
    ) : this(
        episodeId = episodeId,
        embedUrl = embedUrl.ifBlank { url },
        qualities = listOf(VideoQuality(quality, url, url.contains(".m3u8"), headers)),
        headers = headers,
        servers = if (provider.isNotBlank()) listOf(VideoServer(provider, embedUrl.ifBlank { url }, listOf(VideoQuality(quality, url, url.contains(".m3u8"), headers)), emptyList(), headers)) else emptyList()
    )

    val bestQuality: VideoQuality? get() = qualities.maxByOrNull { it.qualityLabel.extractNumber() }
    val defaultQuality: VideoQuality? get() = qualities.firstOrNull { it.qualityLabel == "720p" } ?: bestQuality
}

data class VideoServer(
    val name: String,
    val embedUrl: String,
    val qualities: List<VideoQuality> = emptyList(),
    val subtitles: List<Subtitle> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val drmLicenseUrl: String? = null,
    val drmHeaders: Map<String, String> = emptyMap()
)

data class VideoQuality(
    val qualityLabel: String, // "1080p", "720p", "480p", "360p"
    val url: String,
    val isM3U8: Boolean = url.contains(".m3u8"),
    val headers: Map<String, String> = emptyMap(),
    val drmLicenseUrl: String? = null,
    val drmHeaders: Map<String, String> = emptyMap()
)

data class Subtitle(
    val language: String,
    val url: String,
    val format: String = "vtt" // "vtt", "srt", "ass"
)

private fun String.extractNumber(): Int = filter { it.isDigit() }.toIntOrNull() ?: 0
