package com.example.data.models

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "page_downloads",
    primaryKeys = ["profileId", "sourceId", "mangaId", "chapterId", "pageIndex"],
    foreignKeys = [
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["profileId", "sourceId", "mangaId", "chapterId"],
            childColumns = ["profileId", "sourceId", "mangaId", "chapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("profileId", "sourceId", "mangaId", "chapterId")
    ]
)
@Serializable
data class PageDownloadEntity(
    val profileId: Long = 1L,
    val sourceId: String,
    val mangaId: String,
    val chapterId: String,
    val pageIndex: Int,
    val remoteUrl: String,
    val localUri: String? = null,
    val state: String = "QUEUE", // QUEUE, DOWNLOADING, DOWNLOADED, ERROR
    val bytes: Long = 0,
    val checksum: String? = null
)
