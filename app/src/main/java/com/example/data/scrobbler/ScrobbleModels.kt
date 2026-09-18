package com.example.data.scrobbler

import kotlinx.serialization.Serializable

/**
 * Platform definitions for Multi-Service Auto-Scrobbler & Sync Engine.
 */
enum class ScrobblePlatform(
    val id: String,
    val displayName: String,
    val protocol: String,
    val apiType: String,
    val logoUrl: String
) {
    MY_ANIME_LIST(
        id = "myanimelist",
        displayName = "MyAnimeList",
        protocol = "OAuth2 PKCE",
        apiType = "REST API v2",
        logoUrl = "https://upload.wikimedia.org/wikipedia/commons/7/7a/MyAnimeList_Logo.png"
    ),
    ANI_LIST(
        id = "anilist",
        displayName = "AniList",
        protocol = "OAuth2 Bearer",
        apiType = "GraphQL v2",
        logoUrl = "https://anilist.co/img/icons/android-chrome-512x512.png"
    ),
    KITSU(
        id = "kitsu",
        displayName = "Kitsu",
        protocol = "OAuth2 Password / Token",
        apiType = "REST API JSON:API",
        logoUrl = "https://kitsu.io/kitsu-256-ed442f7567271af715884ca06a9f6871.png"
    );

    companion object {
        fun fromId(id: String): ScrobblePlatform? {
            return values().find { it.id.equals(id, ignoreCase = true) }
        }
    }
}

/**
 * Media type representation for sync and scrobbling.
 */
enum class MediaSyncType {
    ANIME,
    MANGA
}

/**
 * Standardized status across tracking platforms.
 */
enum class ScrobbleStatus(val malValue: String, val anilistValue: String, val kitsuValue: String) {
    WATCHING("watching", "CURRENT", "current"),
    READING("reading", "CURRENT", "current"),
    COMPLETED("completed", "COMPLETED", "completed"),
    PLAN_TO_WATCH("plan_to_watch", "PLANNING", "planned"),
    PLAN_TO_READ("plan_to_read", "PLANNING", "planned"),
    ON_HOLD("on_hold", "PAUSED", "on_hold"),
    DROPPED("dropped", "DROPPED", "dropped");

    companion object {
        fun fromMal(statusStr: String, isAnime: Boolean): ScrobbleStatus {
            return when (statusStr.lowercase()) {
                "watching" -> WATCHING
                "reading" -> READING
                "completed" -> COMPLETED
                "plan_to_watch" -> PLAN_TO_WATCH
                "plan_to_read" -> PLAN_TO_READ
                "on_hold" -> ON_HOLD
                "dropped" -> DROPPED
                else -> if (isAnime) WATCHING else READING
            }
        }

        fun fromAniList(statusStr: String, isAnime: Boolean): ScrobbleStatus {
            return when (statusStr.uppercase()) {
                "CURRENT" -> if (isAnime) WATCHING else READING
                "COMPLETED" -> COMPLETED
                "PLANNING" -> if (isAnime) PLAN_TO_WATCH else PLAN_TO_READ
                "PAUSED" -> ON_HOLD
                "DROPPED" -> DROPPED
                else -> if (isAnime) WATCHING else READING
            }
        }

        fun fromKitsu(statusStr: String, isAnime: Boolean): ScrobbleStatus {
            return when (statusStr.lowercase()) {
                "current" -> if (isAnime) WATCHING else READING
                "completed" -> COMPLETED
                "planned" -> if (isAnime) PLAN_TO_WATCH else PLAN_TO_READ
                "on_hold" -> ON_HOLD
                "dropped" -> DROPOUT_SAFE(isAnime)
                else -> if (isAnime) WATCHING else READING
            }
        }

        private fun DROPOUT_SAFE(isAnime: Boolean): ScrobbleStatus = DROPPED
    }
}

/**
 * Configuration payload for Auto-Scrobbler Engine.
 * Matches the functional specification:
 * {
 *   "scrobble_config": {
 *     "completion_threshold": 0.85,
 *     "target_platforms": ["myanimelist", "anilist", "kitsu"],
 *     "auto_sync_interval_minutes": 15
 *   }
 * }
 */
@Serializable
data class ScrobbleConfig(
    val completionThreshold: Double = 0.85,
    val targetPlatforms: List<String> = listOf("myanimelist", "anilist", "kitsu"),
    val autoSyncIntervalMinutes: Long = 15L
)

/**
 * Representation of a media item imported from MyAnimeList / AniList / Kitsu watchlist/reading list.
 */
data class ImportedWatchlistItem(
    val remoteId: Long,
    val title: String,
    val coverUrl: String,
    val mediaType: MediaSyncType,
    val progress: Int,
    val totalCount: Int,
    val status: ScrobbleStatus,
    val platform: ScrobblePlatform,
    val score: Float = 0f
)

/**
 * Result metrics for two-way synchronization.
 */
data class SyncResult(
    val platform: ScrobblePlatform,
    val importedCount: Int,
    val updatedCount: Int,
    val pushedCount: Int,
    val success: Boolean,
    val message: String = ""
)

/**
 * Aggregated summary for Two-Way Status Sync across all platforms.
 */
data class TwoWaySyncSummary(
    val results: List<SyncResult>,
    val totalImported: Int = results.sumOf { it.importedCount },
    val totalUpdated: Int = results.sumOf { it.updatedCount },
    val totalPushed: Int = results.sumOf { it.pushedCount },
    val timestamp: Long = System.currentTimeMillis()
)
