package com.example.download

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

/**
 * FFmpegExportWorker:
 * Background worker yang mengeksekusi stream copying (-c copy) FFmpeg 
 * tanpa membebani CPU, serta mendaftarkan hasil ekspor MP4 ke Galeri Sistem Android.
 */
class FFmpegExportWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "FFmpegExportWorker"
        private const val CHANNEL_ID = "ffmpeg_export_channel"
        private const val NOTIFICATION_ID = 2005
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val inputPath = inputData.getString("INPUT_PATH")
            ?: return@withContext Result.failure(workDataOf("ERROR" to "Input path tidak ditemukan"))
        val outputTitle = inputData.getString("OUTPUT_TITLE") ?: "Video_Export_${System.currentTimeMillis()}"
        val notificationTitle = inputData.getString("TITLE") ?: outputTitle

        try {
            setForeground(createForegroundInfo(notificationTitle, "Mengekspor video ke Galeri Sistem (MP4)..."))

            val (success, message) = FFmpegExporterEngine.exportToGalleryMp4(context, inputPath, outputTitle)

            if (success) {
                Log.i(TAG, "Berhasil mengekspor video: $message")
                Result.success(workDataOf("MESSAGE" to message))
            } else {
                Log.e(TAG, "Gagal mengekspor video: $message")
                Result.failure(workDataOf("ERROR" to message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pada FFmpegExportWorker: ${e.message}", e)
            Result.failure(workDataOf("ERROR" to (e.message ?: "Kesalahan ekspor tidak diketahui")))
        }
    }

    private fun createForegroundInfo(title: String, statusText: String): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ekspor Galeri FFmpeg",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Mengekspor: $title")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, 50, true)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
