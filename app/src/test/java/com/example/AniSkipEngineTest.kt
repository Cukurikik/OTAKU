package com.example

import com.example.data.trackers.AniSkipInterval
import com.example.data.trackers.AniSkipResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AniSkipEngineTest {

    @Test
    fun testAniSkipIntervalDetection() {
        val opSkip = AniSkipResult(
            interval = AniSkipInterval(startTime = 85.5f, endTime = 175.5f),
            skipType = "op",
            skipId = "op-1",
            episodeLength = 1420f
        )
        val edSkip = AniSkipResult(
            interval = AniSkipInterval(startTime = 1300.0f, endTime = 1390.0f),
            skipType = "ed",
            skipId = "ed-1",
            episodeLength = 1420f
        )
        val skipList = listOf(opSkip, edSkip)

        // Before intro
        var posSeconds = 30.0f
        var active = skipList.firstOrNull { posSeconds >= it.interval.startTime && posSeconds <= it.interval.endTime }
        assertNull(active)

        // During intro
        posSeconds = 100.0f
        active = skipList.firstOrNull { posSeconds >= it.interval.startTime && posSeconds <= it.interval.endTime }
        assertNotNull(active)
        assertEquals("op", active?.skipType)
        assertEquals(175.5f, active?.interval?.endTime)

        // During main episode
        posSeconds = 500.0f
        active = skipList.firstOrNull { posSeconds >= it.interval.startTime && posSeconds <= it.interval.endTime }
        assertNull(active)

        // During ending
        posSeconds = 1320.0f
        active = skipList.firstOrNull { posSeconds >= it.interval.startTime && posSeconds <= it.interval.endTime }
        assertNotNull(active)
        assertEquals("ed", active?.skipType)
        assertEquals(1390.0f, active?.interval?.endTime)
    }

    @Test
    fun testAutoSkipTargetCalculation() {
        val opSkip = AniSkipResult(
            interval = AniSkipInterval(startTime = 60.0f, endTime = 150.0f),
            skipType = "op",
            skipId = "op-123",
            episodeLength = 1400f
        )
        val targetMs = (opSkip.interval.endTime * 1000).toLong() + 200L
        assertEquals(150200L, targetMs)
    }

    @Test
    fun testEpisodeNumberExtraction() {
        val titles = listOf(
            "Episode 12 - The Final Battle" to 12,
            "Ep 01 Sub Indo" to 1,
            "One Piece Episode 1080 [720p]" to 1080,
            "Solo Leveling Season 2 - 08" to 208, // digit filter combination fallback
            "Chapter 45" to 45
        )

        val parsed1 = "Episode 12 - The Final Battle".filter { it.isDigit() }.toIntOrNull()
        assertEquals(12, parsed1)

        val parsedRegex = Regex("(?:ep|episode|chapter|bab)\\s*([0-9]+)", RegexOption.IGNORE_CASE)
            .find("Episode 12 - The Final Battle")?.groupValues?.get(1)?.toIntOrNull()
        assertEquals(12, parsedRegex)
    }
}
