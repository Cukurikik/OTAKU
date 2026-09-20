package com.example.data.sources.core

import androidx.compose.runtime.Immutable
import java.time.Instant

sealed class SourceResult<out T> {
    data class Success<T>(val data: T) : SourceResult<T>()
    data class Error(val message: String, val exception: Throwable? = null) : SourceResult<Nothing>()
}

@Immutable
data class ComicDetail(
    val id: String,
    val url: String,
    val title: String,
    val alternativeTitles: List<String>,
    val coverUrl: String?,
    val description: String?,
    val authors: List<String>,
    val artists: List<String>,
    val genres: List<String>,
    val type: String?,
    val status: String?,
    val rating: Float?,
    val ratingCount: Int?,
    val year: Int?,
    val demographic: String?,
    val chapters: List<Chapter>,
    val sourceId: String
)

@Immutable
data class Chapter(
    val id: String,
    val url: String,
    val name: String,
    val number: Float?,
    val volume: Int?,
    val title: String?,
    val scanlator: String?,
    val publishedAt: Instant?,
    val uploadDate: Instant?,
    val sourceId: String
)

@Immutable
data class PageImage(
    val index: Int,
    val url: String,
    val referer: String?,
    val headers: Map<String, String>,
    val width: Int?,
    val height: Int?,
    val sourceId: String
)

enum class ImageValidation { VALID, HTML, CLOUDFLARE, FORBIDDEN, EMPTY, CORRUPTED }

@Immutable
data class SourceHealth(
    val sourceId: String,
    val reachable: Boolean,
    val httpStatus: Int?,
    val cloudflareDetected: Boolean,
    val javascriptRequired: Boolean,
    val parserWorking: Boolean,
    val searchWorking: Boolean,
    val detailWorking: Boolean,
    val chapterWorking: Boolean,
    val imageWorking: Boolean,
    val lastChecked: Instant,
    val error: String?
)

@Immutable
data class ComicItem(val id: String, val url: String, val title: String, val coverUrl: String?)

@Immutable
data class HomeSection(val title: String, val items: List<ComicItem>)

@Immutable
data class HomePage(val sections: List<HomeSection>)

@Immutable
data class SearchPage(val items: List<ComicItem>, val hasNextPage: Boolean)

@Immutable
data class BrowsePage(val items: List<ComicItem>, val hasNextPage: Boolean)

// Anime Models
@Immutable
data class AnimeHome(val sections: List<AnimeHomeSection>)

@Immutable
data class AnimeHomeSection(val title: String, val items: List<AnimeItem>)

@Immutable
data class AnimeItem(val id: String, val url: String, val title: String, val coverUrl: String?)

@Immutable
data class AnimeSearchPage(val items: List<AnimeItem>, val hasNextPage: Boolean)

@Immutable
data class AnimeDetail(
    val id: String,
    val url: String,
    val title: String,
    val coverUrl: String?,
    val description: String?,
    val genres: List<String>,
    val status: String?,
    val episodes: List<Episode>,
    val sourceId: String
)

@Immutable
data class Episode(
    val id: String,
    val url: String,
    val name: String,
    val number: Float?,
    val date: Instant?,
    val sourceId: String
)

@Immutable
data class VideoStream(
    val url: String,
    val quality: String,
    val headers: Map<String, String>? = null,
    val type: String = "video/mp4" // "application/x-mpegURL" for HLS
)

// Drama Models
@Immutable
data class DramaItem(val id: String, val url: String, val title: String, val coverUrl: String?)

@Immutable
data class DramaSearchPage(val items: List<DramaItem>, val hasNextPage: Boolean)

@Immutable
data class DramaDetail(
    val id: String,
    val url: String,
    val title: String,
    val coverUrl: String?,
    val description: String?,
    val genres: List<String>,
    val status: String?,
    val episodes: List<DramaEpisode>,
    val sourceId: String
)

@Immutable
data class DramaEpisode(
    val id: String,
    val url: String,
    val name: String,
    val number: Float?,
    val date: Instant?,
    val sourceId: String
)

