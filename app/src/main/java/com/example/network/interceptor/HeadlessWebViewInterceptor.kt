package com.example.network.interceptor

import android.content.Context
import android.webkit.CookieManager
import com.example.data.NetworkModule
import com.example.data.sources.InterceptedStreamResult
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.WebViewFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document

/**
 * HeadlessWebViewInterceptor:
 * Komponen latar belakang yang mengeksekusi runtime JavaScript lengkap
 * untuk menyelesaikan tantangan Cloudflare Turnstile, WAF, dan mengambil clearance cookie (cf_clearance, cf_bm)
 * serta mencegat URL stream langsung dari network traffic.
 */
class HeadlessWebViewInterceptor(
    private val context: Context,
    private val fetcher: WebViewFetcher = WebViewFetcher(context)
) {

    /**
     * Mengeksekusi bypass Cloudflare Turnstile / WAF secara headless di latar belakang
     * dan mengembalikan dokumen HTML yang sudah selesai dirender.
     */
    suspend fun bypassAndFetchHtml(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = 18000L
    ): Document = withContext(Dispatchers.Main) {
        val doc = fetcher.fetchHtmlRendered(url, timeoutMs, headers = headers)
        
        // Sync cookies from CookieManager
        val host = java.net.URI(url).host ?: ""
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(url) ?: ""
        if (cookies.contains("cf_clearance")) {
            // Update WebkitCookieJar
            val hostCookies = cookies.split(";").map { it.trim() }
            for (cookie in hostCookies) {
                if (cookie.startsWith("cf_clearance=")) {
                    cookieManager.setCookie(url, cookie)
                }
            }
        }
        doc
    }

    /**
     * Mencegat stream video (HLS/MP4) secara headless dari network traffic WebView.
     */
    suspend fun interceptStream(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = 15000L
    ): InterceptedStreamResult? = withContext(Dispatchers.Main) {
        fetcher.interceptMediaStreamAdvanced(url, timeoutMs, headers)
    }
}
