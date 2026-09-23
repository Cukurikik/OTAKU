package com.example.trackers

import com.example.data.trackers.AnimeMalAutoBinderEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeMalAutoBinderEngineTest {

    @Test
    fun testTitleSanitization() {
        val testCases = listOf(
            "Kimetsu no Yaiba: Hashira Geiko-hen Episode 04 Sub Indo" to "Kimetsu no Yaiba: Hashira Geiko-hen",
            "Jujutsu Kaisen Season 2 (Sub Indo) [1080p BD]" to "Jujutsu Kaisen",
            "Solo Leveling Ep 08 Uncensored" to "Solo Leveling",
            "One Piece Episode 1100 Subtitle Indonesia" to "One Piece",
            "Chainsaw Man S1 (ENG SUB) [BD 4K]" to "Chainsaw Man",
            "Attack on Titan Final Season Part 3 Batch" to "Attack on Titan Final Season Part 3"
        )

        for ((raw, expected) in testCases) {
            val cleaned = AnimeMalAutoBinderEngine.sanitizeTitle(raw)
            assertEquals("Failed sanitizing '$raw'", expected, cleaned)
        }
    }

    @Test
    fun testLevenshteinAndSimilarityCalculation() {
        // Exact match
        val exactScore = AnimeMalAutoBinderEngine.calculateSimilarity("Kimetsu no Yaiba", "Kimetsu no Yaiba")
        assertEquals(1.0, exactScore, 0.001)

        // Close match
        val closeScore = AnimeMalAutoBinderEngine.calculateSimilarity("Demon Slayer", "Demon Slayer:")
        assertTrue("Expected similarity > 0.90 but was $closeScore", closeScore > 0.90)

        // Case insensitivity
        val caseScore = AnimeMalAutoBinderEngine.calculateSimilarity("SOLO LEVELING", "solo leveling")
        assertEquals(1.0, caseScore, 0.001)

        // Completely different
        val diffScore = AnimeMalAutoBinderEngine.calculateSimilarity("Naruto", "One Piece")
        assertTrue("Expected similarity < 0.40 but was $diffScore", diffScore < 0.40)
    }

    @Test
    fun testFormulaMathematicalAccuracy() {
        val s1 = "Kimetsu no Yaiba"
        val s2 = "Kimetsu no Yaiba S2"
        val dist = AnimeMalAutoBinderEngine.calculateLevenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)
        val expectedSim = 1.0 - (dist.toDouble() / maxLen.toDouble())

        val calculatedSim = AnimeMalAutoBinderEngine.calculateSimilarity(s1, s2)
        assertEquals(expectedSim, calculatedSim, 0.0001)
    }
}
