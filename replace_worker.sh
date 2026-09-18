#!/bin/bash
cat << 'INNER' > app/src/main/java/com/example/workers/VideoDownloadWorker.kt
package com.example.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.OtakuHubApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.firstOrNull

class VideoDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val client = com.example.data.NetworkModule.okHttpClient

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sourceId = inputData.getString("sourceId") ?: return@withContext Result.failure()
        val animeId = inputData.getString("animeId") ?: return@withContext Result.failure()
        val episodeId = inputData.getString("episodeId") ?: return@withContext Result.failure()
        
        val app = applicationContext as OtakuHubApp
        val db = app.database
        val repo = app.repository
        val downloadDao = db.downloadDao()
        
        var download = downloadDao.getDownload(sourceId, animeId, episodeId)
            ?: return@withContext Result.failure()

        download = download.copy(state = "DOWNLOADING", startedAt = System.currentTimeMillis())
        downloadDao.updateDownload(download)

        try {
            setForeground(createForegroundInfo(0, 100, "Resolving video stream..."))
            
            // Resolve stream
            val streamInfo = repo.getVideoStream(episodeId, sourceId).firstOrNull { 
                it.qualities.isNotEmpty() || it.embedUrl.isNotEmpty() 
            }
            
            if (streamInfo == null) {
                download = download.copy(state = "ERROR", errorMessage = "Failed to resolve stream")
                downloadDao.updateDownload(download)
                return@withContext Result.failure()
            }
            
            val targetUrl = streamInfo.bestQuality?.url ?: streamInfo.embedUrl
            if (targetUrl.isEmpty()) {
                download = download.copy(state = "ERROR", errorMessage = "Stream URL is empty")
                downloadDao.updateDownload(download)
                return@withContext Result.failure()
            }

            // Create ExoPlayer DownloadRequest
            val downloadId = "${sourceId}||${animeId}||${episodeId}"
            val uri = android.net.Uri.parse(targetUrl)
            
            val downloadRequest = androidx.media3.exoplayer.offline.DownloadRequest.Builder(
                downloadId,
                uri
            ).build()

            // Send to DownloadService
            androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
                applicationContext,
                VideoDownloadService::class.java,
                downloadRequest,
                false
            )

            // Result.success() because the actual downloading is delegated to Media3's DownloadService
            Result.success()
        } catch (e: CancellationException) {
            download = download.copy(state = "CANCELLED")
            downloadDao.updateDownload(download)
            throw e
        } catch (e: Exception) {
            download = download.copy(state = "ERROR", errorMessage = e.message)
            downloadDao.updateDownload(download)
            Result.retry()
        }
    }

    private fun createForegroundInfo(progress: Int, max: Int, message: String): ForegroundInfo {
        val id = "video_download_channel"
        val title = "Downloading Video"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(id, title, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, id)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(max, progress, false)
            .build()
            
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id.hashCode(), notification)
        }
    }
}
INNER
