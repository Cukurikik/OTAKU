package com.example.network

import com.example.data.sources.anime.extractors.ApiResponseParser
import com.example.ui.player.SubtitleCue
import com.example.ui.player.SubtitleParser
import com.example.ui.player.SubtitleSyncController
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubtitleExtractionAndPlaybackTest {

    @Test
    fun testExtractSubtitlesFromJWPlayerConfig() {
        val jwPlayerHtml = """
            <script>
            jwplayer("vplayer").setup({
                file: "https://stream.server.io/master.m3u8",
                tracks: [
                    { file: "https://sub.server.io/indonesian.vtt", label: "Indonesian", kind: "captions", default: true },
                    { file: "https://sub.server.io/english.srt", label: "English", kind: "subtitles" }
                ]
            });
            </script>
        """.trimIndent()

        val extracted = ApiResponseParser.extractSubtitleTracks("https://provider.io/watch/ep1", jwPlayerHtml)
        assertTrue("Should extract at least 2 subtitle tracks", extracted.size >= 2)
        
        val indo = extracted.find { it.url.contains("indonesian.vtt") }
        assertNotNull("Indonesian subtitle track must be found", indo)
        assertEquals("Indonesian", indo?.language)
        assertEquals("vtt", indo?.format)

        val eng = extracted.find { it.url.contains("english.srt") }
        assertNotNull("English subtitle track must be found", eng)
        assertEquals("English", eng?.language)
        assertEquals("srt", eng?.format)
    }

    @Test
    fun testExtractSubtitlesFromHtmlTrackTags() {
        val htmlWithTracks = """
            <video id="player" controls>
                <source src="https://video.host.com/movie.mp4" type="video/mp4">
                <track src="/subtitles/id.vtt" kind="subtitles" srclang="id" label="Bahasa Indonesia">
                <track src="https://cdn.host.com/subs/en.vtt" kind="captions" label="English">
            </video>
        """.trimIndent()

        val extracted = ApiResponseParser.extractSubtitleTracks("https://video.host.com/player", htmlWithTracks)
        assertTrue("Should extract HTML track tags", extracted.isNotEmpty())

        val idTrack = extracted.find { it.url.contains("id.vtt") }
        assertNotNull("Relative track URL should resolve with baseUrl", idTrack)
        assertEquals("https://video.host.com/subtitles/id.vtt", idTrack?.url)

        val enTrack = extracted.find { it.url.contains("en.vtt") }
        assertNotNull("Absolute track URL should be extracted", enTrack)
        assertEquals("https://cdn.host.com/subs/en.vtt", enTrack?.url)
    }

    @Test
    fun testExtractSubtitlesFromRawJsonApiResponse() {
        val jsonPayload = """
            {
                "status": 200,
                "data": {
                    "stream": "https://stream.io/hls/manifest.m3u8",
                    "subtitles": [
                        { "file": "https://cdn.io/subs/indo_hd.vtt", "label": "Indonesia Hardsub" },
                        { "file": "https://cdn.io/subs/eng_soft.srt", "label": "English Softsub" }
                    ]
                }
            }
        """.trimIndent()

        val extracted = ApiResponseParser.extractSubtitleTracks("https://stream.io/api/source", jsonPayload)
        assertEquals(2, extracted.size)
        assertTrue(extracted.any { it.url == "https://cdn.io/subs/indo_hd.vtt" && it.language == "Indonesia Hardsub" })
        assertTrue(extracted.any { it.url == "https://cdn.io/subs/eng_soft.srt" && it.language == "English Softsub" })
    }

    @Test
    fun testSubtitleSyncControllerOffsetBounds() {
        val controller = SubtitleSyncController()

        // Default 0s
        assertEquals(0L, controller.getOffsetMs())
        assertEquals(0.0f, controller.getOffsetSeconds(), 0.001f)

        // Positive clamping (+5.0s max)
        controller.applyOffset(7.5f)
        assertEquals(5000L, controller.getOffsetMs())
        assertEquals(5.0f, controller.getOffsetSeconds(), 0.001f)

        // Negative clamping (-5.0s min)
        controller.applyOffset(-10.0f)
        assertEquals(-5000L, controller.getOffsetMs())
        assertEquals(-5.0f, controller.getOffsetSeconds(), 0.001f)

        // Valid offset within range (+2.5s)
        controller.applyOffset(2.5f)
        assertEquals(2500L, controller.getOffsetMs())
        assertEquals(2.5f, controller.getOffsetSeconds(), 0.001f)
    }

    @Test
    fun testSubtitleDisplayTimeCalculation() {
        val controller = SubtitleSyncController()
        
        // Offset: +1.5s (+1500ms)
        // Formula: T_display = T_current + Delta T_offset
        controller.applyOffset(1.5f)
        val currentPlaybackPos = 10_000L // 10.0s
        val displayTime = controller.calculateDisplayTime(currentPlaybackPos)
        assertEquals(11_500L, displayTime) // 10.0s + 1.5s = 11.5s

        // Negative Offset: -2.0s (-2000ms)
        controller.applyOffset(-2.0f)
        val displayTimeNegative = controller.calculateDisplayTime(currentPlaybackPos)
        assertEquals(8_000L, displayTimeNegative) // 10.0s - 2.0s = 8.0s

        // Negative Offset clamping at 0ms (no negative playback time)
        val earlyPos = 500L
        val displayTimeClamped = controller.calculateDisplayTime(earlyPos)
        assertEquals(0L, displayTimeClamped)
    }

    @Test
    fun testSubtitleParserAndCueMatching() {
        val rawVtt = """
            WEBVTT

            00:00:01.000 --> 00:00:04.000
            Halo dunia, selamat datang!

            00:00:05.500 --> 00:00:08.200
            Ini adalah baris subtitle kedua.
        """.trimIndent()

        val cues = SubtitleParser.parseVtt(rawVtt)
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(4000L, cues[0].endMs)
        assertEquals("Halo dunia, selamat datang!", cues[0].text)

        assertEquals(5500L, cues[1].startMs)
        assertEquals(8200L, cues[1].endMs)
        assertEquals("Ini adalah baris subtitle kedua.", cues[1].text)

        // Test with SubtitleSyncController offset application
        val controller = SubtitleSyncController()
        controller.applyOffset(0.5f) // +500ms

        val sampleTime = 5200L // Without offset: outside cue (cue starts at 5500)
        val effectiveDisplayTime = controller.calculateDisplayTime(sampleTime) // 5200 + 500 = 5700ms (Inside cue 5500..8200)
        
        val activeCues = cues.filter { effectiveDisplayTime in it.startMs..it.endMs }
        assertEquals(1, activeCues.size)
        assertEquals("Ini adalah baris subtitle kedua.", activeCues[0].text)
    }
}
