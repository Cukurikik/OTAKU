package com.example.download.muxing

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import com.example.data.NetworkModule
import com.example.media.CacheDataSourceFactory
import com.example.media.PersistentHttpHeaderResolver

/**
 * ExoPlayerDownloadHelperFactory:
 * Factory for creating ExoPlayer Media3 DownloadHelper and DownloadRequest instances
 * with custom headers, adaptive track selection, and segmented caching integration.
 */
@OptIn(UnstableApi::class)
object ExoPlayerDownloadHelperFactory {

    /**
     * Creates a DownloadHelper configured for HLS / DASH or progressive media streaming.
     */
    fun createDownloadHelper(
        context: Context,
        mediaUri: Uri,
        headers: Map<String, String> = emptyMap(),
        mimeType: String? = null
    ): DownloadHelper {
        val resolvedMime = mimeType ?: when {
            mediaUri.toString().contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            mediaUri.toString().contains(".mpd") -> MimeTypes.APPLICATION_MPD
            else -> MimeTypes.VIDEO_MP4
        }

        val mediaItem = MediaItem.Builder()
            .setUri(mediaUri)
            .setMimeType(resolvedMime)
            .build()

        val allHeaders = HashMap(headers)
        val persistentHeaders = PersistentHttpHeaderResolver.resolveHeadersForRequest(
            requestUri = mediaUri,
            activeStreamUrl = mediaUri.toString()
        )
        persistentHeaders.forEach { (k, v) -> allHeaders[k] = v }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(NetworkModule.userAgent)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(allHeaders)

        return DownloadHelper.forMediaItem(
            context,
            mediaItem,
            null, // default renderers factory
            httpDataSourceFactory
        )
    }

    /**
     * Creates a DownloadRequest for persistent background downloading.
     */
    fun createDownloadRequest(
        contentId: String,
        mediaUri: Uri,
        mimeType: String? = null,
        customData: ByteArray? = null
    ): DownloadRequest {
        val resolvedMime = mimeType ?: when {
            mediaUri.toString().contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            mediaUri.toString().contains(".mpd") -> MimeTypes.APPLICATION_MPD
            else -> MimeTypes.VIDEO_MP4
        }

        return DownloadRequest.Builder(contentId, mediaUri)
            .setMimeType(resolvedMime)
            .setData(customData)
            .build()
    }
}
