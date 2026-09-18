package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.workers.CacheJanitor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class CacheJanitorTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clean test environment
        File(context.filesDir, "downloads").deleteRecursively()
        File(context.cacheDir, "stagingDir").deleteRecursively()
        File(context.cacheDir, "play_urls").deleteRecursively()
    }

    @Test
    fun testOrphanStagingDirectoryDeleted() = runTest {
        val sourceId = "komikcast"
        val mangaId = "martial-peak"
        val activeChapter = "chapter-100"
        val orphanChapter = "chapter-99"

        val mangaDir = File(context.filesDir, "downloads/$sourceId/$mangaId").apply { mkdirs() }
        
        // Active staging directory
        val activeStaging = File(mangaDir, "tmp_$activeChapter").apply {
            mkdirs()
            File(this, "001.jpg").writeText("dummy image data")
        }

        // Orphan staging directory (not in active download IDs)
        val orphanStaging = File(mangaDir, "tmp_$orphanChapter").apply {
            mkdirs()
            File(this, "001.jpg").writeText("orphan image data")
        }

        assertTrue(activeStaging.exists())
        assertTrue(orphanStaging.exists())

        // Run Janitor with only activeChapter in the active set
        val cleaned = CacheJanitor.cleanOrphanStagingDirectories(
            context = context,
            activeDownloadChapterIds = setOf(activeChapter)
        )

        assertEquals(1, cleaned)
        assertTrue("Active staging should still exist", activeStaging.exists())
        assertFalse("Orphan staging should have been deleted", orphanStaging.exists())
    }

    @Test
    fun testExpiredStagingDirectoryDeletedEvenIfActive() = runTest {
        val sourceId = "asurascans"
        val mangaId = "solo-leveling"
        val expiredChapter = "chapter-50"

        val mangaDir = File(context.filesDir, "downloads/$sourceId/$mangaId").apply { mkdirs() }
        val expiredStaging = File(mangaDir, "tmp_$expiredChapter").apply {
            mkdirs()
            File(this, "001.jpg").writeText("old image data")
            // Set timestamp to 48 hours ago
            setLastModified(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(48))
        }

        val cleaned = CacheJanitor.cleanOrphanStagingDirectories(
            context = context,
            activeDownloadChapterIds = setOf(expiredChapter), // Marked active but expired > 24 hours
            maxAgeMillis = TimeUnit.HOURS.toMillis(24)
        )

        assertEquals(1, cleaned)
        assertFalse("Expired staging directory must be deleted", expiredStaging.exists())
    }

    @Test
    fun testCacheStagingDirectoryAndPlayUrlsCleaned() = runTest {
        val cacheStagingDir = File(context.cacheDir, "stagingDir").apply { mkdirs() }
        val orphanCacheDir = File(cacheStagingDir, "tmp_stream_123").apply {
            mkdirs()
            File(this, "chunk.ts").writeText("video chunk")
        }

        val playUrlsDir = File(context.cacheDir, "play_urls").apply { mkdirs() }
        val expiredToken = File(playUrlsDir, "token_old.txt").apply {
            writeText("https://stream.example.com/video.m3u8")
            setLastModified(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(30))
        }

        val cleaned = CacheJanitor.cleanOrphanStagingDirectories(
            context = context,
            activeDownloadChapterIds = emptySet(),
            maxAgeMillis = TimeUnit.HOURS.toMillis(24)
        )

        assertEquals(2, cleaned)
        assertFalse(orphanCacheDir.exists())
        assertFalse(expiredToken.exists())
    }
}
