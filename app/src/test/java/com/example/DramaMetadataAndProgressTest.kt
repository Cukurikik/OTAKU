package com.example

import com.example.data.models.Chapter
import com.example.data.models.ChapterEntry
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.ui.screens.resolveSeasonNumber
import com.example.ui.screens.resolveSubtitleStatus
import org.junit.Assert.*
import org.junit.Test

class DramaMetadataAndProgressTest {

    @Test
    fun testResolveSubtitleStatus() {
        // Explicit status should be preserved
        assertEquals("Raw", resolveSubtitleStatus("Queen of Tears", "Raw"))
        assertEquals("Sub Indo", resolveSubtitleStatus("Queen of Tears", "Sub Indo"))
        assertEquals("Eng Sub", resolveSubtitleStatus("Queen of Tears", "Eng Sub"))

        // Implicit extraction from title strings
        assertEquals("Sub Indo", resolveSubtitleStatus("Queen of Tears Episode 1 Sub Indo", ""))
        assertEquals("Sub Indo", resolveSubtitleStatus("Vincenzo Ep 5 Subtitle Indonesia", ""))
        assertEquals("Raw", resolveSubtitleStatus("Taxi Driver Episode 10 [RAW]", ""))
        assertEquals("Eng Sub", resolveSubtitleStatus("Moving Ep 12 English Sub", ""))
        assertEquals("Eng Sub", resolveSubtitleStatus("All of Us Are Dead Ep 3 EngSub", ""))
        assertEquals("Hardsub", resolveSubtitleStatus("Weak Hero Class 1 Ep 8 Hardsub Indo", ""))
        
        // Default fallback for aggregator
        assertEquals("Sub Indo", resolveSubtitleStatus("Alchemy of Souls Ep 1", ""))
    }

    @Test
    fun testResolveSeasonNumber() {
        // Explicit season
        assertEquals(2, resolveSeasonNumber("Taxi Driver", 2))
        assertEquals(1, resolveSeasonNumber("Hospital Playlist", 1))

        // Regex parsing from titles
        assertEquals(2, resolveSeasonNumber("Taxi Driver Season 2 Ep 4", null))
        assertEquals(3, resolveSeasonNumber("Sweet Home S3 Episode 1", null))
        assertEquals(1, resolveSeasonNumber("Squid Game season 1", null))
        assertEquals(2, resolveSeasonNumber("The Glory S02", null))
        assertNull(resolveSeasonNumber("Moving Episode 1", null))
    }

    @Test
    fun testProgressCalculationFormula() {
        // Formula: P = min(100%, (T_last / T_total) * 100%)
        val totalDurationMs = 3600_000L // 60 minutes
        val lastPositionMs = 2700_000L // 45 minutes

        val progressPercentage = (lastPositionMs.toDouble() / totalDurationMs.toDouble()) * 100.0
        val clampedPercentage = minOf(100.0, progressPercentage)
        assertEquals(75.0, clampedPercentage, 0.01)

        val progressFloat = (lastPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        assertEquals(0.75f, progressFloat, 0.001f)

        // Overflow case: last position >= total duration
        val overflowPositionMs = 4000_000L
        val overflowFloat = (overflowPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        assertEquals(1.0f, overflowFloat, 0.001f)

        // Zero case
        val zeroPositionMs = 0L
        val zeroFloat = (zeroPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        assertEquals(0.0f, zeroFloat, 0.001f)
    }

    @Test
    fun testContentItemAndChapterMetadataModel() {
        val dramaItem = ContentItem(
            id = "https://example.com/drama/queen-of-tears",
            title = "Queen of Tears Season 1",
            source = "Viki",
            type = ContentType.DRAMA,
            seasonNumber = 1,
            subtitleStatus = "Sub Indo",
            castList = listOf("Kim Soo-hyun", "Kim Ji-won", "Park Sung-hoon", "Kwak Dong-yeon"),
            lastWatchedPositionMs = 1800_000L,
            totalDurationMs = 3600_000L
        )

        assertEquals(1, dramaItem.seasonNumber)
        assertEquals("Sub Indo", dramaItem.subtitleStatus)
        assertEquals(4, dramaItem.castList.size)
        assertTrue(dramaItem.castList.contains("Kim Soo-hyun"))
        assertEquals(1800_000L, dramaItem.lastWatchedPositionMs)
        assertEquals(3600_000L, dramaItem.totalDurationMs)

        val episodeChapter = Chapter(
            id = "ep-1",
            title = "Episode 1",
            url = "https://example.com/drama/queen-of-tears/ep-1",
            seasonNumber = 1,
            subtitleStatus = "Sub Indo",
            castList = dramaItem.castList,
            lastWatchedPositionMs = 900_000L,
            totalDurationMs = 3600_000L
        )

        assertEquals(1, episodeChapter.seasonNumber)
        assertEquals("Sub Indo", episodeChapter.subtitleStatus)
        assertEquals(900_000L, episodeChapter.lastWatchedPositionMs)
        assertEquals(3600_000L, episodeChapter.totalDurationMs)
    }
}
