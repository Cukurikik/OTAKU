package com.example.download.compressor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Background WorkManager Worker untuk mengeksekusi kompresi offline video HEVC/H.265.
 */
class VideoCompressionWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "VideoCompressionWorker"
        const val CHANNEL_ID = "video_compressor_channel"
        private const val NOTIFICATION_ID = 2026

        const val KEY_INPUT_PATH = "INPUT_PATH"
        const val KEY_PROFILE = "PROFILE"
        const val KEY_DOWNLOAD_ID = "DOWNLOAD_ID"
        const val KEY_TITLE = "TITLE"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val inputPath = inputData.getString(KEY_INPUT_PATH)
            ?: return@withContext Result.failure(workDataOf("ERROR" to "Input path tidak valid"))
        val profileName = inputData.getString(KEY_PROFILE) ?: CompressionProfile.SPACE_SAVER.name
        val title = inputData.getString(KEY_TITLE) ?: "Video Offline"
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: inputPath

        val profile = try {
            CompressionProfile.valueOf(profileName)
        } catch (e: Exception) {
            CompressionProfile.SPACE_SAVER
        }

        val inputFile = File(inputPath)
        if (!inputFile.exists()) {
            return@withContext Result.failure(workDataOf("ERROR" to "File video tidak ditemukan"))
        }

        try {
            setForeground(createForegroundInfo(title, "Mengompresi video ke ${profile.codecBadge}..."))

            val engine = VideoCompressorEngine(context)
            val result = engine.compressVideo(inputFile, profile)

            if (result.success) {
                StorageSpaceSaverManager.recordCompression(
                    context = context,
                    downloadId = downloadId,
                    filePath = inputFile.absolutePath,
                    profile = profile,
                    originalBytes = result.originalBytes,
                    compressedBytes = result.compressedBytes
                )

                showCompletionNotification(title, result)
                Result.success(
                    workDataOf(
                        "MESSAGE" to result.message,
                        "SAVED_BYTES" to result.bytesSaved,
                        "PERCENTAGE" to result.savingsPercentage
                    )
                )
            } else {
                Result.failure(workDataOf("ERROR" to result.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saat kompresi latar belakang: ${e.message}", e)
            Result.failure(workDataOf("ERROR" to (e.message ?: "Kesalahan tidak diketahui")))
        }
    }

    private fun createForegroundInfo(title: String, statusText: String): ForegroundInfo {
        createChannelIfNeeded()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Hemat Ruang: $title")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, 50, true)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun showCompletionNotification(title: String, result: CompressionResult) {
        createChannelIfNeeded()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Kompresi Video Berhasil!")
            .setContentText("$title • Hemat %.1f%% (${formatBytes(result.bytesSaved)})".format(result.savingsPercentage))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kompresi Video Hemat Ruang",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024f * 1024f)
        val gb = mb / 1024f
        return if (gb >= 1f) "%.2f GB".format(gb) else "%.1f MB".format(mb)
    }
}
