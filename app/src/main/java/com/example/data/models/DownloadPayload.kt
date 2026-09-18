package com.example.data.models

import kotlinx.serialization.Serializable

@Serializable
data class DownloadPayload(
    val sourceId: String,
    val mangaId: String,
    val chapterId: String,
    val directUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val cookies: String? = null,
    val mimeType: String? = null,
    val qualityLabel: String? = null,
    val subtitles: List<SubtitlePayload> = emptyList()
)

@Serializable
data class SubtitlePayload(
    val language: String,
    val url: String,
    val format: String = "vtt"
)
