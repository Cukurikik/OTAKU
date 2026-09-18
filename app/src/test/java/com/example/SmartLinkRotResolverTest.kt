package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.linkrot.AutoMigrationEngine
import com.example.data.linkrot.LinkRotTracker
import com.example.data.models.ContentType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartLinkRotResolverTest {

    private lateinit var context: Context
    private lateinit var linkRotTracker: LinkRotTracker

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        linkRotTracker = LinkRotTracker.getInstance(context)
        linkRotTracker.resetAll()
    }

    @Test
    fun testLinkRotTracker_ConsecutiveFailuresAndReset() {
        val sourceId = "komikcast"
        val mangaId = "solo-leveling-chapter-1"

        // Kondisi awal: 0 kegagalan
        assertEquals(0, linkRotTracker.getFailureCount(sourceId, mangaId))
        assertFalse(linkRotTracker.isLinkRot(sourceId, mangaId))

        // Kegagalan pertama (misal HTTP 502)
        val count1 = linkRotTracker.recordFailure(sourceId, mangaId, "HTTP 502 Bad Gateway")
        assertEquals(1, count1)
        assertEquals(1, linkRotTracker.getFailureCount(sourceId, mangaId))
        assertFalse(linkRotTracker.isLinkRot(sourceId, mangaId))

        // Kegagalan kedua (misal HTTP 404)
        val count2 = linkRotTracker.recordFailure(sourceId, mangaId, "HTTP 404 Not Found")
        assertEquals(2, count2)
        assertFalse(linkRotTracker.isLinkRot(sourceId, mangaId))

        // Kegagalan ketiga (ambang batas link rot tercapai)
        val count3 = linkRotTracker.recordFailure(sourceId, mangaId, "HTTP 404 Not Found")
        assertEquals(3, count3)
        assertTrue(linkRotTracker.isLinkRot(sourceId, mangaId))

        // Reset keberhasilan (jika link pulih atau setelah migrasi sukses)
        linkRotTracker.recordSuccess(sourceId, mangaId)
        assertEquals(0, linkRotTracker.getFailureCount(sourceId, mangaId))
        assertFalse(linkRotTracker.isLinkRot(sourceId, mangaId))
    }

    @Test
    fun testAutoMigrationEngine_LevenshteinDistance() {
        assertEquals(0, AutoMigrationEngine.calculateLevenshteinDistance("Solo Leveling", "Solo Leveling"))
        assertEquals(1, AutoMigrationEngine.calculateLevenshteinDistance("One Piece", "One Pieces"))
        assertEquals(3, AutoMigrationEngine.calculateLevenshteinDistance("kitten", "sitting"))
        assertEquals(2, AutoMigrationEngine.calculateLevenshteinDistance("Bleach", "Black"))
    }

    @Test
    fun testAutoMigrationEngine_NormalizeTitle() {
        val raw1 = "[Sub Indo] Solo Leveling (Batch) Season 1"
        val normalized1 = AutoMigrationEngine.normalizeTitle(raw1)
        assertEquals("solo leveling 1", normalized1)

        val raw2 = "One Piece - Subtitle Indonesia [END] BD"
        val normalized2 = AutoMigrationEngine.normalizeTitle(raw2)
        assertEquals("one piece", normalized2)
    }

    @Test
    fun testAutoMigrationEngine_FuzzyScoreMatching() {
        // Judul sama persis
        val scoreExact = AutoMigrationEngine.calculateFuzzyScore("Solo Leveling", "Solo Leveling")
        assertEquals(1.0f, scoreExact, 0.01f)

        // Judul dengan variasi tag/sub indo
        val scoreVariant = AutoMigrationEngine.calculateFuzzyScore(
            "Solo Leveling [Sub Indo]",
            "Solo Leveling (Batch)"
        )
        assertTrue("Skor variasi harus tinggi (>= 0.8)", scoreVariant >= 0.8f)

        // Judul sangat berbeda
        val scoreDifferent = AutoMigrationEngine.calculateFuzzyScore(
            "Solo Leveling",
            "Detective Conan"
        )
        assertTrue("Skor judul berbeda harus rendah (< 0.3)", scoreDifferent < 0.3f)
    }

    @Test
    fun testAutoMigrationEngine_ContentTypeDetection() {
        assertEquals(ContentType.ANIME, AutoMigrationEngine.getContentType("otakudesu"))
        assertEquals(ContentType.ANIME, AutoMigrationEngine.getContentType("kuramanime"))
        assertEquals(ContentType.ANIME, AutoMigrationEngine.getContentType("anichin"))
        assertEquals(ContentType.DRAMA, AutoMigrationEngine.getContentType("viki"))
        assertEquals(ContentType.DRAMA, AutoMigrationEngine.getContentType("licil"))
        assertEquals(ContentType.COMIC, AutoMigrationEngine.getContentType("komikcast"))
        assertEquals(ContentType.COMIC, AutoMigrationEngine.getContentType("asurascans"))
    }

    @Test
    fun testAutoMigrationEngine_FindMatchingChapter() {
        val chapterList = listOf(
            com.example.data.models.ChapterEntry(
                sourceId = "kuramanime",
                mangaId = "solo-leveling",
                chapterId = "ep-1",
                name = "Episode 01",
                url = "https://example.com/ep-1",
                number = 1f,
                dateUpload = 1700000000L
            ),
            com.example.data.models.ChapterEntry(
                sourceId = "kuramanime",
                mangaId = "solo-leveling",
                chapterId = "ep-2",
                name = "Episode 02",
                url = "https://example.com/ep-2",
                number = 2f,
                dateUpload = 1700000100L
            ),
            com.example.data.models.ChapterEntry(
                sourceId = "kuramanime",
                mangaId = "solo-leveling",
                chapterId = "ep-3",
                name = "Episode 03 - The Dungeon",
                url = "https://example.com/ep-3",
                number = 3f,
                dateUpload = 1700000200L
            )
        )

        // Matching by number
        val matchByNumber = AutoMigrationEngine.findMatchingChapter(chapterList, 2f, "Episode 2")
        assertNotNull(matchByNumber)
        assertEquals("ep-2", matchByNumber?.chapterId)

        // Matching by name text
        val matchByName = AutoMigrationEngine.findMatchingChapter(chapterList, null, "Episode 03 - The Dungeon")
        assertNotNull(matchByName)
        assertEquals("ep-3", matchByName?.chapterId)

        // Matching by digit extraction
        val matchByDigit = AutoMigrationEngine.findMatchingChapter(chapterList, null, "Ep. 1 Sub Indo")
        assertNotNull(matchByDigit)
        assertEquals("ep-1", matchByDigit?.chapterId)
    }
}
