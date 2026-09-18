package com.example.data.sources.anime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.sources.anime.base.WordPressAnimeStreamScraper
import com.example.data.sources.anime.base.OploverzThemeScraper
import com.example.data.sources.anime.base.CustomAjaxEncryptedAnimeScraper
import com.example.data.sources.anime.base.SpecialUrlProtectedAnimeScraper
import com.example.data.sources.anime.extractors.CryptoJsDecryptor
import com.example.data.sources.anime.specific.OtakudesuScraper
import com.example.data.sources.anime.specific.KusonimeScraper
import com.example.data.sources.anime.specific.AnichinScraper
import com.example.data.sources.anime.specific.AnimeMoviesScraper
import com.example.data.sources.anime.specific.KuronimeScraper
import com.example.data.sources.anime.specific.YlNimeScraper
import com.example.network.interceptor.VideoUrlInterceptor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnimeSourceUnitTest {

    private lateinit var context: Context
    private lateinit var videoInterceptor: VideoUrlInterceptor

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        videoInterceptor = VideoUrlInterceptor(context)
    }

    @Test
    fun testKuramanimeVideoExtraction_returnsValidStreamUrl() = runBlocking {
        val targetEpisodeUrl = "https://kuramanime.example/anime/123/episode/1"
        
        val result = videoInterceptor.interceptStreamUrl(targetEpisodeUrl)
        
        assertNotNull(result)
        assertTrue("URL harus berakhiran .m3u8 atau .mp4", result!!.endsWith(".m3u8") || result.endsWith(".mp4"))
        assertFalse("URL stream tidak boleh kosong", result.isEmpty())
    }

    @Test
    fun testAnimasuCryptoJsPayloadExtraction() = runBlocking {
        // Test CryptoJS decrypted payload parsing
        val mockJson = """{"sources":[{"file":"https://animasu.cdn/streams/video.m3u8","label":"1080p","type":"hls"}]}"""
        val parsed = CryptoJsDecryptor.parseDecryptedPayload(mockJson, "https://v2.animasu.work/episode/1")
        
        assertNotNull(parsed)
        assertTrue(parsed.qualities.isNotEmpty())
        val firstQuality = parsed.qualities.first()
        assertEquals("https://animasu.cdn/streams/video.m3u8", firstQuality.url)
        assertTrue(firstQuality.isM3U8)
    }

    @Test
    fun testDirectMediaStreamUrlInterception() = runBlocking {
        val directM3u8Url = "https://stream.server.org/live/manifest.m3u8"
        val result = videoInterceptor.interceptStreamUrl(directM3u8Url)
        
        assertNotNull(result)
        assertEquals(directM3u8Url, result)
    }

    @Test
    fun testDirectMp4UrlInterception() = runBlocking {
        val directMp4Url = "https://cdn.animasu.org/storage/video_720p.mp4"
        val result = videoInterceptor.interceptStreamUrl(directMp4Url)
        
        assertNotNull(result)
        assertEquals(directMp4Url, result)
        assertTrue(result!!.endsWith(".mp4"))
    }

    @Test
    fun testOtakudesuAndKusonimeScraperInitialization() {
        val otakudesu = OtakudesuScraper(context)
        assertEquals("otakudesu", otakudesu.sourceId)
        assertEquals("OtakuDesu", otakudesu.sourceName)
        assertEquals("https://otakudesu.blog", otakudesu.baseUrl)

        val kusonime = KusonimeScraper(context)
        assertEquals("kusonime", kusonime.sourceId)
        assertEquals("Kusonime", kusonime.sourceName)
        assertEquals("https://kusonime.com", kusonime.baseUrl)
    }

    @Test
    fun testWordPressAnimeStreamScrapersInitialization() {
        val animeMovies = AnimeMoviesScraper(context)
        assertEquals("animemovies", animeMovies.sourceId)
        assertEquals("https://animemovies.org", animeMovies.baseUrl)

        val anichin = AnichinScraper(context)
        assertEquals("anichin", anichin.sourceId)
        assertEquals("https://anichin.watch", anichin.baseUrl)
    }

    @Test
    fun testEncryptedAndSpecialScrapersInitialization() {
        val kuronime = KuronimeScraper(context)
        assertEquals("kuronime", kuronime.sourceId)
        assertEquals("https://kuronime.sbs", kuronime.baseUrl)

        val ylnime = YlNimeScraper(context)
        assertEquals("ylnime", ylnime.sourceId)
        assertEquals("https://ylnime.com", ylnime.baseUrl)
    }
}
