package com.example.workers

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.OtakuHubApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class CacheJanitorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val app = applicationContext as? OtakuHubApp
            if (app != null) {
                val cleaned = CacheJanitor.performCleanup(app, app.database)
                Log.d(TAG, "CacheJanitorWorker finished cleanup: $cleaned items cleaned")
            } else {
                CacheJanitor.cleanOrphanStagingDirectories(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "CacheJanitorWorker failed: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CacheJanitorWorker"
        private const val WORK_NAME_PERIODIC = "cache_janitor_periodic_work"
        private const val WORK_NAME_ONE_TIME = "cache_janitor_one_time_work"

        fun schedule(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<CacheJanitorWorker>(24, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to schedule CacheJanitorWorker: ${e.message}")
            }
        }

        fun runOnce(context: Context) {
            try {
                val request = OneTimeWorkRequestBuilder<CacheJanitorWorker>()
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME_ONE_TIME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enqueue one-time CacheJanitorWorker: ${e.message}")
            }
        }
    }
}
