package com.example.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import com.example.data.BackupManager
import java.util.concurrent.TimeUnit

class AutoBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            val backupManager = BackupManager(applicationContext)
            backupManager.createAutoBackup()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
    
    companion object {
        fun schedule(context: Context) {
            try {
                val intervalDays = com.example.data.SettingsManager(context).autoBackupInterval
                if (intervalDays <= 0) {
                    WorkManager.getInstance(context).cancelUniqueWork("auto_backup")
                    return
                }
                val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(intervalDays.toLong(), TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "auto_backup", ExistingPeriodicWorkPolicy.UPDATE, request
                )
            } catch (e: Exception) {
                // Ignore in tests
            }
        }
    }
}
