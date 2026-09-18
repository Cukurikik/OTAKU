package com.example.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_sync_actions")
data class PendingSyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: String = "",
    val trackerType: String = "", // ANILIST / MAL / CLOUD / KITSU
    val progress: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val profileId: Long = 1,
    val remoteId: Long = 0L,
    val itemId: String = ""
) {
    // Backwards compatibility properties
    val trackerId: String get() = trackerType.ifEmpty { "anilist" }
}

