package com.example

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import com.example.data.sources.Subtitle
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.drama.DramaSubtitleExtractorEngine
import com.example.media.ExoPlayerMediaItemFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Proposal2SubtitleExtractionAndSyncTest {

    @Test
    fun testExtractSubtitlesFromHtmlTracks() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <video id="player" controls>
                    <source src="https://stream.example.com/video.m3u8" type="application/x-mpegURL">
                    <track kind="subtitles" src="/subs/indonesia.vtt" srclang="id" label="Bahasa Indonesia" default>
                    <track kind="subtitles" src="/subs/english.vtt" srclang="en" label="English">
                    <track kind="subtitles" src="https://external.com/subs/korean.srt" srclang="ko" label="Korean">
                    <track kind="chapters" src="/subs/chapters.vtt" srclang="en"> <!-- Should be ignored -->
                </video>
            </body>
            </html>
        """.trimIndent()

        val subtitles = DramaSubtitleExtractorEngine.extractSubtitlesFromHtml(html, "https://drama.example.com/watch/ep1")
        assertEquals(3, subtitles.size)

        val indSub = subtitles.find { it.language == "Indonesian" }
        assertNotNull(indSub)
        assertEquals("https://drama.example.com/subs/indonesia.vtt", indSub?.url)
        assertEquals("vtt", indSub?.format)

        val engSub = subtitles.find { it.language == "English" }
        assertNotNull(engSub)
        assertEquals("https://drama.example.com/subs/english.vtt", engSub?.url)

        val korSub = subtitles.find { it.language == "Korean" }
        assertNotNull(korSub)
        assertEquals("https://external.com/subs/korean.srt", korSub?.url)
        assertEquals("srt", korSub?.format)
    }

    @Test
    fun testExtractSubtitlesFromJsonEmbeds() {
        val scriptHtml = """
            <html>
            <head>
                <script>
                    var player = jwplayer("player").setup({
                        file: "https://stream.server.net/drama-ep1.mp4",
                        tracks: [
                            { file: "https://subs.server.net/ep1-id.vtt", label: "Indonesian", kind: "captions", default: true },
                            { file: "https://subs.server.net/ep1-en.vtt", label: "English", kind: "captions" },
                            { file: "https://subs.server.net/ep1-ar.vtt", label: "Arabic", kind: "captions" }
                        ]
                    });
                </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val subtitles = DramaSubtitleExtractorEngine.extractSubtitlesFromHtml(scriptHtml, "https://stream.server.net")
        assertEquals(3, subtitles.size)

        val id = subtitles.find { it.language == "Indonesian" }
        assertNotNull(id)
        assertEquals("https://subs.server.net/ep1-id.vtt", id?.url)

        val ar = subtitles.find { it.language == "Arabic" }
        assertNotNull(ar)
        assertEquals("https://subs.server.net/ep1-ar.vtt", ar?.url)
    }

    @Test
    fun testExtractVikiSubtitlesAndLanguageNormalization() {
        val videoId = "1198547v"
        val subtitles = DramaSubtitleExtractorEngine.extractVikiSubtitles(videoId)

        assertTrue(subtitles.isNotEmpty())
        val idSub = subtitles.find { it.language == "Indonesian" }
        assertNotNull(idSub)
        assertTrue(idSub!!.url.contains("/videos/$videoId/subtitles/id.vtt"))

        // Test language normalizer variations
        val info1 = DramaSubtitleExtractorEngine.normalizeLanguage("Sub Indo")
        assertEquals("id", info1.iso639_1)
        assertEquals("Indonesian", info1.displayName)
        assertTrue(info1.isDefault)

        val info2 = DramaSubtitleExtractorEngine.normalizeLanguage("eng")
        assertEquals("en", info2.iso639_1)
        assertEquals("English", info2.displayName)

        val info3 = DramaSubtitleExtractorEngine.normalizeLanguage("korea")
        assertEquals("ko", info3.iso639_1)
        assertEquals("Korean", info3.displayName)
    }

    @Test
    fun testConvertSrtToWebVttWithTimecodeOffset() {
        val srtSample = """
            1
            00:00:01,000 --> 00:00:03,500
            Halo selamat datang di drama ini!

            2
            00:00:04,000 --> 00:00:07,200
            Episode 1 dimulai sekarang.
        """.trimIndent()

        // Test conversion without offset
        val vttWithoutOffset = DramaSubtitleExtractorEngine.convertSrtToWebVtt(srtSample, 0L)
        assertTrue(vttWithoutOffset.startsWith("WEBVTT"))
        assertTrue(vttWithoutOffset.contains("00:00:01.000 --> 00:00:03.500"))
        assertTrue(vttWithoutOffset.contains("Halo selamat datang di drama ini!"))

        // Test conversion with +1500ms offset (Timecode_normalized = Timecode_raw + 1.5s)
        val vttWithOffset = DramaSubtitleExtractorEngine.convertSrtToWebVtt(srtSample, 1500L)
        assertTrue(vttWithOffset.contains("00:00:02.500 --> 00:00:05.000"))
        assertTrue(vttWithOffset.contains("00:00:05.500 --> 00:00:08.700"))
    }

    @Test
    fun testExoPlayerMediaItemFactorySubtitleConfigurations() {
        val subtitles = listOf(
            Subtitle(language = "Indonesian", url = "https://subs.org/id.vtt", format = "vtt"),
            Subtitle(language = "English", url = "https://subs.org/en.srt", format = "srt"),
            Subtitle(language = "Japanese", url = "https://subs.org/ja.ass", format = "ass")
        )

        val streamInfo = VideoStreamInfo(
            episodeId = "ep-1",
            embedUrl = "https://stream.org/master.m3u8",
            subtitles = subtitles
        )

        val mediaItem = ExoPlayerMediaItemFactory.createMediaItemFromStreamInfo(
            streamInfo = streamInfo,
            preferredLanguage = "id"
        )

        assertEquals("https://stream.org/master.m3u8", mediaItem.localConfiguration?.uri.toString())
        val configs = mediaItem.localConfiguration?.subtitleConfigurations
        assertNotNull(configs)
        assertEquals(3, configs?.size)

        val idConfig = configs!![0]
        assertEquals("id", idConfig.language)
        assertEquals("Indonesian", idConfig.label)
        assertEquals(MimeTypes.TEXT_VTT, idConfig.mimeType)
        assertEquals(C.SELECTION_FLAG_DEFAULT, idConfig.selectionFlags)

        val enConfig = configs[1]
        assertEquals("en", enConfig.language)
        assertEquals("English", enConfig.label)
        assertEquals(MimeTypes.APPLICATION_SUBRIP, enConfig.mimeType)
        assertEquals(0, enConfig.selectionFlags)

        val jaConfig = configs[2]
        assertEquals("ja", jaConfig.language)
        assertEquals("Japanese", jaConfig.label)
        assertEquals(MimeTypes.TEXT_SSA, jaConfig.mimeType)
    }
}
