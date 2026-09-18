package com.example

import org.jsoup.Jsoup
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
class DebugParseJsoupTest {
    @Test
    fun testJsoup() {
        val html = """<a href="https://v19.kuramanime.ing/anime/1533/road-of-naruto"><h5 class="sidebar-title-h5 px-2 py-2">Road of Naruto</h5></a>"""
        val doc = Jsoup.parse(html, "https://v19.kuramanime.ing")
        val cards = doc.select(".product__sidebar__view__item, .product__item, a")
        println("STANDARD_OUT: cards size = " + cards.size)
        for (card in cards) {
            val anchor = card.selectFirst("a") ?: if (card.tagName() == "a") card else null
            println("STANDARD_OUT: card tagName = " + card.tagName())
            println("STANDARD_OUT: anchor href = " + anchor?.absUrl("href"))
        }
    }
}
