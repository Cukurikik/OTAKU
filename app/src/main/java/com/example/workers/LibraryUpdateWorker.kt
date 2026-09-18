package com.example.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.Flow

import com.example.MainActivity
import com.example.OtakuHubApp
import com.example.data.models.ChapterEntry
import com.example.data.models.ContentType
import com.example.data.models.LibraryEntry
import com.example.data.models.UpdateEntity
import com.example.workers.DownloadAutomator

    import com.example.data.SettingsManager

    class LibraryUpdateWorker(

    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val app = applicationContext as? OtakuHubApp ?: return@withContext Result.failure()
            val repository = app.repository
            val db = app.database
            val settings = SettingsManager(applicationContext)

            val libraryItems = db.libraryDao().getAllEntriesSync(com.example.data.ProfileManager.currentProfileIdValue)
            if (libraryItems.isEmpty()) {
                return@withContext Result.success()
            }

            val semaphore = Semaphore(4) // Max 4 concurrent network requests
            val updatedEntries = mutableListOf<Pair<LibraryEntry, List<ChapterEntry>>>()

            val jobs = libraryItems.map { entry ->
                async {
                    semaphore.withPermit {
                        try {
                            withTimeoutOrNull(25_000L) {
                                val chapters = repository.getChapters(entry.mangaId, entry.sourceId).firstOrNull() ?: emptyList()
                                if (chapters.isNotEmpty()) {
                                    val existingEntries = db.chapterDao().getChaptersList(com.example.data.ProfileManager.currentProfileIdValue, entry.sourceId, entry.mangaId)
                                    val existingChapterIds = existingEntries.map { it.chapterId }.toSet()
                                    val existingUrls = existingEntries.map { it.url }.toSet()

                                    val newChapters = chapters.filter { it.id !in existingChapterIds && it.url !in existingUrls }
                                    if (newChapters.isNotEmpty()) {
                                        val entities = newChapters.mapIndexed { index, chapter ->
                                            ChapterEntry(
                                                sourceId = entry.sourceId,
                                                mangaId = entry.mangaId,
                                                chapterId = chapter.id,
                                                name = chapter.title,
                                                url = chapter.url,
                                                dateUpload = null,
                                                number = (chapters.size - index).toFloat(),
                                                read = false,
                                                bookmark = false,
                                                downloaded = false
                                            )
                                        }

                                        val updateEntities = newChapters.map { chapter ->
                                            UpdateEntity(
                                                mangaId = entry.mangaId,
                                                sourceId = entry.sourceId,
                                                chapterId = chapter.id,
                                                title = entry.title,
                                                chapterName = chapter.title,
                                                thumbnailUrl = entry.thumbnailUrl,
                                                dateFetch = System.currentTimeMillis(),
                                                read = false
                                            )
                                        }

                                        db.chapterDao().insertChapters(entities)
                                        db.updateDao().insertAll(updateEntities)
                                        db.libraryDao().updateUnreadCount(com.example.data.ProfileManager.currentProfileIdValue, entry.sourceId, entry.mangaId)

                                        // Check auto download if configured
                                        if (settings.autoDownloadNext > 0) {
                                            try {
                                                val contentType = when (entry.sourceId) {
                                                    in listOf("viki", "licil", "lokalfilm", "jucelebrity") -> ContentType.DRAMA
                                                    else -> ContentType.COMIC
                                                }
                                                entities.take(settings.autoDownloadNext).forEach { ch ->
                                                    DownloadAutomator.processAutomations(
                                                        context = applicationContext,
                                                        settings = settings,
                                                        chapterDao = db.chapterDao(),
                                                        downloadDao = db.downloadDao(),
                                                        sourceId = entry.sourceId,
                                                        itemId = entry.mangaId,
                                                        currentChapterId = ch.chapterId,
                                                        contentType = contentType
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                // Ignore download automator error
                                            }
                                        }

                                        synchronized(updatedEntries) {
                                            updatedEntries.add(Pair(entry, entities))
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Isolated error per entry
                        }
                    }
                }
            }

            jobs.awaitAll()

            // Synchronize all library unread counts
            db.libraryDao().updateAllUnreadCounts(com.example.data.ProfileManager.currentProfileIdValue)

            // Show notifications if any updates were found
            if (updatedEntries.isNotEmpty()) {
                val totalNewChapters = updatedEntries.sumOf { it.second.size }
                val seriesCount = updatedEntries.size

                // Send notification for each series
                for ((entry, newChs) in updatedEntries) {
                    val message = if (newChs.size == 1) {
                        "${newChs.first().name} baru rilis!"
                    } else {
                        "${newChs.size} chapter/episode baru rilis!"
                    }

                    com.example.notifications.NotificationHelper.showUpdateNotification(
                        context = applicationContext,
                        title = entry.title,
                        message = message,
                        itemId = entry.mangaId,
                        sourceId = entry.sourceId
                    )
                }

                // Send summary notification
                com.example.notifications.NotificationHelper.showSummaryNotification(
                    context = applicationContext,
                    summaryText = "$totalNewChapters chapter/episode baru dari $seriesCount seri di Library"
                )
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val CHANNEL_ID = "library_updates"
        const val GROUP_KEY = "com.example.LIBRARY_UPDATES"
        const val SUMMARY_NOTIFICATION_ID = 99999
        const val WORK_NAME_PERIODIC = "LibraryUpdate"
        const val WORK_NAME_ONE_TIME = "manual_library_update"

        fun schedule(context: Context) {
            try {
                val settingsManager = SettingsManager(context)
                val interval = settingsManager.libraryUpdateInterval

                if (interval <= 0) {
                    WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
                    return
                }

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<LibraryUpdateWorker>(interval.toLong(), TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            } catch (e: Exception) {
                // Ignore in tests
            }
        }

        fun runOnce(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = OneTimeWorkRequestBuilder<LibraryUpdateWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME_ONE_TIME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            } catch (e: Exception) {
                // Ignore in tests
            }
        }

        fun isUpdatingFlow(context: Context): Flow<Boolean> {
            val workManager = WorkManager.getInstance(context)
            return workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME_ONE_TIME).map { workInfos ->
                workInfos.any { it.state == androidx.work.WorkInfo.State.RUNNING || it.state == androidx.work.WorkInfo.State.ENQUEUED }
            }
        }
    }
}
