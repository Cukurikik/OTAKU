package eu.kanade.tachiyomi.animesource.model

import okhttp3.Headers

open class Video(
    val url: String,
    val quality: String,
    var videoUrl: String?,
    val headers: Headers? = null,
    val subtitleTracks: List<Subtitle> = emptyList(),
    val audioTracks: List<Audio> = emptyList()
)

class Subtitle(val url: String, val lang: String)
class Audio(val url: String, val lang: String)
