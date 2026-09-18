package com.example

import com.example.data.models.ContentItem
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.junit.Test
import org.junit.Assert.*

class DebugParseCardsTest {
    @Test
    fun testParseCards() = runBlocking {
        try {
            val url = "https://v19.kuramanime.ing/anime?search=Naruto&order_by=latest"
            val proxyUrl = "https://anime-aggregator.njirlah.workers.dev/?url=" + java.net.URLEncoder.encode(url, "UTF-8")
            val request = okhttp3.Request.Builder()
                .url(proxyUrl)
                .header("User-Agent", com.example.data.NetworkModule.userAgent)
                .build()
            val response = com.example.data.NetworkModule.okHttpClient.newCall(request).execute()
            val html = response.body?.string() ?: ""
            val doc = Jsoup.parse(html, url)
            
            val cards = doc.select(".product__sidebar__view__item, .product__item")
            println("STANDARD_OUT: Cards found: " + cards.size)
            
            cards.forEachIndexed { index, card ->
                val anchor = card.selectFirst("a") ?: if (card.tagName() == "a") card else null
                val absUrl = anchor?.absUrl("href").orEmpty()
                
                val titleNode = card.selectFirst("h5, .sidebar-title-h5")
                val title = titleNode?.text()?.trim().orEmpty()
                    .ifBlank { anchor?.attr("title").orEmpty().trim() }
                    .ifBlank { anchor?.text().orEmpty().trim() }
                    
                println("STANDARD_OUT: Card $index -> absUrl: '$absUrl' | title: '$title'")
            }
        } catch (e: Exception) {
            println("STANDARD_OUT: Network not available during local unit test: ${e.message}")
        }
    }
}
