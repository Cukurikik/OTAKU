package com.example.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ComicDownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "comic_download_channel"

    override suspend fun doWork(): Result {
        val imageUrls = inputData.getStringArray("IMAGE_URLS") ?: return Result.failure(workDataOf("ERROR" to "No image URLs provided"))
        val chapterTitle = inputData.getString("CHAPTER_TITLE") ?: "Chapter"
        val mangaTitle = inputData.getString("MANGA_TITLE") ?: "Manga"
        
        // Clean title for file name
        val safeChapterTitle = chapterTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val safeMangaTitle = mangaTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        return try {
            setForeground(createForegroundInfo(chapterTitle, 0, imageUrls.size))

            val settings = com.example.data.SettingsManager(context)
            val useSecureStorage = settings.galleryIsolationEnabled || settings.fileObfuscationEnabled

            val downloadsDir = if (useSecureStorage) {
                com.example.storage.AppStorageDirectoryConfig.getSecureMangaDir(context, "comics", safeMangaTitle)
            } else {
                File(context.getExternalFilesDir(null), "comics/$safeMangaTitle").apply { mkdirs() }
            }

            val cbzFile = File(downloadsDir, "$safeChapterTitle.cbz")
            
            withContext(Dispatchers.IO) {
                if (useSecureStorage) {
                    val entries = mutableMapOf<String, ByteArray>()
                    imageUrls.forEachIndexed { index, url ->
                        val imageBytes = fetchImageBytes(url)
                        if (imageBytes.isNotEmpty()) {
                            val entryName = String.format("page_%03d.dat", index + 1)
                            entries[entryName] = imageBytes
                        }
                        delay(100)
                        setForeground(createForegroundInfo(chapterTitle, index + 1, imageUrls.size))
                    }
                    com.example.storage.secure.SecureEncryptedMediaStorage(context).createEncryptedArchive(cbzFile, entries)
                } else {
                    ZipOutputStream(BufferedOutputStream(FileOutputStream(cbzFile))).use { zipOut ->
                        imageUrls.forEachIndexed { index, url ->
                            val imageBytes = fetchImageBytes(url)
                            if (imageBytes.isNotEmpty()) {
                                val entry = ZipEntry(String.format("page_%03d.jpg", index + 1))
                                zipOut.putNextEntry(entry)
                                zipOut.write(imageBytes)
                                zipOut.closeEntry()
                            }
                            
                            // Small delay to prevent rate-limiting
                            delay(100)

                            // Update progress
                            setForeground(createForegroundInfo(chapterTitle, index + 1, imageUrls.size))
                        }
                    }
                }
            }
            Result.success(workDataOf("OUTPUT_PATH" to cbzFile.absolutePath))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(workDataOf("ERROR" to e.localizedMessage))
        }
    }

    private fun createForegroundInfo(title: String, progress: Int, max: Int): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Comic Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Downloading Comic: $title")
            .setContentText("Downloading pages...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(max, progress, false)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(1002, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(1002, notification)
        }
    }

    private suspend fun fetchImageBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkModule.userAgent)
                .build()
            val response = NetworkModule.okHttpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                response.body?.bytes() ?: ByteArray(0)
            } else {
                ByteArray(0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ByteArray(0)
        }
    }
}
