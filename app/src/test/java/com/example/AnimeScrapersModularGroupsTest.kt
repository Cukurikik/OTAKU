package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.sources.anime.specific.AnimeMoviesScraper
import com.example.data.sources.anime.specific.StuckNimeScraper
import com.example.data.sources.anime.specific.AnimeKuInfoScraper
import com.example.data.sources.anime.specific.KuronimeScraper
import com.example.data.sources.anime.specific.AnimeIndoScraper
import com.example.data.sources.anime.specific.AnimasuMovieScraper
import com.example.data.sources.anime.specific.AnimasuIDScraper
import com.example.data.sources.anime.specific.YlNimeScraper
import com.example.data.sources.anime.specific.NimegamiScraper
import com.example.data.sources.anime.specific.WibukuScraper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = OtakuHubApp::class)
class AnimeScrapersModularGroupsTest {

    private val app: Application by lazy { ApplicationProvider.getApplicationContext<OtakuHubApp>() }

    @Test
    fun testGroup1WordPressAnimeStreamInstantiations() {
        val animemovies = AnimeMoviesScraper(app)
        assertEquals("animemovies", animemovies.sourceId)
        assertEquals("https://animemovies.org", animemovies.baseUrl)

        val stucknime = StuckNimeScraper(app)
        assertEquals("stucknime", stucknime.sourceId)
        assertEquals("https://stucknime.my.id", stucknime.baseUrl)

        val animekuInfo = AnimeKuInfoScraper(app)
        assertEquals("animeku-info", animekuInfo.sourceId)
        assertEquals("https://animeku.info", animekuInfo.baseUrl)
    }

    @Test
    fun testGroup2CustomAjaxEncryptedScrapers() {
        val kuronime = KuronimeScraper(app)
        assertEquals("kuronime", kuronime.sourceId)
        assertEquals("https://kuronime.sbs", kuronime.baseUrl)

        val animeindo = AnimeIndoScraper(app)
        assertEquals("animeindo", animeindo.sourceId)
        assertEquals("https://animeindo.me", animeindo.baseUrl)

        val animasuMovie = AnimasuMovieScraper(app)
        assertEquals("animasu-movie", animasuMovie.sourceId)
        assertEquals("https://v2.animasu.work/anime-movie/", animasuMovie.baseUrl)

        val animasuid = AnimasuIDScraper(app)
        assertEquals("animasuid", animasuid.sourceId)
        assertEquals("https://animasuid.com", animasuid.baseUrl)
    }

    @Test
    fun testGroup3SpecialUrlProtectedScrapers() {
        val ylnime = YlNimeScraper(app)
        assertEquals("ylnime", ylnime.sourceId)
        assertEquals("https://ylnime.com", ylnime.baseUrl)

        val nimegami = NimegamiScraper(app)
        assertEquals("nimegami", nimegami.sourceId)
        assertEquals("https://nimegami.id", nimegami.baseUrl)

        val wibuku = WibukuScraper(app)
        assertEquals("wibuku", wibuku.sourceId)
        assertEquals("https://wibuku.id", wibuku.baseUrl)
    }
}
