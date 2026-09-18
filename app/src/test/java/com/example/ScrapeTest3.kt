package com.example
import org.junit.Test
import org.jsoup.Jsoup
class ScrapeTest3 {
    @Test
    fun testOtakudesu() {
        val url = "https://otakudesu.blog/anime/aishiteru-game-owarasetai-sub-indo/"
        val doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get()
        println("Contains episodelist: " + doc.html().contains("episodelist"))
        println("Contains epztipe: " + doc.html().contains("epztipe"))
    }
}
