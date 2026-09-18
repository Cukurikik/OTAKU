package com.example.workers

import android.content.Context
import androidx.work.*
import com.example.data.SettingsManager
import com.example.data.db.ChapterDao
import com.example.data.db.DownloadDao
import com.example.data.models.ContentType
import com.example.data.models.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File

object DownloadAutomator {
    suspend fun processAutomations(
        context: Context,
        settings: SettingsManager,
        chapterDao: ChapterDao,
        downloadDao: DownloadDao,
        sourceId: String,
        itemId: String,
        currentChapterId: String,
        contentType: ContentType
    ) {
        withContext(Dispatchers.IO) {
            // Auto Delete Read
            if (settings.autoDeleteRead) {
                val download = downloadDao.getDownload(com.example.data.ProfileManager.currentProfileIdValue, sourceId, itemId, currentChapterId)
                if (download != null) {
                    val cbzFile = File(context.filesDir, "downloads/$sourceId/$itemId/$currentChapterId.cbz")
                    if (cbzFile.exists()) cbzFile.delete()
                    val stagingDir = File(context.filesDir, "downloads/$sourceId/$itemId/tmp_$currentChapterId")
                    if (stagingDir.exists()) stagingDir.deleteRecursively()
                    val chapterDir = File(context.filesDir, "downloads/$sourceId/$itemId/$currentChapterId")
                    if (chapterDir.exists()) {
                        chapterDir.deleteRecursively()
                    }
                    downloadDao.deleteDownload(download)
                    try {
                        (context.applicationContext as? com.example.OtakuHubApp)?.database?.pageDownloadDao()
                            ?.deletePagesByChapter(sourceId, itemId, currentChapterId)
                    } catch (e: Exception) {}
                    val chapterEntry = chapterDao.getChapter(com.example.data.ProfileManager.currentProfileIdValue, sourceId, itemId, currentChapterId)
                    if (chapterEntry != null) {
                        chapterDao.updateChapter(chapterEntry.copy(downloaded = false))
                    }
                }
            }

            // Auto Download Next
            val downloadNextCount = settings.autoDownloadNext
            if (downloadNextCount > 0) {
                val allChapters = chapterDao.getChapters(com.example.data.ProfileManager.currentProfileIdValue, sourceId, itemId).firstOrNull() ?: emptyList()
                if (allChapters.size > 1) {
                    val firstNum = allChapters.first().name.filter { it.isDigit() }.toIntOrNull() ?: 0
                    val lastNum = allChapters.last().name.filter { it.isDigit() }.toIntOrNull() ?: 0
                    val isAscending = firstNum < lastNum
                    
                    val currentIndex = allChapters.indexOfFirst { it.chapterId == currentChapterId }
                    if (currentIndex != -1) {
                        val nextChapters = mutableListOf<com.example.data.models.ChapterEntry>()
                        var i = if (isAscending) currentIndex + 1 else currentIndex - 1
                        val step = if (isAscending) 1 else -1
                        
                        while (i in allChapters.indices && nextChapters.size < downloadNextCount) {
                            val ch = allChapters[i]
                            if (!ch.read && !ch.downloaded) {
                                nextChapters.add(ch)
                            }
                            i += step
                        }
                        
                        nextChapters.forEach { nextCh ->
                            enqueueDownload(context, settings, downloadDao, sourceId, itemId, nextCh.chapterId, contentType)
                        }
                    }
                }
            }
        }
    }
    
    private suspend fun enqueueDownload(
        context: Context,
        settings: SettingsManager,
        downloadDao: DownloadDao,
        sourceId: String,
        itemId: String,
        chapterId: String,
        contentType: ContentType
    ) {
        val existing = downloadDao.getDownload(com.example.data.ProfileManager.currentProfileIdValue, sourceId, itemId, chapterId)
        if (existing != null) return
        
        val download = DownloadEntity(
            sourceId = sourceId,
            mangaId = itemId,
            chapterId = chapterId,
            state = "QUEUE",
            priority = 0
        )
        downloadDao.insertDownload(download)
        
        var baseUrl = ""
        val app = context.applicationContext as? com.example.OtakuHubApp
        if (app != null) {
            val source = app.repository.sources.find { it.sourceId == sourceId }
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
        
        val data = Data.Builder()
            .putString("sourceId", sourceId)
            .putString("baseUrl", baseUrl)
            .putString("mangaId", itemId)
            .putString("animeId", itemId)
            .putString("episodeId", chapterId)
            .putString("chapterId", chapterId)
            .build()
            
        val constraintsBuilder = Constraints.Builder()
            .setRequiredNetworkType(if (settings.autoDownloadOnlyOnWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            
        val workerClass = if (contentType == ContentType.ANIME || contentType == ContentType.DRAMA) {
            VideoDownloadWorker::class.java
        } else {
            MangaDownloadWorker::class.java
        }
        
        val request = OneTimeWorkRequest.Builder(workerClass)
            .setConstraints(constraintsBuilder.build())
            .setInputData(data)
            .build()
            
        WorkManager.getInstance(context).enqueueUniqueWork(
            "download_${sourceId}_${itemId}_${chapterId}",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
