package com.example.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["profileId", "mangaId"])
    ]
)
@Serializable
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 1L,
    val mangaId: String,
    val trackerId: String,
    val remoteId: Long,
    val title: String,
    var lastChapterRead: Float = 0f,
    var totalChapters: Int = 0,
    var status: Int = 0, // 0 = reading, 1 = completed, 2 = on hold, 3 = dropped, 4 = plan to read
    var score: Float = 0f
)

@Serializable
data class TrackItem(
    val trackerId: String,
    val id: Long,
    val title: String,
    val totalChapters: Int,
    val coverUrl: String,
    val summary: String
)
