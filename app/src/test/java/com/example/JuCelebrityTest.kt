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
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JuCelebrityTest {
    init {
        ShadowLog.stream = System.out
    }

    @Test
    fun testJuCelebrity() = runBlocking {
        val config = DramaDirectSourceCatalog.sources.first { it.sourceId == "jucelebrity" }
        val scraper = GenericDramaScraper(RuntimeEnvironment.getApplication(), config)
        
        println("Fetching Recommendations from JuCelebrity...")
        val popular = scraper.getRecommendations(1).firstOrNull()
        println("Popular: $popular")
        
        if (!popular.isNullOrEmpty()) {
            val item = popular.first()
            println("Fetching Chapters for ${item.id}...")
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
