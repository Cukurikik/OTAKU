package com.example.workers

import android.content.Context
import android.util.Log
import com.example.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CacheJanitor {
    private const val TAG = "CacheJanitor"
    const val DEFAULT_MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L // 24 hours

    /**
     * Cleans orphan staging directories in downloads and cache.
     * All file traversal and disk I/O operations are explicitly executed on Dispatchers.IO to prevent ANR.
     *
     * @param context Application context
     * @param activeDownloadChapterIds Set of chapterIds currently in QUEUE or DOWNLOADING state
     * @param maxAgeMillis Threshold age after which temporary files are considered expired
     * @return Number of cleaned staging directories/files
     */
    suspend fun cleanOrphanStagingDirectories(
        context: Context,
        activeDownloadChapterIds: Set<String> = emptySet(),
        maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS
    ): Int = withContext(Dispatchers.IO) {
        var cleanedCount = 0
        val thresholdTime = System.currentTimeMillis() - maxAgeMillis

        try {
            // 1. Clean Manga downloads staging directories: filesDir/downloads/{sourceId}/{mangaId}/tmp_{chapterId}
            val downloadsBaseDir = File(context.filesDir, "downloads")
            if (downloadsBaseDir.exists() && downloadsBaseDir.isDirectory) {
                downloadsBaseDir.listFiles()?.forEach { sourceDir ->
                    if (sourceDir.isDirectory) {
                        sourceDir.listFiles()?.forEach { mangaDir ->
                            if (mangaDir.isDirectory) {
                                mangaDir.listFiles()?.forEach { item ->
                                    if (item.isDirectory && item.name.startsWith("tmp_")) {
                                        val chapterId = item.name.removePrefix("tmp_")
                                        val isExpired = item.lastModified() < thresholdTime
                                        val isNotActive = chapterId !in activeDownloadChapterIds

                                        if (isExpired || isNotActive) {
                                            Log.d(TAG, "Removing orphan staging directory: ${item.absolutePath} (expired=$isExpired, notActive=$isNotActive)")
                                            if (item.deleteRecursively()) {
                                                cleanedCount++
                                            }
                                        }
                                    } else if (item.isFile && item.name.endsWith(".tmp")) {
                                        // Leftover single temp files
                                        if (item.lastModified() < thresholdTime || item.name.substringBefore(".tmp") !in activeDownloadChapterIds) {
                                            if (item.delete()) {
                                                cleanedCount++
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Clean cacheDir/stagingDir if present
            val cacheStagingDir = File(context.cacheDir, "stagingDir")
            if (cacheStagingDir.exists() && cacheStagingDir.isDirectory) {
                cacheStagingDir.listFiles()?.forEach { dir ->
                    val isExpired = dir.lastModified() < thresholdTime
                    val isNotActive = dir.name !in activeDownloadChapterIds && dir.name.removePrefix("tmp_") !in activeDownloadChapterIds

                    if (isExpired || isNotActive) {
                        Log.d(TAG, "Removing cache staging directory: ${dir.absolutePath}")
                        if (dir.deleteRecursively()) {
                            cleanedCount++
                        }
                    }
                }
            }

            // 3. Clean temporary play URL tokens in cacheDir/play_urls
            val playUrlsDir = File(context.cacheDir, "play_urls")
            if (playUrlsDir.exists() && playUrlsDir.isDirectory) {
                playUrlsDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.lastModified() < thresholdTime) {
                        if (file.delete()) {
                            cleanedCount++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while cleaning staging directories: ${e.message}", e)
        }

        cleanedCount
    }

    /**
     * Asynchronous helper to perform full cleanup with database verification.
     * Explicitly executed on Dispatchers.IO.
     */
    suspend fun performCleanup(
        context: Context,
        db: AppDatabase,
        maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS
    ): Int = withContext(Dispatchers.IO) {
        try {
            val activeDownloads = db.downloadDao().getActiveDownloadsAllProfiles()
            val activeChapterIds = activeDownloads.map { it.chapterId }.toSet()
            val cleaned = cleanOrphanStagingDirectories(context, activeChapterIds, maxAgeMillis)
            Log.i(TAG, "CacheJanitor cleanup completed: $cleaned orphan items removed.")
            cleaned
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform database-aware cleanup: ${e.message}", e)
            // Fallback: clean only strictly expired files older than threshold
            cleanOrphanStagingDirectories(context, emptySet(), maxAgeMillis)
        }
    }
}
