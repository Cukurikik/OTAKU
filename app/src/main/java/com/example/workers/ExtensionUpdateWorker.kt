package com.example.workers

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.*
import com.example.data.SettingsManager
import com.example.data.sources.tachiyomi.AvailableExtension
import com.example.data.sources.tachiyomi.ExtensionManager
import com.example.data.sources.tachiyomi.ExtensionRepositoryManager
import com.example.data.sources.tachiyomi.InstalledExtension
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * ExtensionUpdateWorker executes silent background checks against registered
 * extension repositories (index.min.json), detecting newer version codes,
 * caching APK updates, and triggering silent or one-click updates to maintain 99% source uptime.
 */
class ExtensionUpdateWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ExtensionUpdateWorker"
        const val WORK_NAME_PERIODIC = "PeriodicExtensionUpdate"
        const val WORK_NAME_ONE_TIME = "ManualExtensionUpdate"

        /**
         * Schedules periodic background extension update checks (12h or 24h).
         */
        fun schedule(context: Context) {
            try {
                val settings = SettingsManager(context)
                if (!settings.autoUpdateExtensionsEnabled || settings.autoUpdateExtensionsIntervalHours <= 0) {
                    WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
                    Log.d(TAG, "Extension auto-update is disabled, cancelling periodic work.")
                    return
                }

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val intervalHours = settings.autoUpdateExtensionsIntervalHours.coerceAtLeast(12).toLong()

                val request = PeriodicWorkRequestBuilder<ExtensionUpdateWorker>(
                    intervalHours,
                    TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        15,
                        TimeUnit.MINUTES
                    )
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
                Log.d(TAG, "ExtensionUpdateWorker scheduled periodically every $intervalHours hours.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to schedule ExtensionUpdateWorker: ${e.message}")
            }
        }

        /**
         * Triggers an immediate one-time extension update check.
         */
        fun runOnce(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = OneTimeWorkRequestBuilder<ExtensionUpdateWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME_ONE_TIME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
                Log.d(TAG, "ExtensionUpdateWorker manual run enqueued.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to run ExtensionUpdateWorker manually: ${e.message}")
            }
        }

        /**
         * Cancels periodic background extension checks.
         */
        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cancel periodic ExtensionUpdateWorker: ${e.message}")
            }
        }

        /**
         * Observable flow indicating whether the manual check is currently in progress.
         */
        fun isCheckingFlow(context: Context): Flow<Boolean> {
            val workManager = WorkManager.getInstance(context)
            return workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME_ONE_TIME).map { workInfos ->
                workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = SettingsManager(appContext)
        Log.d(TAG, "ExtensionUpdateWorker starting background execution...")

        try {
            val repoManager = ExtensionRepositoryManager(appContext)
            val registeredRepos = repoManager.repositories.value.ifEmpty {
                ExtensionManager.BUILT_IN_REPOS
            }

            val installedExtensions = ExtensionManager.getInstalledExtensions(appContext)
            if (installedExtensions.isEmpty()) {
                Log.d(TAG, "No installed extensions found to check for updates.")
                settings.lastExtensionUpdateCheckTime = System.currentTimeMillis()
                settings.lastExtensionUpdateCheckResult = "Tidak ada ekstensi terpasang"
                return@withContext Result.success()
            }

            Log.d(TAG, "Fetching index.min.json across ${registeredRepos.size} repositories...")
            val availableResult = ExtensionManager.getAllAvailableExtensions(registeredRepos)
            val availableExtensions = availableResult.getOrDefault(emptyList())

            if (availableExtensions.isEmpty()) {
                Log.w(TAG, "Could not fetch available extensions from repositories.")
                settings.lastExtensionUpdateCheckTime = System.currentTimeMillis()
                settings.lastExtensionUpdateCheckResult = "Gagal menghubungi repositori"
                return@withContext Result.retry()
            }

            val availableByPkg = availableExtensions.associateBy { it.pkg }
            val updatesToProcess = mutableListOf<Pair<InstalledExtension, AvailableExtension>>()

            for (installed in installedExtensions) {
                val remote = availableByPkg[installed.pkgName] ?: availableByPkg[installed.pkg]
                if (remote != null && remote.code > installed.versionCode) {
                    Log.i(TAG, "Update detected for ${installed.name}: v${installed.versionName} (${installed.versionCode}) -> v${remote.version} (${remote.code})")
                    updatesToProcess.add(Pair(installed, remote))
                }
            }

            if (updatesToProcess.isEmpty()) {
                Log.d(TAG, "All ${installedExtensions.size} installed extensions are up to date.")
                settings.lastExtensionUpdateCheckTime = System.currentTimeMillis()
                settings.lastExtensionUpdateCheckResult = "Semua ekstensi mutakhir"
                return@withContext Result.success()
            }

            var downloadedCount = 0
            val downloadedFiles = mutableListOf<File>()

            if (settings.autoDownloadExtensionUpdates) {
                Log.d(TAG, "Auto-downloading ${updatesToProcess.size} extension updates into cache...")
                for ((_, remote) in updatesToProcess) {
                    try {
                        val downloadResult = ExtensionManager.downloadExtensionApk(
                            context = appContext,
                            apkName = remote.apk,
                            repoUrl = remote.repoUrl,
                            expectedPkg = remote.pkg,
                            expectedVersionCode = remote.code,
                            onProgress = { /* Silent background progress */ }
                        )

                        downloadResult.onSuccess { apkFile ->
                            downloadedCount++
                            downloadedFiles.add(apkFile)

                            // Attempt silent package session install if supported and enabled
                            if (settings.silentExtensionInstall && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                try {
                                    ExtensionManager.installApk(appContext, apkFile)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Silent install skipped for ${remote.name}: ${e.message}")
                                }
                            }
                        }.onFailure { err ->
                            Log.w(TAG, "Failed to download update APK for ${remote.name}: ${err.message}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error downloading ${remote.name}: ${e.message}")
                    }
                }
            }

            // Record execution metadata
            settings.lastExtensionUpdateCheckTime = System.currentTimeMillis()
            settings.lastExtensionUpdateCheckResult = "${updatesToProcess.size} pembaruan terdeteksi"

            // Notify UI & package listeners
            ExtensionManager.notifyPackageChanged()

            // Trigger One-Tap or Downloaded notification
            val updateNames = updatesToProcess.map { it.second.name }
            NotificationHelper.showExtensionUpdateNotification(
                context = appContext,
                updatedCount = updatesToProcess.size,
                extensionNames = updateNames,
                isDownloaded = downloadedCount > 0
            )

            Log.i(TAG, "ExtensionUpdateWorker completed successfully. ${updatesToProcess.size} updates detected, $downloadedCount downloaded.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "ExtensionUpdateWorker encountered fatal error: ${e.message}", e)
            settings.lastExtensionUpdateCheckTime = System.currentTimeMillis()
            settings.lastExtensionUpdateCheckResult = "Kesalahan pemeriksaan: ${e.message}"
            Result.failure()
        }
    }
}
