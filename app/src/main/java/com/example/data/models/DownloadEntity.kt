package com.example.data.models

import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

@Entity(
    tableName = "downloads",
    primaryKeys = ["profileId", "sourceId", "mangaId", "chapterId"],
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["sourceId", "mangaId", "chapterId"])
    ]
)
@Serializable
data class DownloadEntity(
    val profileId: Long = 1L,
    val sourceId: String,
    val mangaId: String,
    val chapterId: String,
    val state: String = "QUEUE", // QUEUE, RESOLVING, DOWNLOADING, DOWNLOADED, ERROR, PAUSED, CANCELLED
    val priority: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val directUrl: String? = null,
    val headersJson: String? = null,
    val cookies: String? = null,
    val mimeType: String? = null,
    val qualityLabel: String? = null,
    val subtitlesJson: String? = null
)
