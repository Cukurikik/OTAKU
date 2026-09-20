package com.example.data.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class ContentType {
    COMIC, ANIME, DRAMA, MOVIE
}

@Immutable
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

@Immutable
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
    val alternativeSources: List<AlternativeSource> = emptyList(),
    val seasonNumber: Int? = null,
    val subtitleStatus: String = "", // "Sub Indo", "Raw", "Eng Sub", etc.
    val castList: List<String> = emptyList(),
    val lastWatchedPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L
)

@Immutable
@Serializable
data class Chapter(
    val id: String = "",
    val title: String = "",
    val url: String,
    val date: String = "",
    val seasonNumber: Int? = null,
    val subtitleStatus: String = "", // "Sub Indo", "Raw", "Eng Sub", etc.
    val castList: List<String> = emptyList(),
    val lastWatchedPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L
)

@Immutable
@Serializable
data class Page(
    val url: String
)

@Immutable
@Serializable
data class AlternativeSource(
    val id: String = "",
    val sourceId: String,
    val sourceName: String
)
