package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.OtakuHubApp
import com.example.data.db.PendingSyncDao
import com.example.data.models.PendingSyncEntity
import com.example.data.trackers.TrackerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PendingSyncQueue(
    private val pendingSyncDao: PendingSyncDao,
    private val trackerManager: TrackerManager,
    private val context: Context? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    init {
        updatePendingCount()
    }

    fun updatePendingCount() {
        scope.launch {
            try {
                val actions = pendingSyncDao.getAllPendingActions()
                _pendingCount.value = actions.size
            } catch (_: Exception) {}
        }
    }

    suspend fun queueAction(
        mediaId: String,
        trackerType: String,
        progress: Int,
        remoteId: Long = 0L,
        itemId: String = mediaId,
        profileId: Long = 1L
    ) = withContext(Dispatchers.IO) {
        val existing = pendingSyncDao.getPendingAction(mediaId, trackerType)
        if (existing != null) {
            pendingSyncDao.updateProgress(existing.id, progress, System.currentTimeMillis())
        } else {
            pendingSyncDao.insertAction(
                PendingSyncEntity(
                    mediaId = mediaId,
                    trackerType = trackerType,
                    progress = progress,
                    updatedAt = System.currentTimeMillis(),
                    profileId = profileId,
                    remoteId = remoteId,
                    itemId = itemId
                )
            )
        }
        updatePendingCount()

        if (isNetworkAvailable()) {
            processQueue()
        }
    }

    suspend fun processQueue(): Unit = withContext(Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            return@withContext
        }
        try {
            _isSyncing.value = true
            val pendingActions = pendingSyncDao.getAllPendingActions()
            Log.d("PendingSyncQueue", "Processing ${pendingActions.size} pending sync actions")

            for (action in pendingActions) {
                val trackerType = action.trackerType.lowercase()

                if (trackerType == "cloud") {
                    try {
                        val ctx = context ?: OtakuHubApp.instance.applicationContext
                        CloudSyncManager.syncNow(ctx)
                        pendingSyncDao.deleteAction(action.id)
                    } catch (e: Exception) {
                        Log.w("PendingSyncQueue", "Cloud sync failed for action: ${e.message}")
                    }
                    continue
                }

                // External Tracker (AniList, MyAnimeList, Kitsu)
                val remoteState = trackerManager.getRemoteProgress(action.mediaId, action.trackerType)

                if (remoteState == null || action.updatedAt > remoteState.updatedAt) {
                    // Local state is newer: Push ke Server (LWW)
                    val success = trackerManager.pushProgress(
                        mediaId = action.mediaId,
                        trackerType = action.trackerType,
                        progress = action.progress,
                        remoteId = action.remoteId
                    )
                    if (success) {
                        pendingSyncDao.deleteAction(action.id)
                        Log.d("PendingSyncQueue", "Pushed local progress for ${action.mediaId} ($trackerType)")
                    }
                } else {
                    // Server state is newer: Timpa Local State & Hapus Antrean (LWW)
                    trackerManager.updateLocalProgress(action.mediaId, remoteState.progress)
                    pendingSyncDao.deleteAction(action.id)
                    Log.d("PendingSyncQueue", "Updated local progress from remote for ${action.mediaId} ($trackerType)")
                }
            }
        } catch (e: Exception) {
            Log.e("PendingSyncQueue", "Error during processQueue: ${e.message}", e)
        } finally {
            _isSyncing.value = false
            updatePendingCount()
            syncMutex.unlock()
        }
    }

    fun isNetworkAvailable(): Boolean {
        val appContext = context ?: try { OtakuHubApp.instance.applicationContext } catch (_: Exception) { null } ?: return false
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private var networkCallbackRegistered = false

        fun registerNetworkMonitor(context: Context, queueProvider: () -> PendingSyncQueue) {
            if (networkCallbackRegistered) return
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

                cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        Log.d("PendingSyncQueue", "Network connectivity restored. Auto processing sync queue.")
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                queueProvider().processQueue()
                            } catch (e: Exception) {
                                Log.w("PendingSyncQueue", "Network recovery sync error: ${e.message}")
                            }
                        }
                    }
                })
                networkCallbackRegistered = true
            } catch (e: Exception) {
                Log.w("PendingSyncQueue", "Failed to register network callback: ${e.message}")
            }
        }
    }
}
