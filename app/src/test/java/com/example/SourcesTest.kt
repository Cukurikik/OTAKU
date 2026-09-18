package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.sources.otakudesu.OtakudesuSource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.firstOrNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = OtakuHubApp::class)
class SourcesTest {
    @Test
    fun testOtakudesu() {
        runBlocking {
            val app = ApplicationProvider.getApplicationContext<OtakuHubApp>()
            val source = OtakudesuSource(app)
            println("Testing ${source.sourceName} - ${source.baseUrl}")
            try {
                val items = source.getRecommendations(1).firstOrNull()
                println("Items count: ${items?.size}")
                items?.take(5)?.forEach { println("- ${it.title}") }
            } catch (e: Exception) {
                println("Error: ${e.message}")
            }
        }
    }
}
