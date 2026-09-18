package com.example

import com.example.data.trackers.AutoBindMappingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBindMappingEngineTest {

    @Test
    fun testLevenshteinDistance() {
        val distance1 = AutoBindMappingEngine.calculateLevenshteinDistance("kitten", "sitting")
        assertEquals(3, distance1) // k->s, e->i, +g

        val distance2 = AutoBindMappingEngine.calculateLevenshteinDistance("flitten", "flitten")
        assertEquals(0, distance2)
        
        val distance3 = AutoBindMappingEngine.calculateLevenshteinDistance("One Piece", "One Piece (TV)")
        assertEquals(5, distance3) // " (TV)"
    }

    @Test
    fun testSimilarityPercentage() {
        val score1 = AutoBindMappingEngine.calculateSimilarity("Naruto Shippuden", "Naruto: Shippuden")
        assertTrue("Score should be high, it's: $score1", score1 > 0.85)

        val score2 = AutoBindMappingEngine.calculateSimilarity("Bleach: Thousand-Year Blood War", "Bleach: Sennen Kessen-hen")
        // Different languages might fail exact match, but testing the math
        assertTrue("Score should be calculated", score2 < 0.85)

        val score3 = AutoBindMappingEngine.calculateSimilarity("Jujutsu Kaisen Season 2", "Jujutsu Kaisen 2nd Season")
        // Lengths: 23, 25. distance is small.
        assertTrue("Score should be calculated", score3 > 0.5)
    }

    @Test
    fun testRegexCleaning() {
        val title = "One Piece (Subtitle Indonesia) [720p]"
        val cleaned = title.replace(Regex("\\(.*?\\)"), "").replace(Regex("\\[.*?\\]"), "").trim()
        assertEquals("One Piece", cleaned)
    }
}
