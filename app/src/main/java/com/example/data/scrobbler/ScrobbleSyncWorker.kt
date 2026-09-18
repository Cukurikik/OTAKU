package com.example.data.scrobbler

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.OtakuHubApp
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker for Two-Way Status Sync and Auto-Scrobbler synchronization.
 * Runs on a 15-minute periodic schedule or can be triggered immediately on-demand.
 */
class ScrobbleSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? OtakuHubApp ?: return Result.failure()
        val settings = app.settingsManager
        if (!settings.autoTrackerSync) {
            return Result.success()
        }

        val secureStorage = SecureCredentialStoreImpl(applicationContext, settings)
        val trackerManager = app.trackerManager
        val syncEngine = TwoWaySyncEngine(applicationContext, secureStorage, trackerManager)

        val profileId = com.example.data.ProfileManager.currentProfileIdValue
        return try {
            val summary = syncEngine.syncAll(profileId)
            val anySuccess = summary.results.any { it.success }
            if (anySuccess || summary.results.isEmpty()) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "PeriodicScrobbleSyncWork"
        const val ONE_TIME_WORK_NAME = "OneTimeScrobbleSyncWork"

        fun schedulePeriodic(context: Context, intervalMinutes: Long = 15L) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ScrobbleSyncWorker>(
                intervalMinutes, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun enqueueImmediate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<ScrobbleSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
