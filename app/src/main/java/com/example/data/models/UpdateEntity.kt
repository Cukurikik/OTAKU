package com.example.data.models

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.PrimaryKey

@Entity(tableName = "updates")
@Serializable
data class UpdateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mangaId: String,
    val sourceId: String,
    val chapterId: String,
    val title: String,
    val chapterName: String,
    val thumbnailUrl: String?,
    val dateFetch: Long,
    val read: Boolean = false
)
