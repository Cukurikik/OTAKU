package com.example

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
class DebugParseOriginalTest {
    @Test
    fun testOriginal() = runBlocking {
        val url = "https://v19.kuramanime.ing/anime?search=Naruto&order_by=latest"
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", com.example.data.NetworkModule.userAgent)
            .build()
        val response = com.example.data.NetworkModule.okHttpClient.newCall(request).execute()
        val html = response.body?.string() ?: ""
        println("STANDARD_OUT: Original code: " + response.code)
        println("STANDARD_OUT: Original HTML: " + html.take(200).replace("\n", " "))
    }
}
