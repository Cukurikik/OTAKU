import sys

new_content = """package com.example.workers

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
import com.example.data.db.PageDownloadEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ChapterDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val client = com.example.data.NetworkModule.okHttpClient

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sourceId = inputData.getString("sourceId") ?: return@withContext Result.failure()
        val mangaId = inputData.getString("mangaId") ?: return@withContext Result.failure()
        val chapterId = inputData.getString("chapterId") ?: return@withContext Result.failure()
        val passedBaseUrl = inputData.getString("baseUrl")

        val app = applicationContext as OtakuHubApp
        val db = app.database
        val repo = app.repository
        val downloadDao = db.downloadDao()
        val pageDownloadDao = db.pageDownloadDao()
        val chapterDao = db.chapterDao()

        var download = downloadDao.getDownload(sourceId, mangaId, chapterId)
            ?: return@withContext Result.failure()
            
        // Mark as running
        download = download.copy(state = "DOWNLOADING", startedAt = System.currentTimeMillis())
        downloadDao.updateDownload(download)
        
        try {
            setForeground(createForegroundInfo(0, 100, "Initializing..."))

            // Fetch pages with timeout to avoid infinite collection
            var pages = emptyList<com.example.data.models.Page>()
            try {
                kotlinx.coroutines.withTimeout(20000) {
                    pages = repo.getPages(chapterId, sourceId).first { it.isNotEmpty() }
                }
            } catch (e: Exception) {
                // If first fails, fallback to standard collect
                try {
                    repo.getPages(chapterId, sourceId).collect { p ->
                        if (p.isNotEmpty()) {
                            pages = p
                        }
                    }
                } catch (ex: Exception) {
                    android.util.Log.e("ChapterDownloadWorker", "Failed to fetch pages: ${ex.message}")
                }
            }
            
            if (pages.isEmpty()) {
                download = download.copy(state = "ERROR", errorMessage = "No pages found")
                downloadDao.updateDownload(download)
                return@withContext Result.failure()
            }
            
            // Convert to page entities
            val pageEntities = pages.mapIndexed { index, page ->
                PageDownloadEntity(
                    sourceId = sourceId,
                    mangaId = mangaId,
                    chapterId = chapterId,
                    pageIndex = index,
                    remoteUrl = page.url
                )
            }
            pageDownloadDao.insertPages(pageEntities)
            
            val mangaDir = File(applicationContext.filesDir, "downloads/$sourceId/$mangaId")
            if (!mangaDir.exists()) {
                mangaDir.mkdirs()
            }
            
            val stagingDir = File(mangaDir, "tmp_$chapterId")
            if (!stagingDir.exists()) {
                stagingDir.mkdirs()
            }
            
            val cbzFile = File(mangaDir, "$chapterId.cbz")
            
            // Download loop
            var allSuccess = true
            for ((index, page) in pageEntities.withIndex()) {
                setForeground(createForegroundInfo(index, pageEntities.size, "Downloading page ${index + 1}/${pageEntities.size}"))
                
                val fileName = "${String.format("%03d", index)}.jpg"
                val file = File(stagingDir, fileName)
                
                if (file.exists() && file.length() > 0) {
                    continue
                }
                
                val tempFile = File(stagingDir, "$fileName.tmp")
                
                var baseUrl = passedBaseUrl ?: page.remoteUrl
                if (baseUrl == page.remoteUrl) {
                    val source = repo.sources.find { it.sourceId == sourceId }
                    if (source != null) {
                        try {
                            val m = source.javaClass.getMethod("getBaseUrl")
                            val b = m.invoke(source) as? String
                            if (b != null) baseUrl = b
                        } catch (e: Exception) {
                            try {
                                val f = source.javaClass.getDeclaredField("baseUrl")
                                f.isAccessible = true
                                val b = f.get(source) as? String
                                if (b != null) baseUrl = b
                            } catch (ex: Exception) {}
                        }
                    }
                }
                
                var success = false
                try {
                    val request = Request.Builder()
                        .url(page.remoteUrl)
                        .header("Referer", baseUrl)
                        .header("User-Agent", com.example.data.NetworkModule.userAgent)
                        .build()
                        
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.byteStream()?.use { input ->
                                FileOutputStream(tempFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (tempFile.renameTo(file)) {
                                success = true
                            }
                        } else if (response.code == 403 || response.code == 503) {
                            // Retry with worker
                            val workerUrl = "https://anime-aggregator.njirlah.workers.dev/?url=" + java.net.URLEncoder.encode(page.remoteUrl, "UTF-8")
                            val workerReq = Request.Builder()
                                .url(workerUrl)
                                .header("Authorization", "Bearer cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4")
                                .header("User-Agent", com.example.data.NetworkModule.userAgent)
                                .build()
                            client.newCall(workerReq).execute().use { workerRes ->
                                if (workerRes.isSuccessful) {
                                    workerRes.body?.byteStream()?.use { input ->
                                        FileOutputStream(tempFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    if (tempFile.renameTo(file)) {
                                        success = true
                                    }
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("ChapterDownloadWorker", "Page failed: ${e.message}")
                }
                
                if (!success) {
                    allSuccess = false
                    break
                }
            }
            
            if (!allSuccess) {
                // Clean up staging dir
                stagingDir.deleteRecursively()
                download = download.copy(state = "ERROR", errorMessage = "Failed to download all pages")
                downloadDao.updateDownload(download)
                return@withContext Result.retry()
            }
            
            // Atomically create CBZ
            setForeground(createForegroundInfo(pageEntities.size, pageEntities.size, "Compressing to CBZ..."))
            
            val tempCbzFile = File(mangaDir, "$chapterId.cbz.tmp")
            try {
                ZipOutputStream(FileOutputStream(tempCbzFile)).use { zout ->
                    stagingDir.listFiles()?.sortedBy { it.name }?.forEach { file ->
                        if (file.name.endsWith(".jpg")) {
                            FileInputStream(file).use { fis ->
                                val entry = ZipEntry(file.name)
                                zout.putNextEntry(entry)
                                fis.copyTo(zout)
                                zout.closeEntry()
                            }
                        }
                    }
                }
                
                if (tempCbzFile.renameTo(cbzFile)) {
                    // Success, clean up staging
                    stagingDir.deleteRecursively()
                    
                    // Update database
                    pageEntities.forEachIndexed { index, page ->
                        val fileName = "${String.format("%03d", index)}.jpg"
                        pageDownloadDao.updatePage(
                            page.copy(
                                state = "DOWNLOADED",
                                localUri = "cbz://${cbzFile.absolutePath}::$fileName",
                                bytes = cbzFile.length() / pageEntities.size // approximate
                            )
                        )
                    }
                    
                    download = download.copy(state = "DOWNLOADED", completedAt = System.currentTimeMillis())
                    downloadDao.updateDownload(download)
                    
                    val chapterEntry = chapterDao.getChapter(sourceId, mangaId, chapterId)
                    if (chapterEntry != null) {
                        chapterDao.updateChapter(chapterEntry.copy(downloaded = true))
                    }
                    
                    return@withContext Result.success()
                } else {
                    throw Exception("Failed to rename CBZ file")
                }
            } catch (e: Exception) {
                tempCbzFile.delete()
                stagingDir.deleteRecursively()
                download = download.copy(state = "ERROR", errorMessage = "Failed to create CBZ: ${e.message}")
                downloadDao.updateDownload(download)
                return@withContext Result.retry()
            }
        } catch (e: CancellationException) {
            download = download.copy(state = "CANCELLED")
            downloadDao.updateDownload(download)
            throw e
        } catch (e: Exception) {
            download = download.copy(state = "ERROR", errorMessage = e.message)
            downloadDao.updateDownload(download)
            return@withContext Result.retry()
        }
    }

    private fun createForegroundInfo(progress: Int, max: Int, message: String): ForegroundInfo {
        val id = "download_channel"
        val title = "Downloading Chapter"
        
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
"""

with open('app/src/main/java/com/example/workers/ChapterDownloadWorker.kt', 'w') as f:
    f.write(new_content)
