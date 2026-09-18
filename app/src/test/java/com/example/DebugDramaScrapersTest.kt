package com.example

import com.example.data.sources.drama.specific.LicilScraper
import com.example.data.sources.drama.specific.LokalfilmScraper
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DebugDramaScrapersTest {
    init {
        ShadowLog.stream = System.out
    }

    @Test
    fun testLokalfilmAndLicil() {
        runBlocking {
            val context = RuntimeEnvironment.getApplication()
            val lokalfilm = LokalfilmScraper(context)
            val licil = LicilScraper(context)

            println("=== TESTING LOKALFILM REAL FLOW ===")
            println("Lokalfilm defaultBaseUrl: ${lokalfilm.defaultBaseUrl}")
            println("Lokalfilm effectiveBaseUrl: ${lokalfilm.effectiveBaseUrl(context)}")
            try {
                // Call fetchDocument by subclassing or calling getRecommendations
                lokalfilm.getRecommendations(1).collect {
                    println("Lokalfilm collected: ${it.size} items")
                }
            } catch (e: Exception) {
                println("Lokalfilm caught exception: ${e.message}")
                e.printStackTrace()
            }

            println("\n=== TESTING LICIL REAL FLOW ===")
            println("Licil defaultBaseUrl: ${licil.defaultBaseUrl}")
            println("Licil effectiveBaseUrl: ${licil.effectiveBaseUrl(context)}")
            try {
                licil.getRecommendations(1).collect {
                    println("Licil collected: ${it.size} items")
                }
            } catch (e: Exception) {
                println("Licil caught exception: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
