package com.example.data.trackers

import android.content.Context
import android.util.Log
import com.example.OtakuHubApp
import com.example.data.SettingsManager
import com.example.data.models.ContentItem
import com.example.data.models.TrackEntity
import com.example.data.models.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RemoteProgressState(
    val progress: Int,
    val updatedAt: Long = System.currentTimeMillis(),
    val status: String? = null
)

interface Tracker {
    val id: String
    val name: String
    val logoUrl: String
    
    suspend fun search(query: String): List<TrackItem>
    suspend fun bind(trackItem: TrackItem, manga: ContentItem): TrackEntity
    suspend fun update(track: TrackEntity)
    suspend fun refresh(track: TrackEntity): TrackEntity
    suspend fun getRemoteProgress(remoteId: Long): RemoteProgressState? = null
    suspend fun pushProgress(remoteId: Long, progress: Int): Boolean = false
    fun isLogged(): Boolean
    fun logout()
}

open class TrackerManager(
    private val context: Context,
    private val settings: SettingsManager
) {
    val trackers: List<Tracker> by lazy {
        listOf(
            AniListTracker(settings),
            MyAnimeListTracker(settings),
            KitsuTracker(context, settings)
        )
    }

    fun getTracker(id: String): Tracker? {
        return trackers.find { it.id.equals(id, ignoreCase = true) }
    }

    open suspend fun getRemoteProgress(mediaId: String, trackerType: String): RemoteProgressState? = withContext(Dispatchers.IO) {
        val tracker = getTracker(trackerType) ?: return@withContext null
        if (!tracker.isLogged()) return@withContext null
        
        val db = try {
            (context.applicationContext as? OtakuHubApp)?.database ?: OtakuHubApp.instance.database
        } catch (_: Exception) {
            null
        } ?: return@withContext null
        
        val profileId = com.example.data.ProfileManager.currentProfileIdValue
        val tracks = db.trackDao().getTracksForMangaSync(profileId, mediaId)
        val track = tracks.find { it.trackerId.equals(trackerType, ignoreCase = true) }
        val remoteId = track?.remoteId ?: mediaId.toLongOrNull() ?: 0L
        if (remoteId <= 0L) return@withContext null

        tracker.getRemoteProgress(remoteId)
    }

    open suspend fun pushProgress(mediaId: String, progress: Int): Boolean = withContext(Dispatchers.IO) {
        val db = try {
            (context.applicationContext as? OtakuHubApp)?.database ?: OtakuHubApp.instance.database
        } catch (_: Exception) {
            null
        } ?: return@withContext false
        
        val profileId = com.example.data.ProfileManager.currentProfileIdValue
        val tracks = db.trackDao().getTracksForMangaSync(profileId, mediaId)
        var anySuccess = false
        for (track in tracks) {
            val tracker = getTracker(track.trackerId)
            if (tracker != null && tracker.isLogged()) {
                val ok = tracker.pushProgress(track.remoteId, progress)
                if (ok) {
                    db.trackDao().insertTrack(track.copy(lastChapterRead = progress.toFloat()))
                    anySuccess = true
                }
            }
        }
        anySuccess
    }

    open suspend fun pushProgress(
        mediaId: String,
        trackerType: String,
        progress: Int,
        remoteId: Long = 0L
    ): Boolean = withContext(Dispatchers.IO) {
        val tracker = getTracker(trackerType) ?: return@withContext false
        if (!tracker.isLogged()) return@withContext false

        val db = try {
            (context.applicationContext as? OtakuHubApp)?.database ?: OtakuHubApp.instance.database
        } catch (_: Exception) {
            null
        } ?: return@withContext false
        
        val profileId = com.example.data.ProfileManager.currentProfileIdValue
        val actualRemoteId = if (remoteId > 0L) {
            remoteId
        } else {
            val tracks = db.trackDao().getTracksForMangaSync(profileId, mediaId)
            tracks.find { it.trackerId.equals(trackerType, ignoreCase = true) }?.remoteId ?: mediaId.toLongOrNull() ?: 0L
        }

        if (actualRemoteId <= 0L) return@withContext false
        val ok = tracker.pushProgress(actualRemoteId, progress)
        if (ok) {
            val existingTrack = db.trackDao().getTracksForMangaSync(profileId, mediaId).find { it.trackerId.equals(trackerType, ignoreCase = true) }
            if (existingTrack != null) {
                db.trackDao().insertTrack(existingTrack.copy(lastChapterRead = progress.toFloat()))
            }
        }
        ok
    }

    open suspend fun updateLocalProgress(mediaId: String, progress: Int): Unit = withContext(Dispatchers.IO) {
        try {
            val db = (context.applicationContext as? OtakuHubApp)?.database ?: OtakuHubApp.instance.database
            val profileId = com.example.data.ProfileManager.currentProfileIdValue

            // Update TrackEntity
            val tracks = db.trackDao().getTracksForMangaSync(profileId, mediaId)
            for (track in tracks) {
                db.trackDao().insertTrack(track.copy(lastChapterRead = progress.toFloat()))
            }

            // Update HistoryItem if exists
            val history = db.historyDao().getHistoryItemSync(profileId, mediaId)
            if (history != null && history.progress < progress) {
                db.historyDao().insertHistory(
                    history.copy(
                        progress = progress.toFloat(),
                        lastChapterOrEpisode = "Chapter/Ep $progress",
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("TrackerManager", "Failed to update local progress: ${e.message}")
        }
    }
}


