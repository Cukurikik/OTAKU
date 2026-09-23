package com.example.data.sources.drama

import com.example.data.repository.PlaybackStateRepository
import com.example.data.sources.VideoStreamInfo

/**
 * DramaAutoQueueEngine:
 * Smart queueing and seamless auto-play transition engine for multi-episode series.
 *
 * Implements formulation:
 * t_trigger = t_duration * 0.80
 * P_next(N+1) = PreFetchStream(N+1) if t_current >= t_trigger else Idle
 */
class DramaAutoQueueEngine(
    private val repository: PlaybackStateRepository,
    private val preFetchWorker: PreFetchWorker
) {

    var nextEpisodeStream: String? = null
        private set

    var nextEpisodeStreamInfo: VideoStreamInfo? = null
        private set

    var isPreFetched: Boolean = false
        private set

    private var activeEpisodeId: String = ""
    private var activeEpisodeNumber: Int = -1
    private var queuedNextEpisodeUrl: String? = null
    private var activeSourceId: String = ""

    /**
     * Called on continuous playback progress updates with numeric episode number.
     */
    fun onPlaybackProgressChanged(currentPosMs: Long, totalDurationMs: Long, currentEpisode: Int) {
        if (totalDurationMs <= 0L) return
        repository.savePosition(currentEpisode, currentPosMs, totalDurationMs, currentPosMs >= totalDurationMs * 0.9)

        if (activeEpisodeNumber != currentEpisode) {
            activeEpisodeNumber = currentEpisode
            isPreFetched = false
            nextEpisodeStream = null
            nextEpisodeStreamInfo = null
        }

        val progressRatio = currentPosMs.toDouble() / totalDurationMs.toDouble()
        if (progressRatio >= 0.80 && !isPreFetched) {
            isPreFetched = true
            triggerPreFetchNextEpisode(currentEpisode + 1)
        }
    }

    /**
     * Called on continuous playback progress updates with episode ID and next episode URL.
     */
    fun onPlaybackProgressChanged(
        currentPosMs: Long,
        totalDurationMs: Long,
        currentEpisodeId: String,
        nextEpisodeUrl: String?,
        sourceId: String = "",
        seriesId: String = "",
        seriesTitle: String = ""
    ) {
        if (totalDurationMs <= 0L || currentEpisodeId.isBlank()) return

        val isFinished = currentPosMs >= (totalDurationMs * 0.90).toLong()
        repository.savePosition(
            episodeId = currentEpisodeId,
            currentPosMs = currentPosMs,
            totalDurationMs = totalDurationMs,
            isFinished = isFinished,
            sourceId = sourceId,
            seriesId = seriesId,
            seriesTitle = seriesTitle
        )

        if (activeEpisodeId != currentEpisodeId) {
            activeEpisodeId = currentEpisodeId
            queuedNextEpisodeUrl = nextEpisodeUrl
            activeSourceId = sourceId
            isPreFetched = false
            nextEpisodeStream = null
            nextEpisodeStreamInfo = null
        }

        if (nextEpisodeUrl.isNullOrBlank()) return

        val progressRatio = currentPosMs.toDouble() / totalDurationMs.toDouble()
        if (progressRatio >= 0.80 && !isPreFetched) {
            isPreFetched = true
            triggerPreFetchNextEpisode(nextEpisodeUrl, sourceId)
        }
    }

    /**
     * Triggers background prefetch for the next numeric episode.
     */
    fun triggerPreFetchNextEpisode(nextEpisodeNumber: Int) {
        preFetchWorker.fetchStreamUrl(nextEpisodeNumber) { resolvedUrl ->
            nextEpisodeStream = resolvedUrl
            if (resolvedUrl != null) {
                nextEpisodeStreamInfo = VideoStreamInfo(
                    episodeId = "ep_$nextEpisodeNumber",
                    embedUrl = resolvedUrl,
                    qualities = listOf(
                        com.example.data.sources.VideoQuality("PreFetched", resolvedUrl, resolvedUrl.contains(".m3u8"))
                    )
                )
            }
        }
    }

    /**
     * Triggers background prefetch for the next episode URL and source.
     */
    fun triggerPreFetchNextEpisode(nextEpisodeUrl: String, sourceId: String) {
        preFetchWorker.fetchStreamInfo(nextEpisodeUrl, sourceId) { resolvedInfo ->
            nextEpisodeStreamInfo = resolvedInfo
            nextEpisodeStream = resolvedInfo?.qualities?.firstOrNull()?.url ?: resolvedInfo?.embedUrl
        }
    }

    /**
     * Injects or sets pre-fetched stream directly (useful for tests or immediate resolution).
     */
    fun setPreFetchedStream(streamUrl: String, info: VideoStreamInfo? = null) {
        nextEpisodeStream = streamUrl
        nextEpisodeStreamInfo = info ?: VideoStreamInfo(
            episodeId = "prefetched",
            embedUrl = streamUrl,
            qualities = listOf(com.example.data.sources.VideoQuality("PreFetched", streamUrl, streamUrl.contains(".m3u8")))
        )
        isPreFetched = true
    }

    /**
     * Retrieves and consumes the pre-fetched stream URL for instant transition ($0 ms latency).
     */
    fun getNextStreamForTransition(): String? {
        val stream = nextEpisodeStream
        nextEpisodeStream = null
        nextEpisodeStreamInfo = null
        isPreFetched = false
        return stream
    }

    /**
     * Retrieves and consumes the pre-fetched VideoStreamInfo for instant transition.
     */
    fun getNextStreamInfoForTransition(): VideoStreamInfo? {
        val info = nextEpisodeStreamInfo
        nextEpisodeStream = null
        nextEpisodeStreamInfo = null
        isPreFetched = false
        return info
    }

    /**
     * Resets the engine state for a new playback session.
     */
    fun reset() {
        nextEpisodeStream = null
        nextEpisodeStreamInfo = null
        isPreFetched = false
        activeEpisodeId = ""
        activeEpisodeNumber = -1
        queuedNextEpisodeUrl = null
        preFetchWorker.cancel()
    }
}
