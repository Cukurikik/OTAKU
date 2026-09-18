package com.example

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.runBlocking
import com.example.data.sources.anime.specific.AnimasuIDScraper
import kotlinx.coroutines.flow.firstOrNull
import org.robolectric.shadows.ShadowLog
import kotlinx.coroutines.flow.toList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WebViewTest {
    init {
        ShadowLog.stream = System.out
    }

    @Test
    fun testAnimasu() = runBlocking {
        val scraper = AnimasuIDScraper(RuntimeEnvironment.getApplication())
        println("Fetching chapters...")
        val chapters = scraper.getChapters("https://animasuid.com/anime/jujutsu-kaisen-s2-sub-indo/").toList().flatten()
        println("Chapters: \$chapters")
        
        if (chapters.isNotEmpty()) {
            println("Fetching video stream for \${chapters.first().url} ...")
            val streamInfo = scraper.getVideoStream(chapters.first().url).firstOrNull()
            println("Stream Info: \$streamInfo")
        }
    }
}
