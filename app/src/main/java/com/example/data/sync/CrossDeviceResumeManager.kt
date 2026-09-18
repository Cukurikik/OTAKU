package com.example.data.sync

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Orchestrates Real-time Delta State synchronization between devices (e.g. Phone ↔ Android TV).
 * Uploads playback / reading state every 10 seconds or on pause/exit.
 * Listens via Firebase Firestore (Cloud) and LocalResumeBus (UDP LAN) simultaneously.
 */
object CrossDeviceResumeManager {
    private const val TAG = "CrossDeviceResume"
    private const val PREFS_NAME = "cross_device_resume_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_LAST_DISMISSED = "last_dismissed_time"
    private const val UPLOAD_INTERVAL_MS = 10_000L // 10 seconds throttle
    private const val MAX_STALE_DURATION_MS = 3 * 3600 * 1000L // 3 hours

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
    private val _incomingResumeState = MutableStateFlow<CrossDeviceResumeState?>(null)
    val incomingResumeState: StateFlow<CrossDeviceResumeState?> = _incomingResumeState.asStateFlow()

    private var cachedDeviceId: String? = null
    private var lastPushTimestamp: Long = 0L
    private var lastDismissedTimestamp: Long = 0L
    private var firestoreListener: ListenerRegistration? = null
    private var isListening = false

    val localDeviceId: String
        get() = cachedDeviceId ?: "local_device"

    fun resetForTesting(customDeviceId: String = "local_device") {
        cachedDeviceId = customDeviceId
        lastDismissedTimestamp = 0L
        _incomingResumeState.value = null
    }

    fun getDeviceId(context: Context): String {
        cachedDeviceId?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        cachedDeviceId = id
        lastDismissedTimestamp = prefs.getLong(KEY_LAST_DISMISSED, 0L)
        return id
    }

    fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.applicationContext.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return isTvMode || hasLeanback
    }

    fun getDeviceName(context: Context): String {
        val isTv = isTvDevice(context)
        val model = Build.MODEL ?: "Unknown"
        return if (isTv) "Android TV ($model)" else "Ponsel ($model)"
    }

    /**
     * Starts listening for live resume states via Cloud (Firestore) and Local LAN (UDP).
     */
    fun startListening(context: Context) {
        if (isListening) return
        isListening = true
        val appContext = context.applicationContext
        val deviceId = getDeviceId(appContext)

        // 1. Listen on Local Wi-Fi UDP Bus
        LocalResumeBus.startListening(appContext) { state ->
            handleReceivedState(appContext, deviceId, state)
        }

        // 2. Listen on Cloud Firestore (if user is authenticated)
        setupFirestoreListener(appContext, deviceId)
    }

    fun setupFirestoreListener(context: Context, deviceId: String) {
        if (!CloudSyncManager.isInitialized) return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (firestoreListener != null) return

        try {
            val db = FirebaseFirestore.getInstance()
            val docRef = db.collection("users")
                .document(user.uid)
                .collection("live_state")
                .document("current_resume")

            firestoreListener = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Firestore resume listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val map = snapshot.data
                    if (map != null) {
                        val state = CrossDeviceResumeState.fromMap(map)
                        if (state != null) {
                            handleReceivedState(context, deviceId, state)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach Firestore snapshot listener: ${e.message}")
        }
    }

    private fun handleReceivedState(context: Context, myDeviceId: String, state: CrossDeviceResumeState) {
        handleIncomingState(state, myDeviceId)
    }

    fun handleIncomingState(state: CrossDeviceResumeState, myDeviceId: String = localDeviceId) {
        // Must come from a different device
        if (state.deviceId == myDeviceId) return

        // Must not be an already dismissed state
        if (state.updatedAt <= lastDismissedTimestamp) return

        // Must be fresh (not older than MAX_STALE_DURATION_MS)
        val age = System.currentTimeMillis() - state.updatedAt
        if (age > MAX_STALE_DURATION_MS || age < -60_000) return

        // If video, check that it's not basically finished (>95%)
        if (!state.contentType.equals("COMIC", ignoreCase = true) && state.durationMs > 0) {
            if (state.positionMs >= state.durationMs * 0.95f) return
        }

        // Emit new resume state
        _incomingResumeState.value = state
        Log.d(TAG, "Received resume state from ${state.deviceName}: ${state.contentTitle} at ${state.formattedTimestamp()}")
    }

    /**
     * Interceptor method called from PlayerViewModel every 10s or when paused/stopped.
     */
    fun pushPlaybackState(
        context: Context,
        itemId: String,
        sourceId: String,
        contentTitle: String,
        episodeId: String,
        episodeTitle: String,
        contentType: String,
        positionMs: Long,
        durationMs: Long,
        coverImageUrl: String = "",
        force: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastPushTimestamp) < UPLOAD_INTERVAL_MS) {
            return
        }
        if (durationMs <= 0 || positionMs <= 0) return

        lastPushTimestamp = now
        val deviceId = getDeviceId(context)
        val deviceName = getDeviceName(context)

        val state = CrossDeviceResumeState(
            deviceId = deviceId,
            deviceName = deviceName,
            itemId = itemId,
            sourceId = sourceId,
            contentTitle = contentTitle,
            episodeOrChapterId = episodeId,
            episodeOrChapterTitle = episodeTitle,
            contentType = contentType,
            positionMs = positionMs,
            durationMs = durationMs,
            coverImageUrl = coverImageUrl,
            updatedAt = now
        )

        // 1. Broadcast on Local LAN
        LocalResumeBus.broadcastState(state)

        // 2. Upload to Cloud Firestore
        uploadToCloud(state)
    }

    /**
     * Interceptor method called from ReaderViewModel on page turn or exit.
     */
    fun pushReaderState(
        context: Context,
        itemId: String,
        sourceId: String,
        contentTitle: String,
        chapterId: String,
        chapterTitle: String,
        pageIndex: Int,
        totalPages: Int,
        coverImageUrl: String = "",
        force: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastPushTimestamp) < UPLOAD_INTERVAL_MS) {
            return
        }
        lastPushTimestamp = now
        val deviceId = getDeviceId(context)
        val deviceName = getDeviceName(context)

        val state = CrossDeviceResumeState(
            deviceId = deviceId,
            deviceName = deviceName,
            itemId = itemId,
            sourceId = sourceId,
            contentTitle = contentTitle,
            episodeOrChapterId = chapterId,
            episodeOrChapterTitle = chapterTitle,
            contentType = "COMIC",
            pageIndex = pageIndex,
            totalPages = totalPages,
            coverImageUrl = coverImageUrl,
            updatedAt = now
        )

        // 1. Broadcast on Local LAN
        LocalResumeBus.broadcastState(state)

        // 2. Upload to Cloud Firestore
        uploadToCloud(state)
    }

    private fun uploadToCloud(state: CrossDeviceResumeState) {
        if (!CloudSyncManager.isInitialized) return
        val user = FirebaseAuth.getInstance().currentUser ?: return

        scope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("users")
                    .document(user.uid)
                    .collection("live_state")
                    .document("current_resume")
                    .set(state.toMap())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to upload resume state to Firestore: ${e.message}")
            }
        }
    }

    /**
     * Dismisses the current prompt and marks the state timestamp as handled.
     */
    fun dismiss(context: Context, state: CrossDeviceResumeState) {
        lastDismissedTimestamp = state.updatedAt
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_DISMISSED, state.updatedAt).apply()
        if (_incomingResumeState.value?.updatedAt == state.updatedAt) {
            _incomingResumeState.value = null
        }
    }

    /**
     * Marks the prompt as handled when the user accepts and resumes.
     */
    fun consume(context: Context, state: CrossDeviceResumeState) {
        dismiss(context, state)
    }
}
