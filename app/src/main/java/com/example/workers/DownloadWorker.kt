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
import com.example.media.ExoDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resilient DownloadWorker utilizing WorkManager constraints and exponential backoff retry.
 */
class DownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val KEY_DOWNLOAD_URL = "KEY_DOWNLOAD_URL"
        const val KEY_CONTENT_ID = "KEY_CONTENT_ID"
        const val KEY_SOURCE_ID = "KEY_SOURCE_ID"
        const val KEY_MANGA_ID = "KEY_MANGA_ID"
        const val KEY_CHAPTER_ID = "KEY_CHAPTER_ID"
        const val MAX_RETRIES = 5
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL) ?: inputData.getString("downloadUrl")
        val contentId = inputData.getString(KEY_CONTENT_ID) ?: inputData.getString("contentId")

        if (downloadUrl.isNullOrEmpty() || contentId.isNullOrEmpty()) {
            return@withContext Result.failure()
        }

        try {
            setForeground(createForegroundInfo("Mengunduh media secara latar belakang..."))
        } catch (e: Exception) {
            // Foreground info set may be ignored in certain test or restricted background environments
        }

        return@withContext try {
            val success = ExoDownloadManager.executeDownload(downloadUrl, contentId, applicationContext)
            if (success) {
                Result.success()
            } else {
                if (runAttemptCount < MAX_RETRIES) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun createForegroundInfo(message: String): ForegroundInfo {
        val channelId = "resilient_download_channel"
        val title = "Background Download Engine"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Resilient Background Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                channelId.hashCode(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(channelId.hashCode(), notification)
        }
    }
}
