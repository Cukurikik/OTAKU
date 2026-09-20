package com.example.ui.player

/**
 * Controller for managing dynamic real-time subtitle sync offset.
 * Complies with Proposal 5 Technical Specification:
 * - Mathematical formula: T_display = T_current + Delta T_offset
 * - Offset range constraint: -5.0s <= Delta T_offset <= +5.0s (-5000ms to +5000ms)
 */
class SubtitleSyncController(
    private var initialOffsetMs: Long = 0L
) {

    init {
        initialOffsetMs = initialOffsetMs.coerceIn(-5000L, 5000L)
    }

    /**
     * Applies offset in seconds with clamping between -5.0s and +5.0s.
     * Returns the resulting offset in milliseconds.
     */
    fun applyOffset(deltaSeconds: Float): Long {
        val clampedOffset = deltaSeconds.coerceIn(-5.0f, 5.0f)
        initialOffsetMs = (clampedOffset * 1000).toLong()
        return initialOffsetMs
    }

    /**
     * Applies offset directly in milliseconds with clamping between -5000ms and +5000ms.
     */
    fun applyOffsetMs(offsetMs: Long): Long {
        initialOffsetMs = offsetMs.coerceIn(-5000L, 5000L)
        return initialOffsetMs
    }

    /**
     * Incrementally adjusts the offset in milliseconds.
     */
    fun adjustOffsetMs(deltaMs: Long): Long {
        return applyOffsetMs(initialOffsetMs + deltaMs)
    }

    /**
     * Gets the current offset in milliseconds.
     */
    fun getOffsetMs(): Long = initialOffsetMs

    /**
     * Gets the current offset in seconds (float).
     */
    fun getOffsetSeconds(): Float = initialOffsetMs / 1000.0f

    /**
     * Calculates the effective display timestamp for subtitle sampling:
     * T_display = T_current + Delta T_offset
     */
    fun calculateDisplayTime(currentPositionMs: Long): Long {
        return (currentPositionMs + initialOffsetMs).coerceAtLeast(0L)
    }

    /**
     * Shifts cue timestamps by the configured offset.
     */
    fun adjustCueTimestamps(cues: List<SubtitleCue>): List<SubtitleCue> {
        return SubtitleParser.applyOffset(cues, initialOffsetMs)
    }
}
