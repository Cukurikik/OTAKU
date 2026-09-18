package com.example.data.scrobbler

/**
 * Auto-Scrobbling Threshold Detector.
 *
 * Implements completion threshold formulation:
 * ScrobbleTrigger = TRUE if (T_current / T_total >= 0.85), FALSE otherwise.
 * For comics/manga: ScrobbleTrigger = TRUE when reaching the last page of a chapter.
 */
object ScrobbleThresholdDetector {

    const val DEFAULT_THRESHOLD = 0.85

    /**
     * Evaluates video playback progress against completion threshold.
     *
     * @param currentPosition Current position in milliseconds
     * @param duration Total video duration in milliseconds
     * @param threshold Custom threshold ratio (default 0.85)
     * @return true if completion threshold is met or exceeded
     */
    fun shouldTriggerScrobble(
        currentPosition: Long,
        duration: Long,
        threshold: Double = DEFAULT_THRESHOLD
    ): Boolean {
        if (duration <= 0L || currentPosition <= 0L) return false
        val progress = currentPosition.toDouble() / duration.toDouble()
        return progress >= threshold
    }

    /**
     * Calculates the exact playback ratio clamped between 0.0 and 1.0.
     */
    fun calculatePlaybackRatio(currentPosition: Long, duration: Long): Double {
        if (duration <= 0L) return 0.0
        return (currentPosition.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0)
    }

    /**
     * Evaluates manga reading progress for scrobble trigger.
     * Triggers when the user is on the final page of the chapter.
     *
     * @param currentPage 0-indexed page number
     * @param totalPages Total page count in current chapter
     * @return true if user has reached the final page
     */
    fun shouldTriggerMangaScrobble(currentPage: Int, totalPages: Int): Boolean {
        if (totalPages <= 0) return false
        return currentPage >= (totalPages - 1)
    }

    /**
     * Determines whether the status should transition to COMPLETED based on total episodes/chapters.
     * If current episode/chapter number matches or exceeds the known total, mark as COMPLETED;
     * otherwise keep as WATCHING (Anime) or READING (Manga).
     */
    fun determineStatus(
        progressNumber: Int,
        totalCount: Int,
        mediaType: MediaSyncType
    ): ScrobbleStatus {
        return if (totalCount > 0 && progressNumber >= totalCount) {
            ScrobbleStatus.COMPLETED
        } else {
            if (mediaType == MediaSyncType.ANIME) ScrobbleStatus.WATCHING else ScrobbleStatus.READING
        }
    }
}
