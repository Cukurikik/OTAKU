package com.example.media

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl

/**
 * AdaptiveLoadControlBuilder:
 * Advanced buffer management engine for high-bitrate video streams (Drama / Anime).
 *
 * Formulations & Constraints:
 * - Buffer_capacity = Bitrate_stream * t_buffer_target
 * - MinBuffer = 60000 ms (60 seconds)
 * - MaxBuffer = 180000 ms (180 seconds / 3 minutes)
 * - bufferForPlaybackMs = 2500 ms (2.5 seconds)
 * - bufferForPlaybackAfterRebufferMs = 5000 ms (5 seconds)
 *
 * Mitigates third-party CDN bandwidth throttling after 10-15 minutes of continuous streaming,
 * preventing playback freezes and reset friction.
 */
@OptIn(UnstableApi::class)
class AdaptiveLoadControlBuilder(
    val minBufferMs: Int = DEFAULT_MIN_BUFFER_MS,
    val maxBufferMs: Int = DEFAULT_MAX_BUFFER_MS,
    val bufferForPlaybackMs: Int = DEFAULT_BUFFER_FOR_PLAYBACK_MS,
    val bufferForPlaybackAfterRebufferMs: Int = DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
    val backBufferDurationMs: Int = DEFAULT_BACK_BUFFER_MS,
    val retainBackBufferFromKeyframe: Boolean = true
) {

    companion object {
        const val DEFAULT_MIN_BUFFER_MS = 60_000   // 60 seconds
        const val DEFAULT_MAX_BUFFER_MS = 180_000  // 180 seconds (3 minutes)
        const val DEFAULT_BUFFER_FOR_PLAYBACK_MS = 2500
        const val DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5000
        const val DEFAULT_BACK_BUFFER_MS = 30_000

        /**
         * Formulasi:
         * Buffer_capacity = Bitrate_stream * t_buffer_target
         *
         * @param bitrateBps Bitrate in bits per second (e.g. 5_000_000 for 5 Mbps 1080p stream)
         * @param bufferTargetSec Target buffer duration in seconds (default: 180s)
         * @return Required buffer capacity in bytes
         */
        fun calculateBufferCapacityBytes(bitrateBps: Long, bufferTargetSec: Int = 180): Long {
            if (bitrateBps <= 0L || bufferTargetSec <= 0) return 0L
            return (bitrateBps * bufferTargetSec) / 8L
        }
    }

    /**
     * Builds configured ExoPlayer DefaultLoadControl instance with 60s - 180s buffer windows.
     */
    fun buildDefaultLoadControl(): DefaultLoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs
            )
            .setBackBuffer(backBufferDurationMs, retainBackBufferFromKeyframe)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }
}
