package com.example

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.runBlocking
import com.example.data.sources.drama.DramaDirectSourceCatalog
import com.example.data.sources.drama.GenericDramaScraper
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DramaTest {
    init {
        ShadowLog.stream = System.out
    }

    @Test
    fun testLicil() = runBlocking {
        val config = DramaDirectSourceCatalog.sources.first { it.sourceId == "licil" }
        val scraper = GenericDramaScraper(RuntimeEnvironment.getApplication(), config)
        
        println("Fetching Recommendations from Licil...")
        val popular = scraper.getRecommendations(1).firstOrNull()
        println("Popular: $popular")
        
        if (!popular.isNullOrEmpty()) {
            val item = popular.first()
            println("Fetching Detail for ${item.id}...")
            val detail = scraper.getDetail(item.id)
            println("Detail: $detail")
            
            println("Fetching Chapters...")
            val chapters = scraper.getChapters(item.id).firstOrNull()
            println("Chapters: $chapters")
            
            if (!chapters.isNullOrEmpty()) {
                println("Fetching Video Stream for ${chapters.first().url}...")
                val streamInfo = scraper.getVideoStream(chapters.first().url).firstOrNull()
                println("Stream Info: $streamInfo")
            }
        }
    }
}
