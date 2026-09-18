package com.example.scrobbler

import com.example.data.scrobbler.MediaSyncType
import com.example.data.scrobbler.ScrobblePlatform
import com.example.data.scrobbler.ScrobbleStatus
import com.example.data.scrobbler.ScrobbleThresholdDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleThresholdDetectorTest {

    @Test
    fun testExact85PercentCompletionThresholdFormula() {
        val durationMs = 1_440_000L // 24 minutes

        // 84.99% -> Below threshold, should not trigger
        val positionJustBelow = (durationMs * 0.8499).toLong()
        assertFalse(
            "Position at 84.99% should NOT trigger scrobble",
            ScrobbleThresholdDetector.shouldTriggerScrobble(positionJustBelow, durationMs, 0.85)
        )

        // 85.0% -> Exactly at threshold, must trigger
        val positionExact = (durationMs * 0.85).toLong()
        assertTrue(
            "Position at exactly 85.0% MUST trigger scrobble",
            ScrobbleThresholdDetector.shouldTriggerScrobble(positionExact, durationMs, 0.85)
        )

        // 90.0% -> Above threshold, must trigger
        val positionAbove = (durationMs * 0.90).toLong()
        assertTrue(
            "Position at 90.0% MUST trigger scrobble",
            ScrobbleThresholdDetector.shouldTriggerScrobble(positionAbove, durationMs, 0.85)
        )

        // 100.0% -> Finished video, must trigger
        assertTrue(
            "Position at 100.0% MUST trigger scrobble",
            ScrobbleThresholdDetector.shouldTriggerScrobble(durationMs, durationMs, 0.85)
        )
    }

    @Test
    fun testInvalidOrZeroDurationsReturnFalse() {
        assertFalse(
            "Zero duration must return false",
            ScrobbleThresholdDetector.shouldTriggerScrobble(1000L, 0L, 0.85)
        )
        assertFalse(
            "Negative duration must return false",
            ScrobbleThresholdDetector.shouldTriggerScrobble(1000L, -100L, 0.85)
        )
        assertFalse(
            "Zero current position must return false",
            ScrobbleThresholdDetector.shouldTriggerScrobble(0L, 1000L, 0.85)
        )
        assertFalse(
            "Negative position must return false",
            ScrobbleThresholdDetector.shouldTriggerScrobble(-50L, 1000L, 0.85)
        )
    }

    @Test
    fun testPlaybackRatioClamping() {
        assertEquals(0.85, ScrobbleThresholdDetector.calculatePlaybackRatio(850L, 1000L), 0.001)
        assertEquals(0.0, ScrobbleThresholdDetector.calculatePlaybackRatio(0L, 1000L), 0.001)
        assertEquals(0.0, ScrobbleThresholdDetector.calculatePlaybackRatio(500L, 0L), 0.001)
        assertEquals(1.0, ScrobbleThresholdDetector.calculatePlaybackRatio(1500L, 1000L), 0.001)
    }

    @Test
    fun testMangaLastPageDetection() {
        val totalPages = 24

        // User on page 10 of 24 -> false
        assertFalse(
            "Page 10 of 24 should NOT trigger chapter completion",
            ScrobbleThresholdDetector.shouldTriggerMangaScrobble(currentPage = 10, totalPages = totalPages)
        )

        // User on page 22 of 24 (2nd to last) -> false
        assertFalse(
            "Page 22 of 24 should NOT trigger chapter completion",
            ScrobbleThresholdDetector.shouldTriggerMangaScrobble(currentPage = 22, totalPages = totalPages)
        )

        // User on page 23 of 24 (final page, 0-indexed) -> true
        assertTrue(
            "Page 23 of 24 (last page) MUST trigger chapter completion",
            ScrobbleThresholdDetector.shouldTriggerMangaScrobble(currentPage = 23, totalPages = totalPages)
        )

        // Invalid total pages -> false
        assertFalse(
            "Zero total pages should return false",
            ScrobbleThresholdDetector.shouldTriggerMangaScrobble(currentPage = 0, totalPages = 0)
        )
    }

    @Test
    fun testDetermineStatusTransition() {
        // Anime with 12 episodes, watched episode 6 -> WATCHING
        assertEquals(
            ScrobbleStatus.WATCHING,
            ScrobbleThresholdDetector.determineStatus(progressNumber = 6, totalCount = 12, mediaType = MediaSyncType.ANIME)
        )

        // Anime with 12 episodes, watched episode 12 -> COMPLETED
        assertEquals(
            ScrobbleStatus.COMPLETED,
            ScrobbleThresholdDetector.determineStatus(progressNumber = 12, totalCount = 12, mediaType = MediaSyncType.ANIME)
        )

        // Manga with 50 chapters, read chapter 25 -> READING
        assertEquals(
            ScrobbleStatus.READING,
            ScrobbleThresholdDetector.determineStatus(progressNumber = 25, totalCount = 50, mediaType = MediaSyncType.MANGA)
        )

        // Manga with 50 chapters, read chapter 50 -> COMPLETED
        assertEquals(
            ScrobbleStatus.COMPLETED,
            ScrobbleThresholdDetector.determineStatus(progressNumber = 50, totalCount = 50, mediaType = MediaSyncType.MANGA)
        )

        // Unknown total count (0) -> Keeps active status
        assertEquals(
            ScrobbleStatus.WATCHING,
            ScrobbleThresholdDetector.determineStatus(progressNumber = 100, totalCount = 0, mediaType = MediaSyncType.ANIME)
        )
    }

    @Test
    fun testPlatformMetadataMatrix() {
        val mal = ScrobblePlatform.MY_ANIME_LIST
        assertEquals("OAuth2 PKCE", mal.protocol)
        assertEquals("REST API v2", mal.apiType)

        val anilist = ScrobblePlatform.ANI_LIST
        assertEquals("OAuth2 Bearer", anilist.protocol)
        assertEquals("GraphQL v2", anilist.apiType)

        val kitsu = ScrobblePlatform.KITSU
        assertEquals("OAuth2 Password / Token", kitsu.protocol)
        assertEquals("REST API JSON:API", kitsu.apiType)
    }
}
