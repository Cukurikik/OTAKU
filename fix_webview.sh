#!/bin/bash
cat << 'INNER' > app/src/main/java/com/example/data/sources/WebViewFetcher.kt
package com.example.data.sources

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebViewFetcher(private val context: Context) {
    
    suspend fun fetchHtml(url: String, headers: Map<String, String> = emptyMap()): org.jsoup.nodes.Document = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", com.example.data.NetworkModule.userAgent)
            
        headers.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }
            
        val response = com.example.data.NetworkModule.okHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) throw Exception("Failed to fetch HTML: HTTP ${response.code}")
        val html = response.body?.string() ?: ""
        org.jsoup.Jsoup.parse(html, url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchHtmlRendered(url: String, waitDelayMs: Long = 8000, targetSelector: String = ""): Document = withContext(Dispatchers.Main) {
        var webView: WebView? = null
        try {
            suspendCancellableCoroutine { continuation ->
                webView = WebView(context)
                webView!!.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = NetworkModule.userAgent
                    blockNetworkImage = false
                    loadsImagesAutomatically = true
                }

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(webView, true)

                var pollingJob: kotlinx.coroutines.Job? = null
                var finished = false

                webView!!.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val reqUrl = request?.url?.toString()?.lowercase() ?: ""
                        if (reqUrl.contains("google-analytics") || 
                            reqUrl.contains("doubleclick") || 
                            reqUrl.contains("facebook.net") ||
                            reqUrl.endsWith(".mp4") ||
                            reqUrl.endsWith(".webm") ||
                            reqUrl.contains("disqus.com")) {
                            return WebResourceResponse("text/plain", "UTF-8", null)
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
                    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                        if (continuation.isActive) {
                            continuation.resumeWithException(Exception("WebView renderer crashed"))
                        }
                        return true
                    }

                    override fun onPageFinished(view: WebView, currentUrl: String) {
                        if (finished) return
                        pollingJob?.cancel()
                        pollingJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                            var elapsed = 0L
                            while (elapsed < 15000L && continuation.isActive) {
                                delay(1000L)
                                elapsed += 1000L
                                
                                view.evaluateJavascript("window.scrollBy(0, 500);", null)
                                
                                val jsCheck = """
                                    (function() {
                                        var title = document.title || '';
                                        if (title.indexOf('Just a moment') > -1 || title.indexOf('Cloudflare') > -1 || title.indexOf('Attention Required') > -1 || title.indexOf('Security check') > -1) {
                                            return 'CF';
                                        }
                                        var target = '$targetSelector';
                                        if (target !== '') {
                                            if (document.querySelector(target) != null) {
                                                return document.documentElement.outerHTML;
                                            } else {
                                                return 'WAIT';
                                            }
                                        }
                                        return document.documentElement.outerHTML;
                                    })();
                                """.trimIndent()
                                
                                val htmlResult = kotlin.coroutines.suspendCoroutine<String?> { jsCont ->
                                    view.evaluateJavascript(jsCheck) { res -> jsCont.resume(res) }
                                }
                                
                                var unescaped = htmlResult ?: ""
                                if (unescaped.startsWith("\"") && unescaped.endsWith("\"")) {
                                    unescaped = unescaped.substring(1, unescaped.length - 1)
                                        .replace("\\u003C", "<")
                                        .replace("\\\"", "\"")
                                        .replace("\\n", "\n")
                                        .replace("\\r", "\r")
                                        .replace("\\t", "\t")
                                        .replace("\\\\", "\\")
                                }
                                
                                if (unescaped == "CF" || unescaped == "WAIT") {
                                    continue
                                }
                                
                                if (elapsed >= waitDelayMs) {
                                    finished = true
                                    if (continuation.isActive) {
                                        continuation.resume(Jsoup.parse(unescaped, currentUrl))
                                    }
                                    break
                                }
                            }
                            
                            if (!finished && continuation.isActive) {
                                finished = true
                                continuation.resumeWithException(CloudflareException(url))
                            }
                        }
                    }
                }

                webView!!.loadUrl(url)

                continuation.invokeOnCancellation {
                    pollingJob?.cancel()
                }
            }
        } finally {
            webView?.destroy()
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun executeJsExtractor(url: String, jsCode: String, waitDelayMs: Long = 12000): String = withContext(Dispatchers.Main) {
        var webView: WebView? = null
        try {
            suspendCancellableCoroutine { continuation ->
                webView = WebView(context)
                webView!!.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = NetworkModule.userAgent
                }
                
                var finished = false
                
                webView!!.webViewClient = object : WebViewClient() {
                    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
                    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                        if (continuation.isActive) {
                            continuation.resumeWithException(Exception("WebView renderer crashed"))
                        }
                        return true
                    }

                    override fun onPageFinished(view: WebView, currentUrl: String) {
                        if (finished) return
                        
                        view.evaluateJavascript("document.title") { title ->
                            if (title != null && (title.contains("Just a moment") || title.contains("Cloudflare") || title.contains("Attention Required!") || title.contains("Security check"))) {
                                return@evaluateJavascript
                            }
                            
                            finished = true
                            
                            view.postDelayed({
                                view.evaluateJavascript(jsCode) { result ->
                                    var unescaped = result ?: ""
                                    if (unescaped.startsWith("\"") && unescaped.endsWith("\"")) {
                                        unescaped = unescaped.substring(1, unescaped.length - 1)
                                            .replace("\\\"", "\"")
                                            .replace("\\\\", "\\")
                                    }
                                    if (continuation.isActive) {
                                        continuation.resume(unescaped)
                                    }
                                }
                            }, waitDelayMs)
                        }
                    }
                }
                
                webView!!.loadUrl(url)
            }
        } finally {
            webView?.destroy()
        }
    }
}
INNER
