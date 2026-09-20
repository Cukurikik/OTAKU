package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.ContentType
import com.example.data.sources.base.BaseDramaSource
import com.example.data.sources.lokalfilm.LokalFilmSource
import com.example.data.sources.lokalfilm.LokalFilmUvpExtractor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LokalFilmUvpExtractorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val extractor = LokalFilmUvpExtractor()

    @Test
    fun testExtractDirectBase64StreamUrl() {
        val streamUrl = "https://stream.lokalfilm.id/hls/master.m3u8"
        val base64Payload = Base64.getEncoder().encodeToString(streamUrl.toByteArray(Charsets.UTF_8))

        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="player-wrapper">
                    <ul>
                        <li data-video-source="$base64Payload" data-subtitles="https://lokalfilm.id/subtitles/id.vtt">
                            <span class="fwduvp-thumbnail-title">Episode 1</span>
                        </li>
                    </ul>
                </div>
            </body>
            </html>
        """.trimIndent()

        val streamInfo = extractor.extractVideoStreamInfo(html, "https://lokalfilm.id/nonton/film-1")
        assertNotNull("VideoStreamInfo should not be null", streamInfo)
        assertEquals(1, streamInfo!!.qualities.size)
        assertEquals(streamUrl, streamInfo.qualities.first().url)
        assertTrue(streamInfo.qualities.first().isM3U8)
        assertEquals(1, streamInfo.subtitles.size)
        assertEquals("https://lokalfilm.id/subtitles/id.vtt", streamInfo.subtitles.first().url)
    }

    @Test
    fun testExtractUvpMultiQualityJsonArray() {
        val jsonArray = """
            [
                {"source": "https://stream.lokalfilm.id/1080p.m3u8", "label": "1080p"},
                {"source": "https://stream.lokalfilm.id/720p.m3u8", "label": "720p"},
                {"source": "https://stream.lokalfilm.id/480p.mp4", "label": "480p"}
            ]
        """.trimIndent()
        val base64Payload = Base64.getEncoder().encodeToString(jsonArray.toByteArray(Charsets.UTF_8))

        val html = """
            <div id="fwduvpDiv">
                <li data-video-source="$base64Payload">
                    <span class="fwduvp-thumbnail-title">The Big Movie</span>
                </li>
            </div>
        """.trimIndent()

        val streamInfo = extractor.extractVideoStreamInfo(html, "https://lokalfilm.id/movie/big-movie")
        assertNotNull(streamInfo)
        assertEquals(3, streamInfo!!.qualities.size)

        assertEquals("1080p", streamInfo.qualities[0].qualityLabel)
        assertEquals("https://stream.lokalfilm.id/1080p.m3u8", streamInfo.qualities[0].url)
        assertTrue(streamInfo.qualities[0].isM3U8)

        assertEquals("720p", streamInfo.qualities[1].qualityLabel)
        assertEquals("https://stream.lokalfilm.id/720p.m3u8", streamInfo.qualities[1].url)
        assertTrue(streamInfo.qualities[1].isM3U8)

        assertEquals("480p", streamInfo.qualities[2].qualityLabel)
        assertEquals("https://stream.lokalfilm.id/480p.mp4", streamInfo.qualities[2].url)
        assertFalse(streamInfo.qualities[2].isM3U8)
    }

    @Test
    fun testExtractUvpJsonObjectWithSubtitles() {
        val jsonPayload = """
            {
                "sources": [
                    {"file": "https://cdn.lokalfilm.id/video/master.m3u8", "label": "Full HD"}
                ],
                "subtitles": [
                    {"file": "https://cdn.lokalfilm.id/subs/indonesian.vtt", "label": "Bahasa Indonesia"},
                    {"file": "https://cdn.lokalfilm.id/subs/english.vtt", "label": "English"}
                ]
            }
        """.trimIndent()
        val base64 = Base64.getEncoder().encodeToString(jsonPayload.toByteArray(Charsets.UTF_8))

        val html = """
            <ul>
                <li data-video-source="$base64"></li>
            </ul>
        """.trimIndent()

        val streamInfo = extractor.extractVideoStreamInfo(html, "https://lokalfilm.id/watch/ep1")
        assertNotNull(streamInfo)
        assertEquals(1, streamInfo!!.qualities.size)
        assertEquals("https://cdn.lokalfilm.id/video/master.m3u8", streamInfo.qualities.first().url)
        assertEquals(2, streamInfo.subtitles.size)
        assertEquals("Bahasa Indonesia", streamInfo.subtitles[0].language)
        assertEquals("English", streamInfo.subtitles[1].language)
    }

    @Test
    fun testExtractInlineScriptVideoSources() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>LokalFilm</title></head>
            <body>
                <div id="player"></div>
                <script>
                    var playerInstance = new FWDUVP({
                        videoSources: [
                            { source: "https://media.lokalfilm.id/series/ep1-hd.m3u8", label: "1080p" },
                            { source: "https://media.lokalfilm.id/series/ep1-sd.mp4", label: "480p" }
                        ]
                    });
                </script>
            </body>
            </html>
        """.trimIndent()

        val streamInfo = extractor.extractVideoStreamInfo(html, "https://lokalfilm.id/film/series-ep1")
        assertNotNull(streamInfo)
        assertEquals(2, streamInfo!!.qualities.size)
        assertEquals("1080p", streamInfo.qualities[0].qualityLabel)
        assertEquals("https://media.lokalfilm.id/series/ep1-hd.m3u8", streamInfo.qualities[0].url)
        assertEquals("480p", streamInfo.qualities[1].qualityLabel)
        assertEquals("https://media.lokalfilm.id/series/ep1-sd.mp4", streamInfo.qualities[1].url)
    }

    @Test
    fun testLokalFilmSourceWithChaptersAndDetail() = runBlocking {
        val fakeDetailHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="Petualangan Nusantara" />
                <meta property="og:image" content="https://lokalfilm.id/images/poster.jpg" />
                <meta name="description" content="Film petualangan seru melintasi pulau di Indonesia." />
            </head>
            <body>
                <div class="movie-content">
                    <h1 class="entry-title">Petualangan Nusantara</h1>
                    <div class="genres"><a href="/genre/action">Action</a><a href="/genre/adventure">Adventure</a></div>
                </div>
                <div class="fwduvp-playlist">
                    <ul>
                        <li data-video-source="aHR0cHM6Ly9zdHJlYW0ubG9rYWxmaWxtLmlkL2VwMS5tM3U4">
                            <span class="fwduvp-thumbnail-title">Episode 1: Awal Perjalanan</span>
                        </li>
                        <li data-video-source="aHR0cHM6Ly9zdHJlYW0ubG9rYWxmaWxtLmlkL2VwMi5tM3U4">
                            <span class="fwduvp-thumbnail-title">Episode 2: Menuju Puncak</span>
                        </li>
                    </ul>
                </div>
            </body>
            </html>
        """.trimIndent()

        val lokalFilmSource = object : LokalFilmSource(context) {
            override suspend fun fetchDocumentInternal(url: String): Document {
                return Jsoup.parse(fakeDetailHtml, baseUrl)
            }
        }

        // Test Detail
        val detail = lokalFilmSource.getDetail("https://lokalfilm.id/film/petualangan-nusantara")
        assertNotNull(detail)
        assertEquals("Petualangan Nusantara", detail!!.title)
        assertEquals("https://lokalfilm.id/images/poster.jpg", detail.imageUrl)
        assertEquals(ContentType.DRAMA, detail.type)
        assertEquals("lokalfilm", detail.sourceId)
        assertTrue(detail.genres.contains("Action"))
        assertTrue(detail.genres.contains("Adventure"))

        // Test Chapters / Episodes
        val chapters = lokalFilmSource.getChapters("https://lokalfilm.id/film/petualangan-nusantara").first()
        assertEquals(2, chapters.size)
        assertEquals("Episode 1: Awal Perjalanan", chapters[0].title)
        assertEquals("https://stream.lokalfilm.id/ep1.m3u8", chapters[0].url)
        assertEquals("Episode 2: Menuju Puncak", chapters[1].title)
        assertEquals("https://stream.lokalfilm.id/ep2.m3u8", chapters[1].url)

        // Test Video Stream Fetching from UVP source
        val streamResult = lokalFilmSource.fetchVideoStream("https://stream.lokalfilm.id/ep1.m3u8")
        assertTrue(streamResult.isSuccess)
        val streamInfo = streamResult.getOrThrow()
        assertEquals("https://stream.lokalfilm.id/ep1.m3u8", streamInfo.bestQuality?.url)
    }
}
