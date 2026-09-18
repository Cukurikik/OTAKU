package com.example

import com.example.data.sources.torrent.TorrentSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TorrentSourceTest {

    @Test
    fun testTitleCleaning() {
        val source = TorrentSource(RuntimeEnvironment.getApplication())
        
        // Accessing the private cleanTitle using reflection to test title cleaning
        val cleanTitleMethod = TorrentSource::class.java.getDeclaredMethod("cleanTitle", String::class.java).apply {
            isAccessible = true
        }

        val testCase1 = "[SubsPlease] Blue Box - 01 (1080p) [93F56B08]"
        val result1 = cleanTitleMethod.invoke(source, testCase1) as String
        assertEquals("Blue Box", result1)

        val testCase2 = "[Erai-raws] Re:Zero kara Hajimeru Isekai Seikatsu - 3rd Season - 01 [1080p][HEVC]"
        val result2 = cleanTitleMethod.invoke(source, testCase2) as String
        assertEquals("Re:Zero kara Hajimeru Isekai Seikatsu - 3rd Season", result2)

        val testCase3 = "[HorribleSubs] Fairy Tail - 300 [720p].mkv"
        val result3 = cleanTitleMethod.invoke(source, testCase3) as String
        assertEquals("Fairy Tail", result3)
    }

    @Test
    fun testGetChaptersAndDetail() = runBlocking {
        val source = TorrentSource(RuntimeEnvironment.getApplication())
        val magnetUrl = "magnet:?xt=urn:btih:d123456789abcdef&dn=Test+Anime"

        // Test getDetail
        val detail = source.getDetail(magnetUrl)
        assertNotNull(detail)
        assertEquals("Test Anime", detail?.title)
        assertEquals("torrent_source", detail?.sourceId)

        // Test getChapters
        val chapters = source.getChapters(magnetUrl).first()
        assertNotNull(chapters)
        assertEquals(1, chapters.size)
        assertEquals("Play P2P Video Stream", chapters[0].title)
        assertEquals(magnetUrl, chapters[0].id)
    }
}
