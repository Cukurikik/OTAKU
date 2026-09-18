package com.example.data.models

import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

@Entity(
    tableName = "library_entries",
    primaryKeys = ["profileId", "sourceId", "mangaId"],
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["sourceId", "mangaId"])
    ]
)
@Serializable
data class LibraryEntry(
    val profileId: Long = 1L,
    val sourceId: String = "",
    val mangaId: String = "",
    val title: String = "",
    val thumbnailUrl: String? = null,
    val addedAt: Long = 0L,
    val lastUpdatedAt: Long? = null,
    val favorite: Boolean = true,
    val categoryId: Long? = null,
    val status: String = "",
    val unreadCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chapter_entries",
    primaryKeys = ["profileId", "sourceId", "mangaId", "chapterId"],
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["sourceId", "mangaId"]),
        Index(value = ["profileId", "sourceId", "mangaId"])
    ]
)
@Serializable
data class ChapterEntry(
    val profileId: Long = 1L,
    val sourceId: String = "",
    val mangaId: String = "",
    val chapterId: String,
    val name: String,
    val url: String,
    val dateUpload: Long?,
    val number: Float?,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Int = 0,
    val lastReadAt: Long? = null,
    val downloaded: Boolean = false
)
