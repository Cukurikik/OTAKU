package com.example.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.OtakuHubApp
import com.example.data.SettingsManager
import com.example.data.linkrot.AutoMigrationEngine
import com.example.data.linkrot.LinkRotTracker
import com.example.data.models.LibraryEntry
import com.example.data.sources.SourceHealthCatalog
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

class AutoMigrationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AutoMigrationWorker"
        const val WORK_NAME_PERIODIC = "PeriodicAutoMigration"
        const val WORK_NAME_ONE_TIME = "ManualAutoMigration"

        fun schedule(context: Context) {
            try {
                val settings = SettingsManager(context)
                if (!settings.autoMigrationEnabled || settings.autoMigrationIntervalHours <= 0) {
                    WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
                    return
                }

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<AutoMigrationWorker>(
                    settings.autoMigrationIntervalHours.toLong(),
                    TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
                Log.d(TAG, "AutoMigrationWorker scheduled every ${settings.autoMigrationIntervalHours} hours")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to schedule AutoMigrationWorker: ${e.message}")
            }
        }

        fun runOnce(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = OneTimeWorkRequestBuilder<AutoMigrationWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME_ONE_TIME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
                Log.d(TAG, "AutoMigrationWorker manual run enqueued")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to run AutoMigrationWorker manually: ${e.message}")
            }
        }

        fun isMigratingFlow(context: Context): Flow<Boolean> {
            val workManager = WorkManager.getInstance(context)
            return workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME_ONE_TIME).map { workInfos ->
                workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? OtakuHubApp ?: return@withContext Result.failure()
        val settings = SettingsManager(applicationContext)

        if (!settings.autoMigrationEnabled) {
            Log.d(TAG, "Auto migration disabled in settings, skipping.")
            return@withContext Result.success()
        }

        val db = app.database
        val repository = app.repository
        val linkRotTracker = LinkRotTracker.getInstance(applicationContext)
        val profileId = com.example.data.ProfileManager.currentProfileIdValue

        val libraryEntries = db.libraryDao().getAllEntriesSync(profileId)
        if (libraryEntries.isEmpty()) {
            Log.d(TAG, "Library is empty, nothing to check for link rot.")
            return@withContext Result.success()
        }

        Log.i(TAG, "Starting Smart Link Rot & Auto-Migration scan for ${libraryEntries.size} entries...")

        val migratedSummaries = mutableListOf<String>()
        val rotCandidates = mutableListOf<LibraryEntry>()
        val networkConcurrency = Semaphore(3)

        // Langkah 1: Pengecekan status health (HTTP 404/502/DNS/Timeout) pada seluruh item Library
        for (entry in libraryEntries) {
            if (SourceHealthCatalog.isKnownDead(entry.sourceId)) {
                Log.w(TAG, "Source '${entry.sourceId}' is known dead. Marking '${entry.title}' for auto-migration.")
                rotCandidates.add(entry)
                continue
            }

            var checkSuccess = false
            var failureReason = ""

            networkConcurrency.withPermit {
                try {
                    val chapters = withTimeoutOrNull(15_000L) {
                        repository.getChapters(entry.mangaId, entry.sourceId).firstOrNull()
                    }
                    if (!chapters.isNullOrEmpty()) {
                        checkSuccess = true
                        linkRotTracker.recordSuccess(entry.sourceId, entry.mangaId)
                    } else {
                        failureReason = "Daftar chapter kosong (Kemungkinan Link Rot / Page Missing)"
                    }
                } catch (e: Exception) {
                    failureReason = e.message ?: e::class.java.simpleName
                }
            }

            if (!checkSuccess) {
                val currentFailCount = linkRotTracker.recordFailure(entry.sourceId, entry.mangaId, failureReason)
                Log.w(TAG, "Entry '${entry.title}' [${entry.sourceId}] gagal diakses ($currentFailCount/3). Reason: $failureReason")
                
                // Memicu jika mati 3x berturut-turut
                if (currentFailCount >= LinkRotTracker.DEFAULT_ROT_THRESHOLD) {
                    rotCandidates.add(entry)
                }
            }
        }

        Log.i(TAG, "Ditemukan ${rotCandidates.size} entri yang mengalami Link Rot (mati >= 3x). Memulai Auto-Migration...")

        // Langkah 2: Memicu algoritma Fuzzy Matching di belakang layar pada sumber cadangan & update DB
        for (candidate in rotCandidates) {
            try {
                val outcome = AutoMigrationEngine.resolveAndMigrate(app, candidate, profileId)
                if (outcome.isSuccess) {
                    val summaryLine = "${outcome.oldTitle} → ${outcome.toSourceName}"
                    migratedSummaries.add(summaryLine)
                    Log.i(TAG, "Sukses auto-migrasi: $summaryLine")
                } else {
                    Log.w(TAG, "Gagal auto-migrasi '${candidate.title}': ${outcome.errorMessage}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception saat migrasi '${candidate.title}': ${e.message}", e)
            }
        }

        // Langkah 3: Memunculkan notifikasi senyap jika ada seri yang berhasil dipindahkan
        if (migratedSummaries.isNotEmpty()) {
            NotificationHelper.showAutoMigrationSilentNotification(
                context = applicationContext,
                migratedCount = migratedSummaries.size,
                details = migratedSummaries
            )
            Log.i(TAG, "Notifikasi senyap dikirim untuk ${migratedSummaries.size} entri yang dimigrasi.")
        }

        Result.success()
    }
}
