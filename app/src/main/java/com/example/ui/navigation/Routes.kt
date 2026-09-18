package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object LibraryRoute

@Serializable
object ProfileRoute

@Serializable
object ExtensionsRoute

@Serializable
object HistoryRoute

@Serializable
object DownloadQueueRoute

@Serializable
object UpdatesRoute

@Serializable
object MigrationRoute

@Serializable
data class DetailRoute(val itemId: String, val sourceId: String)

@Serializable
data class ReaderRoute(
    val chapterId: String,
    val title: String,
    val itemId: String,
    val sourceId: String,
    val initialPage: Int = 0
)

@Serializable
data class PlayerRoute(
    val episodeId: String,
    val title: String,
    val itemId: String,
    val sourceId: String,
    val startPositionMs: Long = 0L
)

@Serializable
object SettingsRoute

@Serializable
object StatisticsRoute

@Serializable
object BackupSettingsRoute

@kotlinx.serialization.Serializable
data class CloudflareBypassRoute(val url: String)
@Serializable object CloudSyncRoute
@Serializable object LanShareRoute
