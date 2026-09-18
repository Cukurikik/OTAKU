package com.example
import org.junit.Test
import org.jsoup.Jsoup
class ScrapeTest2 {
    @Test
    fun testOtakudesu() {
        val url = "https://otakudesu.blog/anime/aishiteru-game-owarasetai-sub-indo/"
        val doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get()
        println("Title: " + doc.title())
        val eps = doc.select(".episodelist ul li")
        println("Episodes: " + eps.size)
        println("Content size: " + doc.html().length)
    }
}
