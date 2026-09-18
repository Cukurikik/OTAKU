package com.example.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.data.NetworkModule
import com.example.data.VideoCache
import com.example.data.downloads.ExoDownloadManager
import com.example.data.sources.Subtitle
import com.example.data.torrent.TorrentDataSource
import com.example.data.torrent.TorrentStreamManager

/**
 * Aspect ratio display modes for ExoPlayer PlayerView.
 */
enum class AspectRatioMode(val label: String, val resizeMode: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ZOOM("Crop (Zoom)", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FILL("Stretch (Fill)", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    FIXED_16_9("16:9", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
    FIXED_4_3("4:3", AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT)
}

/**
 * High-performance ExoPlayer Manager handling MediaItem construction with
 * external subtitle configurations (.vtt, .srt, .ass), DRM license resolution,
 * bandwidth metering, and resilient caching.
 */
@OptIn(UnstableApi::class)
object ExoPlayerManager {

    /**
     * Determines the appropriate subtitle MIME type from URL or format string.
     */
    fun inferSubtitleMimeType(urlOrFormat: String): String {
        val lower = urlOrFormat.lowercase()
        return when {
            lower.endsWith(".vtt") || lower.contains(".vtt?") || lower == "vtt" || lower == "webvtt" -> MimeTypes.TEXT_VTT
            lower.endsWith(".srt") || lower.contains(".srt?") || lower == "srt" || lower == "subrip" -> MimeTypes.APPLICATION_SUBRIP
            lower.endsWith(".ass") || lower.contains(".ass?") || lower == "ass" ||
            lower.endsWith(".ssa") || lower.contains(".ssa?") || lower == "ssa" -> MimeTypes.TEXT_SSA
            else -> MimeTypes.TEXT_VTT
        }
    }

    /**
     * Injeksi Subtitle Eksternal pada MediaItem tunggal.
     */
    fun createMediaItemWithSubtitles(
        videoUrl: String,
        subtitleUrl: String?,
        mimeType: String = MimeTypes.TEXT_VTT
    ): MediaItem {
        val mediaItemBuilder = MediaItem.Builder().setUri(videoUrl)

        if (!subtitleUrl.isNullOrEmpty()) {
            val resolvedMime = if (mimeType == MimeTypes.TEXT_VTT && !subtitleUrl.endsWith(".vtt")) {
                inferSubtitleMimeType(subtitleUrl)
            } else {
                mimeType
            }
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                .setMimeType(resolvedMime)
                .setLanguage("id")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        return mediaItemBuilder.build()
    }

    /**
     * Injeksi daftar Subtitle eksternal, DRM konfigurasi, dan MediaMetadata.
     */
    fun createMediaItemWithSubtitles(
        videoUrl: String,
        subtitles: List<Subtitle>,
        drmLicenseUrl: String? = null,
        drmHeaders: Map<String, String> = emptyMap(),
        mediaMetadata: MediaMetadata? = null
    ): MediaItem {
        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(videoUrl))

        if (mediaMetadata != null) {
            mediaItemBuilder.setMediaMetadata(mediaMetadata)
        }

        if (subtitles.isNotEmpty()) {
            val configs = subtitles.mapIndexed { index, sub ->
                val mime = inferSubtitleMimeType(sub.format.ifEmpty { sub.url })
                val isDefault = index == 0 || sub.language.contains("id", ignoreCase = true) || sub.language.contains("indonesia", ignoreCase = true)
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                    .setMimeType(mime)
                    .setLanguage(sub.language.ifEmpty { "id" })
                    .setLabel(sub.language.ifEmpty { "Subtitle ${index + 1}" })
                    .setSelectionFlags(if (isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            }
            mediaItemBuilder.setSubtitleConfigurations(configs)
        }

        if (!drmLicenseUrl.isNullOrEmpty()) {
            val drmConfig = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                .setLicenseUri(drmLicenseUrl)
            if (drmHeaders.isNotEmpty()) {
                drmConfig.setLicenseRequestHeaders(drmHeaders)
            }
            mediaItemBuilder.setDrmConfiguration(drmConfig.build())
        }

        return mediaItemBuilder.build()
    }

    /**
     * Builds a configured ExoPlayer instance with custom cache, bandwidth meter, and track selector.
     */
    fun buildPlayer(
        context: Context,
        trackSelector: DefaultTrackSelector,
        bandwidthMeter: BandwidthMeter,
        headersProvider: () -> Map<String, String>,
        streamUrlProvider: () -> String,
        initialSpeed: Float = 1.0f,
        listener: Player.Listener? = null
    ): ExoPlayer {
        val httpDataSourceFactory = OkHttpDataSource.Factory(NetworkModule.okHttpClient)
            .setUserAgent(NetworkModule.userAgent)

        val resolvingDataSourceFactory = PersistentResolvingDataSourceFactory.create(
            upstreamFactory = httpDataSourceFactory,
            headersProvider = headersProvider,
            streamUrlProvider = streamUrlProvider
        )

        val streamCache = VideoCache.getInstance(context)
        val streamDataSourceFactory = CacheDataSource.Factory()
            .setCache(streamCache)
            .setUpstreamDataSourceFactory(resolvingDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val downloadCache = ExoDownloadManager.getCache(context)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(streamDataSourceFactory)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val delegatingDataSourceFactory = DataSource.Factory {
            if (streamUrlProvider() == "torrent://stream") {
                TorrentDataSource(TorrentStreamManager)
            } else {
                cacheDataSourceFactory.createDataSource()
            }
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 50000, 2500, 5000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(delegatingDataSourceFactory))
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .build().apply {
                setPlaybackSpeed(initialSpeed)
                if (listener != null) {
                    addListener(listener)
                }
            }
    }

    /**
     * Toggles Audio-Only Mode on ExoPlayer by disabling or enabling the video track.
     * When audio-only is enabled, video frames are not decoded/rendered, saving >= 80% bandwidth
     * and reducing battery consumption significantly for background or screen-off listening.
     */
    fun setAudioOnlyMode(
        player: Player,
        trackSelector: DefaultTrackSelector,
        enabled: Boolean
    ) {
        val trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, enabled)
            .build()
        player.trackSelectionParameters = trackSelectionParameters

        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, enabled)
        )
    }

    /**
     * Calculates estimated bandwidth savings percentage when switching to audio-only.
     * Efficiency = ((Bitrate Video + Bitrate Audio - Bitrate Audio) / (Bitrate Video + Bitrate Audio)) * 100%
     */
    fun calculateBandwidthSavings(videoBitrateBps: Long, audioBitrateBps: Long): Float {
        val total = videoBitrateBps + audioBitrateBps
        if (total <= 0L) return 80.0f
        val savings = (videoBitrateBps.toFloat() / total.toFloat()) * 100f
        return savings.coerceIn(0f, 99f)
    }
}
