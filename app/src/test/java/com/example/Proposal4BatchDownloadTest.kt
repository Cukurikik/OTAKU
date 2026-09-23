package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.data.sources.VideoQuality
import com.example.download.AnimeBatchDownloadManager
import com.example.download.BatchEpisodeItem
import com.example.download.MultiServerFallbackResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Proposal4BatchDownloadTest {

    private lateinit var context: Context
    private lateinit var resolver: MultiServerFallbackResolver

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        try {
            val config = Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .build()
            WorkManager.initialize(context, config)
        } catch (_: Exception) {
            // Already initialized
        }
        resolver = MultiServerFallbackResolver(context)
    }

    @Test
    fun testDestinationPathFormulationAndSanitization() {
        val baseDir = File("/data/user/0/com.example/files/downloads")
        val complexTitle = "Jujutsu Kaisen: Season 2 / Shibuya Incident *?\"<>"
        val sanitized = MultiServerFallbackResolver.sanitizeTitle(complexTitle)
        
        assertEquals("Jujutsu Kaisen_ Season 2 _ Shibuya Incident _____", sanitized)
        assertEquals("01", MultiServerFallbackResolver.formatTwoDigits(1))
        assertEquals("09", MultiServerFallbackResolver.formatTwoDigits(9))
        assertEquals("12", MultiServerFallbackResolver.formatTwoDigits(12))
        assertEquals("100", MultiServerFallbackResolver.formatTwoDigits(100))

        val destFile = MultiServerFallbackResolver.getDestinationPath(baseDir, "Solo Leveling", 5)
        assertEquals("Episode_05.mp4", destFile.name)
        assertTrue(destFile.absolutePath.contains("Solo Leveling/Episode_05.mp4"))
    }

    @Test
    fun testQualitySelectorMatchingLogic() {
        val availableQualities = listOf(
            VideoQuality("360p (SD)", "https://stream.example.com/360.mp4"),
            VideoQuality("480p (HQ)", "https://stream.example.com/480.mp4"),
            VideoQuality("720p (HD)", "https://stream.example.com/720.mp4"),
            VideoQuality("1080p (FHD)", "https://stream.example.com/1080.mp4")
        )

        // Exact match 1080p
        val match1080 = resolver.pickQualityMatch(availableQualities, "1080p")
        assertNotNull(match1080)
        assertEquals("1080p (FHD)", match1080?.qualityLabel)

        // Exact match 720p
        val match720 = resolver.pickQualityMatch(availableQualities, "720p")
        assertNotNull(match720)
        assertEquals("720p (HD)", match720?.qualityLabel)

        // Exact match 480p
        val match480 = resolver.pickQualityMatch(availableQualities, "480p")
        assertNotNull(match480)
        assertEquals("480p (HQ)", match480?.qualityLabel)

        // Auto selection selects highest quality
        val matchAuto = resolver.pickQualityMatch(availableQualities, "Auto")
        assertNotNull(matchAuto)
        assertEquals("1080p (FHD)", matchAuto?.qualityLabel)

        // Fallback when 1080p not available and user wants 1080p -> selects highest available under target (720p)
        val lowerQualities = listOf(
            VideoQuality("360p", "https://stream.example.com/360.mp4"),
            VideoQuality("720p", "https://stream.example.com/720.mp4")
        )
        val fallbackMatch = resolver.pickQualityMatch(lowerQualities, "1080p")
        assertEquals("720p", fallbackMatch?.qualityLabel)
    }

    @Test
    fun testBatchDownloadManagerEnqueuing() {
        val manager = AnimeBatchDownloadManager(context)
        val items = (1..5).map { epNum ->
            BatchEpisodeItem(
                episodeNumber = epNum,
                episodeTitle = "Episode $epNum",
                episodeUrl = "https://anime.example.com/ep-$epNum",
                chapterId = "ep_$epNum"
            )
        }

        // Enqueue batch without exceptions
        manager.enqueueBatchItems(
            animeTitle = "Frieren Beyond Journey's End",
            episodes = items,
            targetQuality = "720p",
            sourceId = "kuramanime",
            mangaId = "frieren-s1"
        )

        val flow = manager.getBatchWorkInfoFlow("Frieren Beyond Journey's End", "kuramanime")
        assertNotNull(flow)
    }
}
