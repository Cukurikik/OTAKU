package com.example.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class ContentType {
    COMIC, ANIME, DRAMA, MOVIE
}

@Entity(
    tableName = "history_items",
    primaryKeys = ["profileId", "id"],
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["timestamp"])
    ]
)
@Serializable
data class HistoryItem(
    val profileId: Long = 1L,
    val id: String = "",
    val title: String = "",
    val source: String = "",
    val imageUrl: String = "",
    val type: ContentType = ContentType.COMIC,
    val progress: Float = 0f, // 0.0 to 1.0
    val lastChapterOrEpisode: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val sourceId: String = "",
    val status: String = "",
    val year: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)

@Serializable
data class ContentItem(
    val id: String = "",
    val title: String = "",
    val source: String = "",
    val imageUrl: String = "",
    val type: ContentType = ContentType.COMIC,
    val description: String = "",
    val genres: List<String> = emptyList(),
    val sourceId: String = "",
    val status: String = "",
    val year: String = "",
    val lastUpdated: Long = System.currentTimeMillis(),
    val alternativeSources: List<AlternativeSource> = emptyList()
)

@Serializable
data class Chapter(
    val id: String = "",
    val title: String = "",
    val url: String,
    val date: String = ""
)

@Serializable
data class Page(
    val url: String
)

@Serializable
data class AlternativeSource(
    val id: String = "",
    val sourceId: String,
    val sourceName: String
)
