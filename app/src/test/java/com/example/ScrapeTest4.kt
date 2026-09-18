package com.example

import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.Request

class ScrapeTest4 {
    @Test
    fun testOkHttp() {
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder()
            .url("https://otakudesu.blog/anime/aishiteru-game-owarasetai-sub-indo/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        println("Code: " + response.code)
        println("Contains episodelist: " + body.contains("episodelist"))
        println("Contains Cloudflare: " + body.contains("cloudflare", ignoreCase = true))
    }
}
