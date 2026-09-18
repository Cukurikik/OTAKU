package com.example.data
import org.junit.Test
import kotlinx.coroutines.runBlocking
import com.example.data.sources.WebViewFetcher
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class FetcherTest {
    @Test
    fun testMgKomik() = runBlocking {
        val fetcher = WebViewFetcher(ApplicationProvider.getApplicationContext())
        val doc = fetcher.fetchHtml("https://web1.mgkomik.cc/manga/?page=1&order=update")
        println("DOC TITLE: " + doc.title())
        val list = doc.select(".bixbox .bsx, .bixbox .listupd .bs, .listupd .bs, .page-item.detail, .item")
        println("LIST SIZE: " + list.size)
    }
}
