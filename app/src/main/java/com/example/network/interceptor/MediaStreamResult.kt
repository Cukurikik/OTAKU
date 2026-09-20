package com.example.network.interceptor

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.data.sources.Subtitle
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoStreamInfo
import com.example.media.ExoPlayerManager

/**
 * Result model from network/webview interception capturing both video streams and external subtitles.
 */
data class MediaStreamResult(
    val videoUrl: String = "",
    val qualities: List<VideoQuality> = emptyList(),
    val subtitles: List<Subtitle> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val embedUrl: String = "",
    val title: String = "",
    val drmLicenseUrl: String? = null,
    val drmHeaders: Map<String, String> = emptyMap()
) {
    val bestQuality: VideoQuality? get() = qualities.maxByOrNull { it.qualityLabel.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 } ?: qualities.firstOrNull()
    val url: String get() = videoUrl.ifBlank { bestQuality?.url ?: embedUrl }

    /**
     * Converts to ExoPlayer MediaItem with external SubtitleConfiguration and DRM support.
     */
    fun toMediaItem(mediaMetadata: MediaMetadata? = null): MediaItem {
        return ExoPlayerManager.createMediaItemWithSubtitles(
            videoUrl = url,
            subtitles = subtitles,
            drmLicenseUrl = drmLicenseUrl,
            drmHeaders = drmHeaders,
            mediaMetadata = mediaMetadata
        )
    }

    /**
     * Converts to VideoStreamInfo for UI layers and repositories.
     */
    fun toVideoStreamInfo(episodeId: String = ""): VideoStreamInfo {
        return VideoStreamInfo(
            episodeId = episodeId,
            title = title,
            qualities = qualities.ifEmpty {
                if (videoUrl.isNotBlank()) listOf(VideoQuality("Auto", videoUrl, isM3U8 = videoUrl.contains(".m3u8"), headers = headers)) else emptyList()
            },
            subtitles = subtitles,
            embedUrl = embedUrl.ifBlank { videoUrl },
            headers = headers,
            drmLicenseUrl = drmLicenseUrl,
            drmHeaders = drmHeaders
        )
    }
}

/**
 * Extension to convert VideoStreamInfo to MediaStreamResult.
 */
fun VideoStreamInfo.toMediaStreamResult(): MediaStreamResult {
    return MediaStreamResult(
        videoUrl = this.url,
        qualities = this.qualities,
        subtitles = this.subtitles,
        headers = this.headers,
        embedUrl = this.embedUrl,
        title = this.title,
        drmLicenseUrl = this.drmLicenseUrl,
        drmHeaders = this.drmHeaders
    )
}
