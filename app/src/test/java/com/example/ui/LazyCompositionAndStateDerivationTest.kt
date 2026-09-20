package com.example.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.models.Chapter
import com.example.data.models.ChapterEntry
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.base.DramaEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LazyCompositionAndStateDerivationTest {

    @Test
    fun testImmutableDataModelsInstantiation() {
        val dramaItem = ContentItem(
            id = "drama_101",
            title = "Pertemuan di Tengah Awan",
            type = ContentType.DRAMA,
            seasonNumber = 1,
            subtitleStatus = "Sub Indo"
        )
        assertNotNull(dramaItem)
        assertEquals(ContentType.DRAMA, dramaItem.type)

        val chapter = Chapter(
            id = "ch_01",
            title = "Episode 1",
            url = "https://example.com/ep1",
            lastWatchedPositionMs = 300000L,
            totalDurationMs = 600000L
        )
        assertNotNull(chapter)
        assertEquals(300000L, chapter.lastWatchedPositionMs)

        val dramaEpisode = DramaEpisode(
            episodeNumber = 1,
            url = "https://example.com/ep1",
            title = "Episode 1"
        )
        assertEquals("Episode 1", dramaEpisode.title)
    }

    @Test
    fun testDerivedStateOfProgressCalculation() {
        var currentPositionMs by mutableLongStateOf(0L)
        val totalDurationMs = 1_000_000L // 1000 seconds

        // Formula: State_progress = derivedStateOf { CurrentPosition / TotalDuration }
        val derivedProgress by derivedStateOf {
            if (totalDurationMs > 0L) {
                (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        }

        // Initial state at 0ms
        assertEquals(0.0f, derivedProgress, 0.001f)

        // Progress at 250s (25%)
        currentPositionMs = 250_000L
        assertEquals(0.25f, derivedProgress, 0.001f)

        // Progress at 500s (50%)
        currentPositionMs = 500_000L
        assertEquals(0.50f, derivedProgress, 0.001f)

        // Progress over total duration (clamped to 100%)
        currentPositionMs = 1_200_000L
        assertEquals(1.0f, derivedProgress, 0.001f)
    }

    @Test
    fun testRecompositionIsolationWithDerivedState() {
        var rawMilliseconds by mutableLongStateOf(100L)
        var calculationCount = 0

        // Derived state grouping by discrete percentage bucket (e.g. for low-frequency UI indicators)
        val progressPercentageBucket by derivedStateOf {
            calculationCount++
            (rawMilliseconds / 10_000L).toInt() // Group in 10-second buckets
        }

        // Access derived state initial value
        val initialBucket = progressPercentageBucket
        assertEquals(0, initialBucket)
        assertEquals(1, calculationCount)

        // Small millisecond changes within same bucket should not change derived bucket value
        rawMilliseconds = 150L
        val sameBucket1 = progressPercentageBucket
        assertEquals(0, sameBucket1)

        rawMilliseconds = 200L
        val sameBucket2 = progressPercentageBucket
        assertEquals(0, sameBucket2)

        // Cross bucket boundary (e.g. 15,000ms = 15s -> bucket 1)
        rawMilliseconds = 15_000L
        val nextBucket = progressPercentageBucket
        assertEquals(1, nextBucket)
    }
}
