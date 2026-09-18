package com.example

import com.example.data.models.DownloadEntity
import com.example.data.models.DownloadPayload
import com.example.data.models.SubtitlePayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDownloadPreResolutionTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `test download payload serialization and deserialization`() {
        val payload = DownloadPayload(
            sourceId = "kuramanime",
            mangaId = "naruto-shippuden",
            chapterId = "episode-1",
            directUrl = "https://stream.server.example/video/master.m3u8",
            headers = mapOf(
                "Referer" to "https://kuramanime.pro/watch/naruto-shippuden",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
            ),
            cookies = "cf_clearance=abc123xyz; session=test",
            mimeType = "application/x-mpegURL",
            qualityLabel = "1080p",
            subtitles = listOf(
                SubtitlePayload("Indonesian", "https://stream.server.example/sub/id.vtt", "vtt"),
                SubtitlePayload("English", "https://stream.server.example/sub/en.vtt", "vtt")
            )
        )

        val serialized = json.encodeToString(payload)
        val deserialized = json.decodeFromString<DownloadPayload>(serialized)

        assertEquals(payload.sourceId, deserialized.sourceId)
        assertEquals(payload.mangaId, deserialized.mangaId)
        assertEquals(payload.chapterId, deserialized.chapterId)
        assertEquals(payload.directUrl, deserialized.directUrl)
        assertEquals("1080p", deserialized.qualityLabel)
        assertEquals(2, deserialized.subtitles.size)
        assertEquals("Indonesian", deserialized.subtitles[0].language)
    }

    @Test
    fun `test download entity preserves pre-resolved metadata`() {
        val subList = listOf(
            SubtitlePayload("Indonesian", "https://example.com/id.vtt", "vtt")
        )
        val subsJson = json.encodeToString(subList)
        val headersJson = json.encodeToString(mapOf("Referer" to "https://example.com"))

        val entity = DownloadEntity(
            profileId = 1L,
            sourceId = "otakudesu",
            mangaId = "one-piece",
            chapterId = "eps-1000",
            state = "QUEUE",
            priority = 0,
            directUrl = "https://cdn.example.com/video.mp4",
            headersJson = headersJson,
            mimeType = "video/mp4",
            qualityLabel = "720p",
            subtitlesJson = subsJson
        )

        assertNotNull(entity.directUrl)
        assertEquals("video/mp4", entity.mimeType)
        assertEquals("720p", entity.qualityLabel)
        assertTrue(entity.headersJson!!.contains("Referer"))
        assertTrue(entity.subtitlesJson!!.contains("Indonesian"))
    }
}
