package com.example.media

import android.content.Context
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import com.example.OtakuHubApp
import com.example.data.downloads.ExoDownloadManager as CoreExoDownloadManager

/**
 * Media Layer ExoDownloadManager providing access to ExoPlayer DownloadManager,
 * cache instances, and resilient execution of background media downloads.
 */
object ExoDownloadManager {

    @Synchronized
    fun getManager(context: Context): DownloadManager =
        CoreExoDownloadManager.getManager(context)

    fun getCache(context: Context): SimpleCache =
        CoreExoDownloadManager.getCache(context)

    /**
     * Executes direct video download with exponential backoff compatibility.
     */
    suspend fun executeDownload(
        downloadUrl: String,
        contentId: String,
        context: Context = OtakuHubApp.instance
    ): Boolean = CoreExoDownloadManager.executeDownload(downloadUrl, contentId, context)
}
