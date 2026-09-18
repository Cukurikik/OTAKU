package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.models.PendingSyncEntity

@Dao
interface PendingSyncDao {
    @Query("SELECT * FROM pending_sync_actions ORDER BY id ASC")
    suspend fun getAllPendingActions(): List<PendingSyncEntity>

    @Query("SELECT * FROM pending_sync_actions ORDER BY id ASC")
    suspend fun getAllSyncs(): List<PendingSyncEntity>

    @Query("SELECT * FROM pending_sync_actions WHERE (trackerType = :trackerType OR trackerType = :trackerId) AND (remoteId = :remoteId OR mediaId = :mediaId) LIMIT 1")
    suspend fun getSync(trackerId: String, remoteId: Long, trackerType: String = trackerId, mediaId: String = remoteId.toString()): PendingSyncEntity?

    @Query("SELECT * FROM pending_sync_actions WHERE (mediaId = :mediaId OR itemId = :mediaId) AND (trackerType = :trackerType OR trackerType = :trackerId) LIMIT 1")
    suspend fun getPendingAction(mediaId: String, trackerType: String, trackerId: String = trackerType): PendingSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sync: PendingSyncEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: PendingSyncEntity): Long
    
    @Query("UPDATE pending_sync_actions SET progress = :progress, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE pending_sync_actions SET progress = :progress, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: Int, progress: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM pending_sync_actions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_sync_actions WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM pending_sync_actions WHERE id = :id")
    suspend fun deleteAction(id: Long)

    @Query("DELETE FROM pending_sync_actions WHERE id = :id")
    suspend fun deleteAction(id: Int)

    @Query("DELETE FROM pending_sync_actions WHERE (mediaId = :mediaId OR itemId = :mediaId) AND (trackerType = :trackerType)")
    suspend fun deleteByMediaAndTracker(mediaId: String, trackerType: String)
}

