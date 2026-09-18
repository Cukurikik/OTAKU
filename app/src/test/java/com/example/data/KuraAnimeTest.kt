package com.example.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.OtakuHubApp
import com.example.data.sources.kuraanime.KuraAnimeSource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.firstOrNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = OtakuHubApp::class)
class KuraAnimeTest {
    @Test
    fun testKuraAnime() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<OtakuHubApp>()
        val source = KuraAnimeSource(app)
        try {
            val items = source.getRecommendations(1).firstOrNull()
            println("KuraAnime items size: ${items?.size}")
            items?.forEach { println(it.title) }
        } catch (e: Exception) {
            println("KuraAnime failed: ${e.message}")
            e.printStackTrace()
        }
    }
}
