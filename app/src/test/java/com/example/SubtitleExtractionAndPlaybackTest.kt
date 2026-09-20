package com.example

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import com.example.data.sources.Subtitle
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.anime.extractors.ApiResponseParser
import com.example.media.ExoPlayerManager
import com.example.network.interceptor.WebAppInterface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SubtitleExtractionAndPlaybackTest {

    @Test
    fun testApiResponseParserExtractJwPlayerTracks() {
        val jwPlayerHtml = """
            <script>
                jwplayer("player").setup({
                    file: "https://cdn.drama.org/stream/ep1/master.m3u8",
                    tracks: [
                        {
                            file: "https://cdn.drama.org/subtitles/indonesian.vtt",
                            label: "Indonesian",
                            kind: "captions",
                            "default": true
                        },
                        {
                            file: "https://cdn.drama.org/subtitles/english.srt",
                            label: "English",
                            kind: "subtitles"
                        }
                    ]
                });
            </script>
        """.trimIndent()

        val extracted = ApiResponseParser.extractSubtitleTracks("https://drama.org/embed/123", jwPlayerHtml)
        assertTrue("Subtitles list should not be empty", extracted.isNotEmpty())
        assertEquals(2, extracted.size)

        val idSub = extracted.firstOrNull { it.language.contains("Indonesian", ignoreCase = true) }
        assertNotNull(idSub)
        assertEquals("https://cdn.drama.org/subtitles/indonesian.vtt", idSub?.url)
        assertEquals("vtt", idSub?.format)

        val enSub = extracted.firstOrNull { it.language.contains("English", ignoreCase = true) }
        assertNotNull(enSub)
        assertEquals("https://cdn.drama.org/subtitles/english.srt", enSub?.url)
        assertEquals("srt", enSub?.format)
    }

    @Test
    fun testApiResponseParserExtractJsonSubtitles() {
        val jsonPayload = """
            {
                "status": "success",
                "sources": [
                    { "file": "https://server.jucelebrity.com/hls/raw_video.m3u8", "type": "hls" }
                ],
                "subtitles": [
                    { "url": "https://server.jucelebrity.com/subs/ep1_indo.vtt", "label": "Bahasa Indonesia", "kind": "captions" },
                    { "url": "https://server.jucelebrity.com/subs/ep1_en.vtt", "label": "English", "kind": "subtitles" }
                ]
            }
        """.trimIndent()

        val extracted = ApiResponseParser.extractSubtitleTracks("https://server.jucelebrity.com/api/source", jsonPayload)
        assertEquals(2, extracted.size)
        assertEquals("https://server.jucelebrity.com/subs/ep1_indo.vtt", extracted[0].url)
        assertEquals("Bahasa Indonesia", extracted[0].language)
        assertEquals("vtt", extracted[0].format)
    }

    @Test
    fun testApiResponseParserDirectRegexMatching() {
        val rawBody = "Here is video stream: https://cdn.licil.org/video.mp4 and subtitle: https://cdn.licil.org/captions/sub_id.vtt?token=xyz"
        val extracted = ApiResponseParser.extractSubtitleTracks("https://licil.org/watch/1", rawBody)
        assertEquals(1, extracted.size)
        assertEquals("https://cdn.licil.org/captions/sub_id.vtt?token=xyz", extracted[0].url)
        assertEquals("Indonesian", extracted[0].language)
        assertEquals("vtt", extracted[0].format)
    }

    @Test
    fun testInferSubtitleMimeType() {
        assertEquals(MimeTypes.TEXT_VTT, ExoPlayerManager.inferSubtitleMimeType("https://cdn.com/sub.vtt"))
        assertEquals(MimeTypes.TEXT_VTT, ExoPlayerManager.inferSubtitleMimeType("https://cdn.com/sub.vtt?token=123"))
        assertEquals(MimeTypes.TEXT_VTT, ExoPlayerManager.inferSubtitleMimeType("vtt"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, ExoPlayerManager.inferSubtitleMimeType("https://cdn.com/sub.srt"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, ExoPlayerManager.inferSubtitleMimeType("https://cdn.com/sub.srt?key=abc"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, ExoPlayerManager.inferSubtitleMimeType("srt"))
        assertEquals(MimeTypes.TEXT_SSA, ExoPlayerManager.inferSubtitleMimeType("https://cdn.com/sub.ass"))
    }

    @Test
    fun testCreateMediaItemWithSubtitles() {
        val videoUrl = "https://cdn.example.org/drama/ep1.mp4"
        val subs = listOf(
            Subtitle(language = "Indonesian", url = "https://cdn.example.org/subs/ep1_id.vtt", format = "vtt"),
            Subtitle(language = "English", url = "https://cdn.example.org/subs/ep1_en.srt", format = "srt")
        )

        val mediaItem = ExoPlayerManager.createMediaItemWithSubtitles(
            videoUrl = videoUrl,
            subtitles = subs
        )

        assertNotNull(mediaItem)
        assertEquals(Uri.parse(videoUrl), mediaItem.localConfiguration?.uri)
        assertEquals(2, mediaItem.localConfiguration?.subtitleConfigurations?.size)

        val firstConfig = mediaItem.localConfiguration?.subtitleConfigurations?.get(0)
        assertEquals(Uri.parse("https://cdn.example.org/subs/ep1_id.vtt"), firstConfig?.uri)
        assertEquals(MimeTypes.TEXT_VTT, firstConfig?.mimeType)
        assertEquals("Indonesian", firstConfig?.language)
        assertEquals(C.SELECTION_FLAG_DEFAULT, firstConfig?.selectionFlags)

        val secondConfig = mediaItem.localConfiguration?.subtitleConfigurations?.get(1)
        assertEquals(Uri.parse("https://cdn.example.org/subs/ep1_en.srt"), secondConfig?.uri)
        assertEquals(MimeTypes.APPLICATION_SUBRIP, secondConfig?.mimeType)
        assertEquals("English", secondConfig?.language)
        assertEquals(0, secondConfig?.selectionFlags)
    }

    @Test
    fun testWebAppInterfaceSubtitleCallback() {
        var discoveredStream: String? = null
        var discoveredSubtitle: Subtitle? = null

        val bridge = WebAppInterface(
            onStreamFound = { url, _ -> discoveredStream = url },
            onSubtitleFound = { sub -> discoveredSubtitle = sub }
        )

        bridge.processSubtitle("https://server.viki.com/captions/viki_sub.vtt", "Indonesian", "vtt")
        assertNotNull(discoveredSubtitle)
        assertEquals("https://server.viki.com/captions/viki_sub.vtt", discoveredSubtitle?.url)
        assertEquals("Indonesian", discoveredSubtitle?.language)
        assertEquals("vtt", discoveredSubtitle?.format)

        val apiResponse = """
            {
                "url": "https://server.viki.com/hls/ep1.m3u8",
                "tracks": [
                    { "file": "https://server.viki.com/subs/id.srt", "label": "Indonesian", "kind": "captions" }
                ]
            }
        """.trimIndent()

        bridge.processApiResponse("https://server.viki.com/api", apiResponse)
        assertEquals("https://server.viki.com/hls/ep1.m3u8", discoveredStream)
        assertEquals("https://server.viki.com/subs/id.srt", discoveredSubtitle?.url)
        assertEquals("srt", discoveredSubtitle?.format)
    }

    @Test
    fun testVideoStreamInfoSubtitlesModel() {
        val streamInfo = VideoStreamInfo(
            episodeId = "ep_1",
            embedUrl = "https://server.drama.org/embed/ep1",
            qualities = listOf(VideoQuality("1080p", "https://server.drama.org/ep1.m3u8", true)),
            subtitles = listOf(
                Subtitle("Indonesian", "https://server.drama.org/sub_id.vtt", "vtt")
            )
        )

        assertEquals("ep_1", streamInfo.episodeId)
        assertEquals(1, streamInfo.subtitles.size)
        assertEquals("https://server.drama.org/sub_id.vtt", streamInfo.subtitles[0].url)
    }
}
