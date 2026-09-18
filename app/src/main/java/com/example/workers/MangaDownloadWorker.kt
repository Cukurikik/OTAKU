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
import com.example.data.models.PageDownloadEntity
import com.example.storage.StoragePathManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MangaDownloadWorker(
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
        val profileId = com.example.data.ProfileManager.currentProfileIdValue

        var download = downloadDao.getDownload(profileId, sourceId, mangaId, chapterId)
            ?: return@withContext Result.failure()
            
        // Mark as running
        download = download.copy(state = "DOWNLOADING", startedAt = download.startedAt ?: System.currentTimeMillis())
        downloadDao.updateDownload(download)
        
        val mangaDir = com.example.storage.StoragePathManager.getMangaDownloadDir(applicationContext, sourceId, mangaId)
        com.example.storage.StoragePathManager.ensureNoMedia(mangaDir)

        val cbzFile = File(mangaDir, "$chapterId.cbz")
        val stagingDir = File(mangaDir, "tmp_$chapterId")
        if (!stagingDir.exists()) {
            stagingDir.mkdirs()
        }
        com.example.storage.StoragePathManager.ensureNoMedia(stagingDir)

        val settings = com.example.data.SettingsManager(applicationContext)
        val useObfuscation = settings.fileObfuscationEnabled
        val pageExtension = if (useObfuscation) "dat" else "jpg"

        // Fast path: If CBZ is already intact and complete
        if (cbzFile.exists() && cbzFile.length() > 1024) {
            download = download.copy(state = "DOWNLOADED", completedAt = System.currentTimeMillis())
            downloadDao.updateDownload(download)
            val chapterEntry = chapterDao.getChapter(profileId, sourceId, mangaId, chapterId)
            if (chapterEntry != null) {
                chapterDao.updateChapter(chapterEntry.copy(downloaded = true))
            }
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
            }
            return@withContext Result.success()
        }

        try {
            setForeground(createForegroundInfo(0, 100, "Evaluating download cache..."))

            // Retrieve existing page records from DB if available
            val existingDbPages = pageDownloadDao.getPagesList(sourceId, mangaId, chapterId)
                .associateBy { it.pageIndex }

            // Fetch remote page list if not yet recorded in DB
            val pageEntities: List<PageDownloadEntity>
            if (existingDbPages.isNotEmpty()) {
                pageEntities = existingDbPages.values.sortedBy { it.pageIndex }
            } else {
                var pages = emptyList<com.example.data.models.Page>()
                try {
                    kotlinx.coroutines.withTimeout(20000) {
                        pages = repo.getPages(chapterId, sourceId).first { it.isNotEmpty() }
                    }
                } catch (e: Exception) {
                    try {
                        repo.getPages(chapterId, sourceId).collect { p ->
                            if (p.isNotEmpty()) {
                                pages = p
                            }
                        }
                    } catch (ex: Exception) {
                        android.util.Log.e("MangaDownloadWorker", "Failed to fetch pages: ${ex.message}")
                    }
                }

                if (pages.isEmpty()) {
                    download = download.copy(state = "ERROR", errorMessage = "No pages found for chapter")
                    downloadDao.updateDownload(download)
                    return@withContext Result.failure()
                }

                pageEntities = pages.mapIndexed { index, page ->
                    PageDownloadEntity(
                        profileId = profileId,
                        sourceId = sourceId,
                        mangaId = mangaId,
                        chapterId = chapterId,
                        pageIndex = index,
                        remoteUrl = page.url,
                        state = "QUEUE"
                    )
                }
                pageDownloadDao.insertPages(pageEntities)
            }

            // Determine baseUrl for HTTP Referer
            var baseUrl = passedBaseUrl
            if (baseUrl.isNullOrEmpty()) {
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

            var allSuccess = true
            var failedIndex = -1

            // Download loop with Disk Cache Evaluation & Per-Page Auto-Retry
            for ((index, pageEntity) in pageEntities.withIndex()) {
                val baseNum = String.format("%03d", index)
                val fileName = "$baseNum.$pageExtension"
                val file = File(stagingDir, fileName)
                val legacyJpgFile = File(stagingDir, "$baseNum.jpg")
                val legacyDatFile = File(stagingDir, "$baseNum.dat")

                val existingValidFile = when {
                    file.exists() && file.length() > 512 -> file
                    legacyJpgFile.exists() && legacyJpgFile.length() > 512 -> legacyJpgFile
                    legacyDatFile.exists() && legacyDatFile.length() > 512 -> legacyDatFile
                    else -> null
                }

                // 1. EVALUATE LOCAL DISK CACHE: If file already exists and is non-empty (>512 bytes), skip download!
                if (existingValidFile != null) {
                    if (pageEntity.state != "DOWNLOADED" || pageEntity.localUri != existingValidFile.absolutePath || pageEntity.bytes != existingValidFile.length()) {
                        pageDownloadDao.updatePage(
                            pageEntity.copy(
                                state = "DOWNLOADED",
                                localUri = existingValidFile.absolutePath,
                                bytes = existingValidFile.length()
                            )
                        )
                    }
                    setForeground(createForegroundInfo(index + 1, pageEntities.size, "Page ${index + 1}/${pageEntities.size} (cached)"))
                    continue
                }

                // If not cached, proceed to download page
                setForeground(createForegroundInfo(index, pageEntities.size, "Downloading page ${index + 1}/${pageEntities.size}"))
                pageDownloadDao.updatePage(pageEntity.copy(state = "DOWNLOADING"))

                val tempFile = File(stagingDir, "$fileName.tmp")
                val pageReferer = baseUrl ?: pageEntity.remoteUrl
                var pageSuccess = false
                val MAX_PAGE_RETRIES = 3

                for (attempt in 1..MAX_PAGE_RETRIES) {
                    try {
                        if (attempt > 1) {
                            delay(500L * (1L shl (attempt - 1))) // Exponential backoff: 500ms, 1000ms, 2000ms
                        }

                        var directSuccess = false
                        val request = Request.Builder()
                            .url(pageEntity.remoteUrl)
                            .header("Referer", pageReferer)
                            .header("User-Agent", com.example.data.NetworkModule.userAgent)
                            .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful && response.body != null) {
                                response.body!!.byteStream().use { input ->
                                    FileOutputStream(tempFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                if (tempFile.exists() && tempFile.length() > 512) {
                                    directSuccess = true
                                } else {
                                    tempFile.delete()
                                }
                            } else if (response.code in listOf(403, 429, 503)) {
                                directSuccess = false
                            }
                        }

                        // Fallback to Cloudflare Worker proxy if direct download fails or is blocked by Cloudflare Turnstile/WAF
                        if (!directSuccess) {
                            val workerUrl = "https://anime-aggregator.njirlah.workers.dev/?url=" + java.net.URLEncoder.encode(pageEntity.remoteUrl, "UTF-8")
                            val workerReq = Request.Builder()
                                .url(workerUrl)
                                .header("Authorization", "Bearer cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4")
                                .header("User-Agent", com.example.data.NetworkModule.userAgent)
                                .build()
                            client.newCall(workerReq).execute().use { workerRes ->
                                if (workerRes.isSuccessful && workerRes.body != null) {
                                    workerRes.body!!.byteStream().use { input ->
                                        FileOutputStream(tempFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    if (tempFile.exists() && tempFile.length() > 512) {
                                        directSuccess = true
                                    } else {
                                        tempFile.delete()
                                    }
                                }
                            }
                        }

                        if (directSuccess && tempFile.renameTo(file)) {
                            pageSuccess = true
                            pageDownloadDao.updatePage(
                                pageEntity.copy(
                                    state = "DOWNLOADED",
                                    localUri = file.absolutePath,
                                    bytes = file.length()
                                )
                            )
                            break
                        }
                    } catch (e: CancellationException) {
                        tempFile.delete()
                        throw e
                    } catch (e: Exception) {
                        tempFile.delete()
                        android.util.Log.e("MangaDownloadWorker", "Page $index attempt $attempt failed: ${e.message}")
                    }
                }

                if (!pageSuccess) {
                    pageDownloadDao.updatePage(pageEntity.copy(state = "ERROR"))
                    allSuccess = false
                    failedIndex = index
                    break
                }
            }

            if (!allSuccess) {
                // DO NOT DELETE STAGING DIR! Keep all successfully downloaded pages on disk!
                val completedCount = pageDownloadDao.getPagesList(sourceId, mangaId, chapterId).count { it.state == "DOWNLOADED" }
                download = download.copy(
                    state = "ERROR",
                    errorMessage = "Failed downloading page ${failedIndex + 1}/${pageEntities.size} ($completedCount/${pageEntities.size} pages saved)"
                )
                downloadDao.updateDownload(download)
                return@withContext Result.retry()
            }

            // Verify all pages exist before creating CBZ
            val allFilesExist = (0 until pageEntities.size).all { i ->
                val baseNum = String.format("%03d", i)
                val f1 = File(stagingDir, "$baseNum.$pageExtension")
                val f2 = File(stagingDir, "$baseNum.jpg")
                val f3 = File(stagingDir, "$baseNum.dat")
                (f1.exists() && f1.length() > 512) || (f2.exists() && f2.length() > 512) || (f3.exists() && f3.length() > 512)
            }

            if (!allFilesExist) {
                download = download.copy(state = "ERROR", errorMessage = "Some pages are incomplete or missing")
                downloadDao.updateDownload(download)
                return@withContext Result.retry()
            }

            // Atomically bundle to CBZ
            setForeground(createForegroundInfo(pageEntities.size, pageEntities.size, "Compressing to CBZ archive..."))
            val tempCbzFile = File(mangaDir, "$chapterId.cbz.tmp")
            try {
                ZipOutputStream(FileOutputStream(tempCbzFile)).use { zout ->
                    stagingDir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                        val lower = f.name.lowercase()
                        val isImage = (lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".dat") || lower.endsWith(".bin") || lower.endsWith(".webp")) && f.length() > 0
                        if (isImage && f.name != StoragePathManager.NO_MEDIA_FILE_NAME) {
                            FileInputStream(f).use { fis ->
                                val entry = ZipEntry(f.name)
                                zout.putNextEntry(entry)
                                fis.copyTo(zout)
                                zout.closeEntry()
                            }
                        }
                    }
                }

                if (!tempCbzFile.exists() || tempCbzFile.length() < 1024) {
                    throw Exception("Generated CBZ is corrupted or empty")
                }

                if (tempCbzFile.renameTo(cbzFile)) {
                    // Only delete staging directory after CBZ is safely created and verified
                    stagingDir.deleteRecursively()

                    // Update all pages to point to CBZ entries
                    val totalCbzLength = cbzFile.length()
                    val avgBytes = if (pageEntities.isNotEmpty()) totalCbzLength / pageEntities.size else 0L
                    pageEntities.forEachIndexed { index, p ->
                        val pFileName = "${String.format("%03d", index)}.$pageExtension"
                        pageDownloadDao.updatePage(
                            p.copy(
                                state = "DOWNLOADED",
                                localUri = "cbz://${cbzFile.absolutePath}::$pFileName",
                                bytes = avgBytes
                            )
                        )
                    }

                    download = download.copy(
                        state = "DOWNLOADED",
                        errorMessage = null,
                        completedAt = System.currentTimeMillis()
                    )
                    downloadDao.updateDownload(download)

                    val chapterEntry = chapterDao.getChapter(profileId, sourceId, mangaId, chapterId)
                    if (chapterEntry != null) {
                        chapterDao.updateChapter(chapterEntry.copy(downloaded = true))
                    }

                    return@withContext Result.success()
                } else {
                    throw Exception("Failed to finalize CBZ file")
                }
            } catch (e: Exception) {
                tempCbzFile.delete()
                // Do not delete stagingDir so pages are preserved for next attempt
                download = download.copy(state = "ERROR", errorMessage = "Failed to bundle CBZ: ${e.message}")
                downloadDao.updateDownload(download)
                return@withContext Result.retry()
            }
        } catch (e: CancellationException) {
            download = download.copy(state = "PAUSED")
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
