package com.example.network.interceptor

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import com.example.data.sources.Subtitle
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.anime.extractors.ApiResponseParser
import com.example.media.ExoPlayerManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubtitleInterceptionTest {

    @Test
    fun testSubtitleRegexAndUrlDetection() {
        assertTrue(VideoUrlInterceptor.isSubtitleUrl("https://stream.jucelebrity.com/subtitles/episode_01.vtt"))
        assertTrue(VideoUrlInterceptor.isSubtitleUrl("https://licil.org/tracks/indo.srt"))
        assertTrue(VideoUrlInterceptor.isSubtitleUrl("https://viki.com/captions/sub.vtt?token=xyz123"))
        assertTrue(VideoUrlInterceptor.isSubtitleUrl("https://drakor.is/subs/indonesian.ass"))
        assertTrue(VideoUrlInterceptor.isSubtitleUrl("https://nodrakor.icu/subtitles/id.ssa"))

        // False positives should be rejected
        assertFalse(VideoUrlInterceptor.isSubtitleUrl("https://cdn.example.com/player.js"))
        assertFalse(VideoUrlInterceptor.isSubtitleUrl("https://cdn.example.com/style.css"))
        assertFalse(VideoUrlInterceptor.isSubtitleUrl("https://cdn.example.com/poster.jpg"))
        assertFalse(VideoUrlInterceptor.isSubtitleUrl("https://cdn.example.com/banner.png"))
    }

    @Test
    fun testCheckAndCaptureSubtitle() {
        val vttSub = VideoUrlInterceptor.checkAndCaptureSubtitle("https://stream.jucelebrity.com/subtitles/indonesia_track.vtt")
        assertNotNull(vttSub)
        assertEquals("Indonesian", vttSub!!.language)
        assertEquals("vtt", vttSub.format)
        assertEquals("https://stream.jucelebrity.com/subtitles/indonesia_track.vtt", vttSub.url)

        val srtSub = VideoUrlInterceptor.checkAndCaptureSubtitle("https://licil.org/tracks/sub-en.srt")
        assertNotNull(srtSub)
        assertEquals("English", srtSub!!.language)
        assertEquals("srt", srtSub.format)

        val assSub = VideoUrlInterceptor.checkAndCaptureSubtitle("https://drakor.net/subs/kor.ass")
        assertNotNull(assSub)
        assertEquals("Korean", assSub!!.language)
        assertEquals("ass", assSub.format)

        val nullSub = VideoUrlInterceptor.checkAndCaptureSubtitle("https://example.com/video.mp4")
        assertNull(nullSub)
    }

    @Test
    fun testApiResponseParserExtractSubtitleTracksFromJsonAndJwPlayer() {
        val jwPlayerResponse = """
            jwplayer("player").setup({
                file: "https://video.licil.org/master.m3u8",
                tracks: [
                    { file: "https://sub.licil.org/indonesian.vtt", label: "Indonesian", kind: "captions", "default": true },
                    { file: "https://sub.licil.org/english.srt", label: "English", kind: "subtitles" }
                ]
            });
        """.trimIndent()

        val parsedTracks = ApiResponseParser.extractSubtitleTracks("https://licil.org/watch/1", jwPlayerResponse)
        assertEquals(2, parsedTracks.size)

        assertEquals("https://sub.licil.org/indonesian.vtt", parsedTracks[0].url)
        assertEquals("Indonesian", parsedTracks[0].language)
        assertEquals("vtt", parsedTracks[0].format)

        assertEquals("https://sub.licil.org/english.srt", parsedTracks[1].url)
        assertEquals("English", parsedTracks[1].language)
        assertEquals("srt", parsedTracks[1].format)
    }

    @Test
    fun testApiResponseParserExtractSubtitleUrlsNestedJson() {
        val jsonPayload = """
            {
                "status": "success",
                "data": {
                    "stream_url": "https://stream.drakor.is/ep1.m3u8",
                    "subtitles": [
                        { "url": "https://sub.drakor.is/id_sub.vtt", "label": "Bahasa Indonesia", "kind": "captions" },
                        { "src": "https://sub.drakor.is/en_sub.srt", "name": "English Sub" }
                    ]
                }
            }
        """.trimIndent()

        val parsedSubs = ApiResponseParser.extractSubtitleUrls("https://drakor.is/api/stream", jsonPayload)
        assertEquals(2, parsedSubs.size)
        assertEquals("https://sub.drakor.is/id_sub.vtt", parsedSubs[0].url)
        assertEquals("vtt", parsedSubs[0].format)
        assertEquals("https://sub.drakor.is/en_sub.srt", parsedSubs[1].url)
        assertEquals("srt", parsedSubs[1].format)
    }

    @Test
    fun testMediaStreamResultConversionToMediaItemAndVideoStreamInfo() {
        val mediaResult = MediaStreamResult(
            videoUrl = "https://stream.drama.com/video.mp4",
            qualities = listOf(
                VideoQuality("1080p", "https://stream.drama.com/video_1080p.mp4"),
                VideoQuality("720p", "https://stream.drama.com/video_720p.mp4")
            ),
            subtitles = listOf(
                Subtitle(language = "Indonesian", url = "https://sub.drama.com/id.vtt", format = "vtt"),
                Subtitle(language = "English", url = "https://sub.drama.com/en.srt", format = "srt")
            ),
            headers = mapOf("Referer" to "https://drama.com")
        )

        // 1. To VideoStreamInfo
        val streamInfo = mediaResult.toVideoStreamInfo(episodeId = "ep-1")
        assertEquals("ep-1", streamInfo.episodeId)
        assertEquals(2, streamInfo.qualities.size)
        assertEquals(2, streamInfo.subtitles.size)
        assertEquals("https://sub.drama.com/id.vtt", streamInfo.subtitles[0].url)

        // 2. To MediaItem with SubtitleConfiguration
        val mediaItem = mediaResult.toMediaItem()
        assertNotNull(mediaItem.localConfiguration)
        val configs = mediaItem.localConfiguration!!.subtitleConfigurations
        assertEquals(2, configs.size)

        assertEquals("https://sub.drama.com/id.vtt", configs[0].uri.toString())
        assertEquals(MimeTypes.TEXT_VTT, configs[0].mimeType)
        assertEquals("Indonesian", configs[0].language)
        assertEquals(C.SELECTION_FLAG_DEFAULT, configs[0].selectionFlags)

        assertEquals("https://sub.drama.com/en.srt", configs[1].uri.toString())
        assertEquals(MimeTypes.APPLICATION_SUBRIP, configs[1].mimeType)
        assertEquals("English", configs[1].language)
    }
}
