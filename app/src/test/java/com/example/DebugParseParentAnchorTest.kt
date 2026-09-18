package com.example

import org.jsoup.Jsoup
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
class DebugParseParentAnchorTest {
    @Test
    fun testJsoup() {
        val html = """<a href="https://v19.kuramanime.ing/anime/1533/road-of-naruto">
                <div class="product__sidebar__view__item set-bg" data-setbg="https://img.jpg">
                    <h5 class="sidebar-title-h5 px-2 py-2">Road of Naruto</h5>
                </div>
            </a>"""
        val doc = Jsoup.parse(html, "https://v19.kuramanime.ing")
        val cards = doc.select("a:has(.product__sidebar__view__item), a:has(.product__item)")
        println("STANDARD_OUT: cards size = " + cards.size)
        for (card in cards) {
            val anchor = card.selectFirst("a") ?: if (card.tagName() == "a") card else card.parent()?.takeIf { it.tagName() == "a" }
            println("STANDARD_OUT: card tagName = " + card.tagName())
            println("STANDARD_OUT: anchor href = " + anchor?.absUrl("href"))
            println("STANDARD_OUT: image = " + card.selectFirst(".set-bg")?.attr("data-setbg"))
        }
    }
}
