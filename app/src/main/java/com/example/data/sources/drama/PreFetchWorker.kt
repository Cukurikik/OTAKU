package com.example.data.sources.drama

import com.example.data.ContentRepository
import com.example.data.sources.VideoStreamInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * PreFetchWorker:
 * Background pre-fetching engine for multi-episode series.
 * Pre-resolves stream URLs, server sources, and subtitles for Episode N+1 ahead of time
 * to enable zero-latency instant playback transitions.
 */
class PreFetchWorker(
    private val repository: ContentRepository? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private var activeJob: Job? = null
    private var customFetchBlock: (suspend (String) -> String?)? = null

    fun setCustomFetchBlock(block: suspend (String) -> String?) {
        this.customFetchBlock = block
    }

    /**
     * Pre-fetches stream URL for the given episode number using callback.
     */
    fun fetchStreamUrl(nextEpisodeNumber: Int, callback: (String?) -> Unit) {
        val targetId = "ep_$nextEpisodeNumber"
        fetchStreamUrl(targetId, "", callback)
    }

    /**
     * Pre-fetches stream URL for the target episode string identifier/URL.
     */
    fun fetchStreamUrl(
        episodeUrlOrId: String,
        sourceId: String = "",
        callback: (String?) -> Unit
    ) {
        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                val streamUrl = prefetchUrl(episodeUrlOrId, sourceId)
                callback(streamUrl)
            } catch (e: Exception) {
                callback(null)
            }
        }
    }

    /**
     * Pre-fetches full VideoStreamInfo (including subtitles & server mirrors).
     */
    fun fetchStreamInfo(
        episodeUrlOrId: String,
        sourceId: String = "",
        callback: ((VideoStreamInfo?) -> Unit)? = null
    ) {
        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                val info = prefetch(episodeUrlOrId, sourceId)
                callback?.invoke(info)
            } catch (e: Exception) {
                callback?.invoke(null)
            }
        }
    }

    /**
     * Suspending function to resolve VideoStreamInfo in background.
     */
    suspend fun prefetch(episodeUrlOrId: String, sourceId: String): VideoStreamInfo? {
        if (episodeUrlOrId.isBlank()) return null

        if (repository != null && sourceId.isNotBlank()) {
            val repo = repository
            return runCatching {
                repo.getVideoStream(episodeUrlOrId, sourceId).firstOrNull()
            }.getOrNull()
        }

        val custom = customFetchBlock?.invoke(episodeUrlOrId)
        if (custom != null) {
            return VideoStreamInfo(
                episodeId = episodeUrlOrId,
                embedUrl = custom,
                qualities = listOf(
                    com.example.data.sources.VideoQuality("Direct Stream", custom, custom.contains(".m3u8"))
                )
            )
        }

        return null
    }

    suspend fun prefetchUrl(episodeUrlOrId: String, sourceId: String): String? {
        val custom = customFetchBlock?.invoke(episodeUrlOrId)
        if (!custom.isNullOrBlank()) return custom

        val info = prefetch(episodeUrlOrId, sourceId)
        return info?.qualities?.firstOrNull()?.url ?: info?.embedUrl
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
    }
}
