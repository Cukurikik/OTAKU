package com.example.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.data.sources.Subtitle
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.drama.DramaSubtitleExtractorEngine
import com.example.network.interceptor.MediaStreamResult

/**
 * Standardized factory for building Media3 [MediaItem] instances with robust external multi-language subtitle tracks,
 * DRM Widevine configurations, and metadata injection.
 */
object ExoPlayerMediaItemFactory {

    /**
     * Builds a [MediaItem] configured with multi-track external subtitles and DRM options.
     */
    fun createMediaItem(
        videoUri: String,
        subtitles: List<Subtitle> = emptyList(),
        preferredLanguage: String = "id",
        drmLicenseUrl: String? = null,
        drmHeaders: Map<String, String> = emptyMap(),
        mediaMetadata: MediaMetadata? = null
    ): MediaItem {
        val uri = Uri.parse(videoUri)
        val builder = MediaItem.Builder().setUri(uri)

        if (mediaMetadata != null) {
            builder.setMediaMetadata(mediaMetadata)
        }

        // Subtitle configurations via DramaSubtitleExtractorEngine
        if (subtitles.isNotEmpty()) {
            val subtitleConfigs = DramaSubtitleExtractorEngine.buildSubtitleConfigurations(
                subtitles = subtitles,
                preferredLanguageCode = preferredLanguage
            )
            builder.setSubtitleConfigurations(subtitleConfigs)
        }

        // DRM Configuration
        if (!drmLicenseUrl.isNullOrBlank()) {
            val drmConfig = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                .setLicenseUri(drmLicenseUrl)
            if (drmHeaders.isNotEmpty()) {
                drmConfig.setLicenseRequestHeaders(drmHeaders)
            }
            builder.setDrmConfiguration(drmConfig.build())
        }

        return builder.build()
    }

    /**
     * Builds a [MediaItem] optimized for HLS streaming with segment caching hints.
     */
    fun createHlsMediaItem(
        hlsUri: String,
        subtitles: List<Subtitle> = emptyList(),
        preferredLanguage: String = "id",
        drmLicenseUrl: String? = null,
        drmHeaders: Map<String, String> = emptyMap(),
        mediaMetadata: MediaMetadata? = null,
        customCacheKey: String? = null
    ): MediaItem {
        val uri = Uri.parse(hlsUri)
        val builder = MediaItem.Builder()
            .setUri(uri)
            .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)

        if (customCacheKey != null) {
            builder.setCustomCacheKey(customCacheKey)
        }

        if (mediaMetadata != null) {
            builder.setMediaMetadata(mediaMetadata)
        }

        if (subtitles.isNotEmpty()) {
            val subtitleConfigs = DramaSubtitleExtractorEngine.buildSubtitleConfigurations(
                subtitles = subtitles,
                preferredLanguageCode = preferredLanguage
            )
            builder.setSubtitleConfigurations(subtitleConfigs)
        }

        if (!drmLicenseUrl.isNullOrBlank()) {
            val drmConfig = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                .setLicenseUri(drmLicenseUrl)
            if (drmHeaders.isNotEmpty()) {
                drmConfig.setLicenseRequestHeaders(drmHeaders)
            }
            builder.setDrmConfiguration(drmConfig.build())
        }

        return builder.build()
    }

    /**
     * Builds an adaptive [MediaItem] automatically detecting HLS (.m3u8), DASH (.mpd), or progressive MP4.
     */
    fun createAdaptiveMediaItem(
        videoUri: String,
        subtitles: List<Subtitle> = emptyList(),
        preferredLanguage: String = "id",
        drmLicenseUrl: String? = null,
        drmHeaders: Map<String, String> = emptyMap(),
        mediaMetadata: MediaMetadata? = null
    ): MediaItem {
        val isHls = videoUri.contains(".m3u8", ignoreCase = true)
        val isDash = videoUri.contains(".mpd", ignoreCase = true)
        val deterministicKey = HlsSegmentCacheProxy.buildDeterministicCacheKey(videoUri)

        val uri = Uri.parse(videoUri)
        val builder = MediaItem.Builder().setUri(uri)

        when {
            isHls -> builder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
            isDash -> builder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
        }

        builder.setCustomCacheKey(deterministicKey)

        if (mediaMetadata != null) {
            builder.setMediaMetadata(mediaMetadata)
        }

        if (subtitles.isNotEmpty()) {
            val subtitleConfigs = DramaSubtitleExtractorEngine.buildSubtitleConfigurations(
                subtitles = subtitles,
                preferredLanguageCode = preferredLanguage
            )
            builder.setSubtitleConfigurations(subtitleConfigs)
        }

        if (!drmLicenseUrl.isNullOrBlank()) {
            val drmConfig = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                .setLicenseUri(drmLicenseUrl)
            if (drmHeaders.isNotEmpty()) {
                drmConfig.setLicenseRequestHeaders(drmHeaders)
            }
            builder.setDrmConfiguration(drmConfig.build())
        }

        return builder.build()
    }

    /**
     * Builds a [MediaItem] directly from a [VideoStreamInfo].
     */
    fun createMediaItemFromStreamInfo(
        streamInfo: VideoStreamInfo,
        preferredLanguage: String = "id",
        mediaMetadata: MediaMetadata? = null
    ): MediaItem {
        val targetUrl = streamInfo.bestQuality?.url
            ?: streamInfo.qualities.firstOrNull()?.url
            ?: streamInfo.embedUrl

        return createMediaItem(
            videoUri = targetUrl,
            subtitles = streamInfo.subtitles,
            preferredLanguage = preferredLanguage,
            drmLicenseUrl = streamInfo.drmLicenseUrl,
            drmHeaders = streamInfo.drmHeaders,
            mediaMetadata = mediaMetadata
        )
    }

    /**
     * Builds a [MediaItem] from a [MediaStreamResult].
     */
    fun createMediaItemFromInterception(
        result: MediaStreamResult,
        preferredLanguage: String = "id",
        mediaMetadata: MediaMetadata? = null
    ): MediaItem {
        return createMediaItem(
            videoUri = result.url,
            subtitles = result.subtitles,
            preferredLanguage = preferredLanguage,
            drmLicenseUrl = result.drmLicenseUrl,
            drmHeaders = result.drmHeaders,
            mediaMetadata = mediaMetadata
        )
    }
}
