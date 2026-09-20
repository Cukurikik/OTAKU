package com.example.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import com.example.OtakuHubApp
import com.example.data.downloads.ExoDownloadManager as CoreExoDownloadManager

/**
 * Media Layer ExoDownloadManager providing access to ExoPlayer DownloadManager,
 * cache instances, and resilient execution of background media downloads.
 */
@OptIn(UnstableApi::class)
object ExoDownloadManager {

    @Synchronized
    fun getManager(context: Context): DownloadManager =
        CoreExoDownloadManager.getManager(context)

    fun getCache(context: Context): SimpleCache =
        CoreExoDownloadManager.getCache(context)

    fun buildDownloadRequest(
        mediaUri: Uri,
        headers: Map<String, String> = emptyMap(),
        contentId: String = mediaUri.toString(),
        mimeType: String? = null
    ): DownloadRequest =
        CoreExoDownloadManager.buildDownloadRequest(mediaUri, headers, contentId, mimeType)

    fun serializeHeaders(headers: Map<String, String>): ByteArray =
        CoreExoDownloadManager.serializeHeaders(headers)

    fun deserializeHeaders(data: ByteArray?): Map<String, String> =
        CoreExoDownloadManager.deserializeHeaders(data)

    /**
     * Executes direct video download with exponential backoff compatibility.
     */
    suspend fun executeDownload(
        downloadUrl: String,
        contentId: String,
        context: Context = OtakuHubApp.instance,
        headers: Map<String, String> = emptyMap()
    ): Boolean = CoreExoDownloadManager.executeDownload(downloadUrl, contentId, context, headers)
}

