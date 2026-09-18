package com.example

import com.example.data.sync.CrossDeviceResumeManager
import com.example.data.sync.CrossDeviceResumeState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CrossDeviceResumeStateTest {

    @Test
    fun testVideoResumeStateFormatting() {
        // Test 12 minutes 5 seconds (12:05)
        val state = CrossDeviceResumeState(
            deviceId = "phone-123",
            contentType = "ANIME",
            itemId = "anime-naruto",
            contentTitle = "Naruto Shippuden",
            episodeOrChapterId = "ep-50",
            episodeOrChapterTitle = "Episode 50",
            sourceId = "kuramanime",
            positionMs = (12 * 60 + 5) * 1000L,
            durationMs = 24 * 60 * 1000L,
            pageIndex = 0,
            totalPages = 0,
            updatedAt = 1000000L
        )

        assertEquals("12:05", state.formattedTimestamp())
        assertEquals("Naruto Shippuden", state.contentTitle)
        assertEquals(725000L, state.positionMs)
    }

    @Test
    fun testLongDurationVideoFormatting() {
        // Test 1 hour 23 minutes 45 seconds (01:23:45)
        val state = CrossDeviceResumeState(
            deviceId = "phone-123",
            contentType = "DRAMA",
            itemId = "drama-vincenzo",
            contentTitle = "Vincenzo",
            episodeOrChapterId = "ep-1",
            episodeOrChapterTitle = "Episode 1",
            sourceId = "viki",
            positionMs = (1 * 3600 + 23 * 60 + 45) * 1000L,
            durationMs = 2 * 3600 * 1000L,
            pageIndex = 0,
            totalPages = 0,
            updatedAt = 1000000L
        )

        assertEquals("01:23:45", state.formattedTimestamp())
    }

    @Test
    fun testComicResumeStateFormatting() {
        val state = CrossDeviceResumeState(
            deviceId = "phone-123",
            contentType = "COMIC",
            itemId = "manga-one-piece",
            contentTitle = "One Piece",
            episodeOrChapterId = "ch-1050",
            episodeOrChapterTitle = "Chapter 1050",
            sourceId = "komiku",
            positionMs = 0L,
            durationMs = 0L,
            pageIndex = 14, // 0-indexed page 14 is Page 15
            totalPages = 20,
            updatedAt = 1000000L
        )

        assertEquals("Halaman 15 / 20", state.formattedTimestamp())
    }

    @Test
    fun testJsonSerializationAndDeserialization() {
        val original = CrossDeviceResumeState(
            deviceId = "pixel-tablet-abc",
            contentType = "ANIME",
            itemId = "jujutsu-kaisen",
            contentTitle = "Jujutsu Kaisen Season 2",
            episodeOrChapterId = "ep-12",
            episodeOrChapterTitle = "Episode 12",
            sourceId = "otakudesu",
            positionMs = 845000L,
            durationMs = 1420000L,
            pageIndex = 0,
            totalPages = 0,
            updatedAt = 1718000000000L
        )

        val jsonString = original.toJson()
        assertNotNull(jsonString)
        assertTrue(jsonString.contains("jujutsu-kaisen"))
        assertTrue(jsonString.contains("845000"))

        val deserialized = CrossDeviceResumeState.fromJson(jsonString)
        assertNotNull(deserialized)
        assertEquals(original.deviceId, deserialized?.deviceId)
        assertEquals(original.contentType, deserialized?.contentType)
        assertEquals(original.itemId, deserialized?.itemId)
        assertEquals(original.contentTitle, deserialized?.contentTitle)
        assertEquals(original.episodeOrChapterId, deserialized?.episodeOrChapterId)
        assertEquals(original.episodeOrChapterTitle, deserialized?.episodeOrChapterTitle)
        assertEquals(original.sourceId, deserialized?.sourceId)
        assertEquals(original.positionMs, deserialized?.positionMs)
        assertEquals(original.durationMs, deserialized?.durationMs)
        assertEquals(original.updatedAt, deserialized?.updatedAt)
    }

    @Test
    fun testIgnoreSelfBroadcast() {
        // CrossDeviceResumeManager should not trigger if incoming deviceId matches localDeviceId
        val selfState = CrossDeviceResumeState(
            deviceId = CrossDeviceResumeManager.localDeviceId,
            contentType = "ANIME",
            itemId = "bleach",
            contentTitle = "Bleach TYBW",
            episodeOrChapterId = "ep-1",
            episodeOrChapterTitle = "Episode 1",
            sourceId = "kuramanime",
            positionMs = 120000L,
            durationMs = 1400000L,
            pageIndex = 0,
            totalPages = 0,
            updatedAt = System.currentTimeMillis()
        )

        // Reset incoming state
        CrossDeviceResumeManager.resetForTesting()
        CrossDeviceResumeManager.handleIncomingState(selfState)

        // Should be null because it was emitted by self
        assertNull(CrossDeviceResumeManager.incomingResumeState.value)

        // Now from remote device
        val remoteState = selfState.copy(deviceId = "remote-tv-living-room")
        CrossDeviceResumeManager.handleIncomingState(remoteState)

        assertEquals(remoteState, CrossDeviceResumeManager.incomingResumeState.value)
    }
}
