package com.example.data.sources

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.NetworkModule
import com.example.util.WebViewHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class InterceptedStreamResult(
    val url: String,
    val isM3U8: Boolean,
    val headers: Map<String, String>,
    val cookies: String? = null,
    val userAgent: String? = null,
    val directQualities: List<VideoQuality> = emptyList(),
    val subtitles: List<Subtitle> = emptyList()
)

class WebViewFetcher(private val context: Context) {
    
    companion object {
        // Global concurrency control and instance pooling to prevent excessive Chromium process allocation & OOM
        val webViewSemaphore get() = com.example.engine.pool.WebViewPool.semaphore

        fun acquireInstance(context: Context): WebView = com.example.engine.pool.WebViewPool.acquireInstance(context)
        fun releaseInstance(webView: WebView?) = com.example.engine.pool.WebViewPool.releaseInstance(webView)
        fun clearPool() = com.example.engine.pool.WebViewPool.clearPool()
    }

    /**
     * Primary HTML fetcher with automatic 3-stage fallback:
     * 1. Fast OkHttp fetch
     * 2. Cloudflare Worker Proxy (Self-Healing) if WAF/Turnstile detected
     * 3. Full Headless WebView execution
     */
    suspend fun fetchHtml(url: String, headers: Map<String, String> = emptyMap()): Document = withContext(Dispatchers.IO) {
        val effectiveUa = headers["User-Agent"] ?: headers["user-agent"] ?: NetworkModule.userAgent
        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", effectiveUa)
            
        headers.forEach { (key, value) ->
            if (!key.equals("User-Agent", ignoreCase = true)) {
                requestBuilder.header(key, value)
            }
        }
            
        try {
            val response = NetworkModule.okHttpClient.newCall(requestBuilder.build()).execute()
            val html = response.body?.string() ?: ""
            
            if (response.isSuccessful && !NetworkModule.isWafChallenge(html)) {
                return@withContext Jsoup.parse(html, url)
            }
            
            // If challenged or error 403/503, try Cloudflare Worker Proxy directly
            val proxyHtml = NetworkModule.fetchViaCloudflareWorker(url, headers)
            if (!proxyHtml.isNullOrBlank() && !NetworkModule.isWafChallenge(proxyHtml)) {
                return@withContext Jsoup.parse(proxyHtml, url)
            }
        } catch (e: Exception) {
            // Try proxy on network/connection failure
            val proxyHtml = NetworkModule.fetchViaCloudflareWorker(url, headers)
            if (!proxyHtml.isNullOrBlank() && !NetworkModule.isWafChallenge(proxyHtml)) {
                return@withContext Jsoup.parse(proxyHtml, url)
            }
        }

        // Stage 3: Headless WebView Rendered HTML
        fetchHtmlRendered(url, waitDelayMs = 8000L, headers = headers)
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchHtmlRendered(
        url: String, 
        waitDelayMs: Long = 12000L, 
        targetSelector: String = "",
        headers: Map<String, String> = emptyMap()
    ): Document = withContext(Dispatchers.Main) {
        webViewSemaphore.withPermit {
            var webView: WebView? = null
            try {
                suspendCancellableCoroutine { continuation ->
                    val effectiveUa = headers["User-Agent"] ?: headers["user-agent"] ?: NetworkModule.userAgent
                    val effectiveCookie = headers["Cookie"] ?: headers["cookie"]
                    val effectiveReferer = headers["Referer"] ?: headers["referer"] ?: url
                    val host = runCatching { java.net.URI(url).host }.getOrNull() ?: ""

                    webView = com.example.engine.pool.WebViewPool.acquireInstance(context)
                    WebViewHelper.configureSecureSettings(webView!!, effectiveUa, blockImages = true)

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(webView, true)
                    if (!effectiveCookie.isNullOrBlank()) {
                        val cookiePairs = effectiveCookie.split(";")
                        for (cookiePair in cookiePairs) {
                            val trimmed = cookiePair.trim()
                            if (trimmed.isNotEmpty()) {
                                cookieManager.setCookie(url, trimmed)
                            }
                        }
                        cookieManager.flush()
                    }

                    var pollingJob: kotlinx.coroutines.Job? = null
                    val isFinished = AtomicBoolean(false)

                    fun finishWithDoc(doc: Document, capturedCookies: String? = null) {
                        if (isFinished.compareAndSet(false, true)) {
                            pollingJob?.cancel()
                            try {
                                val finalCookies = capturedCookies ?: cookieManager.getCookie(url)
                                if (!finalCookies.isNullOrBlank() && host.isNotBlank()) {
                                    com.example.data.sources.SourceHeaderStore.setCookie(context, host, finalCookies)
                                    com.example.data.sources.SourceHeaderStore.setUserAgent(context, host, effectiveUa)
                                }
                            } catch (_: Throwable) {}
                            if (continuation.isActive) {
                                continuation.resume(doc)
                            }
                        }
                    }

                    fun failoverToProxy() {
                        if (isFinished.get()) return
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            val proxyHtml = NetworkModule.fetchViaCloudflareWorker(url, headers)
                            if (!proxyHtml.isNullOrBlank() && !NetworkModule.isWafChallenge(proxyHtml)) {
                                finishWithDoc(Jsoup.parse(proxyHtml, url))
                            } else {
                                if (isFinished.compareAndSet(false, true)) {
                                    pollingJob?.cancel()
                                    if (continuation.isActive) {
                                        continuation.resumeWithException(CloudflareException(url))
                                    }
                                }
                            }
                        }
                    }

                    webView!!.webViewClient = object : com.example.network.webview.StealthWebViewClient(
                        onChallengeSolved = { cookies ->
                            if (!isFinished.get()) {
                                webView?.evaluateJavascript("document.documentElement.outerHTML") { html ->
                                    var unescaped = html ?: ""
                                    if (unescaped.startsWith("\"") && unescaped.endsWith("\"")) {
                                        unescaped = unescaped.substring(1, unescaped.length - 1)
                                            .replace("\\u003C", "<")
                                            .replace("\\\"", "\"")
                                            .replace("\\n", "\n")
                                            .replace("\\r", "\r")
                                            .replace("\\t", "\t")
                                            .replace("\\\\", "\\")
                                    }
                                    if (unescaped.isNotBlank() && !NetworkModule.isWafChallenge(unescaped)) {
                                        finishWithDoc(Jsoup.parse(unescaped, url), cookies)
                                    }
                                }
                            }
                        }
                    ) {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val reqUri = request?.url ?: return super.shouldInterceptRequest(view, request)
                            val scheme = reqUri.scheme?.lowercase() ?: ""
                            if (scheme == "file" || scheme == "content") {
                                return WebResourceResponse("text/plain", "UTF-8", null)
                            }
                            val reqUrl = reqUri.toString().lowercase()
                            if (reqUrl.contains("google-analytics") || 
                                reqUrl.contains("doubleclick") || 
                                reqUrl.contains("facebook.net") ||
                                reqUrl.contains("histats.com") ||
                                reqUrl.contains("disqus.com")) {
                                return WebResourceResponse("text/plain", "UTF-8", null)
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
                        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                            com.example.engine.pool.WebViewPool.destroyInstance(view)
                            failoverToProxy()
                            return true
                        }

                        override fun onPageFinished(view: WebView?, currentUrl: String?) {
                            super.onPageFinished(view, currentUrl)
                            val finalUrl = currentUrl ?: url
                            if (isFinished.get()) return
                            pollingJob?.cancel()
                            pollingJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                                var elapsed = 0L
                                while (elapsed < waitDelayMs && continuation.isActive && !isFinished.get()) {
                                    delay(1000L)
                                    elapsed += 1000L
                                    
                                    val jsCheck = """
                                        (function() {
                                            var title = document.title || '';
                                            if (title.indexOf('Just a moment') > -1 || 
                                                title.indexOf('Cloudflare') > -1 || 
                                                title.indexOf('Attention Required') > -1 || 
                                                title.indexOf('Security check') > -1 ||
                                                document.querySelector('#challenge-running, #cf-challenge-running, #turnstile-wrapper') != null) {
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
                                        view?.evaluateJavascript(jsCheck) { res -> jsCont.resume(res) }
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
                                    
                                    if (unescaped == "CF") {
                                        continue
                                    }
                                    
                                    if (unescaped == "WAIT") {
                                        continue
                                    }
                                    
                                    if (unescaped.isNotBlank()) {
                                        val curCookies = cookieManager.getCookie(finalUrl)
                                        finishWithDoc(Jsoup.parse(unescaped, finalUrl), curCookies)
                                        break
                                    }
                                }
                                
                                if (!isFinished.get() && continuation.isActive) {
                                    failoverToProxy()
                                }
                            }
                        }
                    }

                    val additionalHeaders = mutableMapOf<String, String>()
                    if (effectiveReferer.isNotBlank()) additionalHeaders["Referer"] = effectiveReferer
                    if (effectiveUa.isNotBlank()) additionalHeaders["User-Agent"] = effectiveUa
                    if (!effectiveCookie.isNullOrBlank()) additionalHeaders["Cookie"] = effectiveCookie

                    if (additionalHeaders.isNotEmpty()) {
                        webView!!.loadUrl(url, additionalHeaders)
                    } else {
                        webView!!.loadUrl(url)
                    }

                    continuation.invokeOnCancellation {
                        pollingJob?.cancel()
                        webView?.stopLoading()
                        com.example.engine.pool.WebViewPool.releaseInstance(webView)
                    }
                }
            } finally {
                com.example.engine.pool.WebViewPool.releaseInstance(webView)
            }
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun executeJsExtractor(
        url: String, 
        jsCode: String, 
        waitDelayMs: Long = 10000L,
        headers: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.Main) {
        webViewSemaphore.withPermit {
            var webView: WebView? = null
            try {
                suspendCancellableCoroutine { continuation ->
                    val effectiveUa = headers["User-Agent"] ?: headers["user-agent"] ?: NetworkModule.userAgent
                    val effectiveCookie = headers["Cookie"] ?: headers["cookie"]
                    val effectiveReferer = headers["Referer"] ?: headers["referer"] ?: url

                    webView = com.example.engine.pool.WebViewPool.acquireInstance(context)
                    WebViewHelper.configureSecureSettings(webView!!, effectiveUa, blockImages = true)

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(webView, true)
                    if (!effectiveCookie.isNullOrBlank()) {
                        val cookiePairs = effectiveCookie.split(";")
                        for (cookiePair in cookiePairs) {
                            val trimmed = cookiePair.trim()
                            if (trimmed.isNotEmpty()) {
                                cookieManager.setCookie(url, trimmed)
                            }
                        }
                        cookieManager.flush()
                    }
                
                    val isFinished = AtomicBoolean(false)
                
                    webView!!.webViewClient = object : com.example.network.webview.StealthWebViewClient(
                        onChallengeSolved = {}
                    ) {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val reqUri = request?.url ?: return super.shouldInterceptRequest(view, request)
                            val scheme = reqUri.scheme?.lowercase() ?: ""
                            if (scheme == "file" || scheme == "content") {
                                return WebResourceResponse("text/plain", "UTF-8", null)
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                        
                        @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
                        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                            com.example.engine.pool.WebViewPool.destroyInstance(view)
                            if (isFinished.compareAndSet(false, true) && continuation.isActive) {
                                continuation.resumeWithException(Exception("WebView renderer crashed"))
                            }
                            return true
                        }

                        override fun onPageFinished(view: WebView?, currentUrl: String?) {
                            super.onPageFinished(view, currentUrl)
                            if (isFinished.get()) return
                            
                            view?.evaluateJavascript("document.title") { title ->
                                val t = title?.lowercase() ?: ""
                                if (t.contains("just a moment") || t.contains("cloudflare") || t.contains("attention required") || t.contains("security check")) {
                                    return@evaluateJavascript
                                }
                                
                                view.postDelayed({
                                    view.evaluateJavascript(jsCode) { result ->
                                        var unescaped = result ?: ""
                                        if (unescaped.startsWith("\"") && unescaped.endsWith("\"")) {
                                            unescaped = unescaped.substring(1, unescaped.length - 1)
                                                .replace("\\\"", "\"")
                                                .replace("\\\\", "\\")
                                        }
                                        if (isFinished.compareAndSet(false, true) && continuation.isActive) {
                                            continuation.resume(unescaped)
                                        }
                                    }
                                }, waitDelayMs)
                            }
                        }
                    }
                    
                    val additionalHeaders = mutableMapOf<String, String>()
                    if (effectiveReferer.isNotBlank()) additionalHeaders["Referer"] = effectiveReferer
                    if (effectiveUa.isNotBlank()) additionalHeaders["User-Agent"] = effectiveUa
                    if (!effectiveCookie.isNullOrBlank()) additionalHeaders["Cookie"] = effectiveCookie

                    if (additionalHeaders.isNotEmpty()) {
                        webView!!.loadUrl(url, additionalHeaders)
                    } else {
                        webView!!.loadUrl(url)
                    }

                    continuation.invokeOnCancellation {
                        webView?.stopLoading()
                        com.example.engine.pool.WebViewPool.releaseInstance(webView)
                    }
                }
            } finally {
                com.example.engine.pool.WebViewPool.releaseInstance(webView)
            }
        }
    }

    /**
     * Intercepts media stream (.m3u8, .mpd, .mp4) with dynamic JS playback injection,
     * network interception (shouldInterceptRequest), and session cookie/user-agent capture.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun interceptMediaStreamAdvanced(
        url: String,
        timeoutMs: Long = 18000L,
        headers: Map<String, String> = emptyMap()
    ): InterceptedStreamResult? = withContext(Dispatchers.Main) {
        webViewSemaphore.withPermit {
            var webView: WebView? = null
            try {
                suspendCancellableCoroutine { continuation ->
                    val effectiveUa = headers["User-Agent"] ?: headers["user-agent"] ?: NetworkModule.userAgent
                    val effectiveCookie = headers["Cookie"] ?: headers["cookie"]
                    val effectiveReferer = headers["Referer"] ?: headers["referer"] ?: url

                    webView = com.example.engine.pool.WebViewPool.acquireInstance(context)
                    WebViewHelper.configureSecureSettings(webView!!, effectiveUa, blockImages = true)

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(webView, true)
                    if (!effectiveCookie.isNullOrBlank()) {
                        val cookiePairs = effectiveCookie.split(";")
                        for (cookiePair in cookiePairs) {
                            val trimmed = cookiePair.trim()
                            if (trimmed.isNotEmpty()) {
                                cookieManager.setCookie(url, trimmed)
                            }
                        }
                        cookieManager.flush()
                    }

                    val isFinished = AtomicBoolean(false)
                    fun finishWithResult(result: InterceptedStreamResult?) {
                        if (isFinished.compareAndSet(false, true) && continuation.isActive) {
                            continuation.resume(result)
                        }
                    }

                    // JavaScript XHR and Fetch Sniffer to intercept dynamic video manifest calls
                    val jsXhrFetchInterceptor = """
                        (function() {
                            if (window.__streamInterceptorInjected) return;
                            window.__streamInterceptorInjected = true;

                            if (window.fetch) {
                                var origFetch = window.fetch;
                                window.fetch = function() {
                                    var args = arguments;
                                    var fetchUrl = args[0];
                                    if (typeof fetchUrl === 'object' && fetchUrl.url) {
                                        fetchUrl = fetchUrl.url;
                                    }
                                    try {
                                        var response = origFetch.apply(this, args);
                                        response.then(function(res) {
                                            try {
                                                var clone = res.clone();
                                                clone.text().then(function(body) {
                                                    if (window.AndroidBridge && typeof window.AndroidBridge.processApiResponse === 'function') {
                                                        window.AndroidBridge.processApiResponse(fetchUrl || '', body);
                                                    }
                                                }).catch(function(){});
                                            } catch(e) {}
                                        }).catch(function(){});
                                        return response;
                                    } catch(err) {
                                        return origFetch.apply(this, args);
                                    }
                                };
                            }

                            if (window.XMLHttpRequest) {
                                var XHR = window.XMLHttpRequest.prototype;
                                var origOpen = XHR.open;
                                var origSend = XHR.send;

                                XHR.open = function(method, reqUrl) {
                                    this._reqUrl = reqUrl;
                                    return origOpen.apply(this, arguments);
                                };

                                XHR.send = function() {
                                    this.addEventListener('load', function() {
                                        try {
                                            var responseText = this.responseText;
                                            if (responseText && window.AndroidBridge && typeof window.AndroidBridge.processApiResponse === 'function') {
                                                window.AndroidBridge.processApiResponse(this._reqUrl || '', responseText);
                                            }
                                        } catch(e) {}
                                    });
                                    return origSend.apply(this, arguments);
                                };
                            }
                        })();
                    """.trimIndent()

                    // AndroidBridge for JS communication
                    val jsBridge = object {
                        @android.webkit.JavascriptInterface
                        fun processApiResponse(reqUrl: String, body: String) {
                            if (isFinished.get()) return
                            val streams = com.example.data.sources.anime.extractors.ApiResponseParser.extractStreamUrls(reqUrl, body)
                            if (streams.isNotEmpty()) {
                                val discovered = streams.first()
                                val cookies = cookieManager.getCookie(discovered) ?: cookieManager.getCookie(url) ?: effectiveCookie
                                val streamHeaders = mutableMapOf<String, String>(
                                    "Referer" to effectiveReferer,
                                    "User-Agent" to effectiveUa
                                )
                                if (!cookies.isNullOrBlank()) streamHeaders["Cookie"] = cookies
                                finishWithResult(
                                    InterceptedStreamResult(
                                        url = discovered,
                                        isM3U8 = discovered.contains(".m3u8", ignoreCase = true),
                                        headers = streamHeaders,
                                        cookies = cookies,
                                        userAgent = effectiveUa
                                    )
                                )
                            }
                        }
                    }
                    webView!!.addJavascriptInterface(jsBridge, "AndroidBridge")

                    // Auto-trigger script to force JWPlayer, HTML5 Video, Plyr, VideoJS to output source and click play/server
                    val jsExtractAndPlay = """
                        (function() {
                            try {
                                var playBtn = document.querySelector('.play-btn, .play, .vjs-big-play-button, button[aria-label="Play"], #player-start, .jw-display-icon-container, .plyr__control--overlaid, .mejs__overlay-play, .btn-play, #btn-play, a.btn-play, .playbutton, .play-button, .play-toggle');
                                if (playBtn) playBtn.click();

                                var serverBtn = document.querySelector('#server-list li, .server-item, .mirror-item, .server-btn, option[data-server], select[name="server"] option, .east_player_option, .server, .mobius, .mirrors li');
                                if (serverBtn && typeof serverBtn.click === 'function') serverBtn.click();
                                
                                var v = document.querySelector('video');
                                if (v) {
                                    if (v.src && v.src.startsWith('http')) return v.src;
                                    if (v.currentSrc && v.currentSrc.startsWith('http')) return v.currentSrc;
                                    var s = v.querySelector('source');
                                    if (s && s.src && s.src.startsWith('http')) return s.src;
                                }
                                
                                if (window.jwplayer && typeof window.jwplayer === 'function') {
                                    var jw = window.jwplayer();
                                    if (jw && jw.getPlaylist) {
                                        var pl = jw.getPlaylist();
                                        if (pl && pl[0] && pl[0].file) return pl[0].file;
                                    }
                                }
                                
                                if (window.player && window.player.src) {
                                    var pSrc = typeof window.player.src === 'function' ? window.player.src() : window.player.src;
                                    if (pSrc && typeof pSrc === 'string' && pSrc.startsWith('http')) return pSrc;
                                }

                                if (window.hls && window.hls.url) {
                                    return window.hls.url;
                                }
                            } catch(e) {}
                            return '';
                        })();
                    """.trimIndent()

                    webView!!.webViewClient = object : com.example.network.webview.StealthWebViewClient(
                        onChallengeSolved = {}
                    ) {
                        override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, pageUrl, favicon)
                            view?.evaluateJavascript(jsXhrFetchInterceptor, null)
                        }

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                            val lowerUrl = reqUrl.lowercase()

                            // Block analytics, trackers & ads to speed up extraction
                            if (lowerUrl.contains("google-analytics") || 
                                lowerUrl.contains("doubleclick") ||
                                lowerUrl.contains("googlesyndication") ||
                                lowerUrl.contains("facebook.net") ||
                                lowerUrl.contains("histats") ||
                                lowerUrl.contains("disqus.com")) {
                                return WebResourceResponse("text/plain", "UTF-8", null)
                            }

                            // Check for video streams (.m3u8, .mpd, .mp4, /hls/, etc.)
                            val isVideo = (lowerUrl.contains(".m3u8") ||
                                    lowerUrl.contains(".mpd") ||
                                    lowerUrl.contains(".mp4") ||
                                    lowerUrl.contains(".webm") ||
                                    lowerUrl.contains("/hls/") ||
                                    lowerUrl.contains("googlevideo.com/videoplayback") ||
                                    lowerUrl.contains("blogger.com/video") ||
                                    lowerUrl.contains("streamtape.com/get_video") ||
                                    lowerUrl.contains("vidhide") ||
                                    lowerUrl.contains("filelions")) &&
                                    !lowerUrl.endsWith(".jpg") &&
                                    !lowerUrl.endsWith(".png") &&
                                    !lowerUrl.endsWith(".webp") &&
                                    !lowerUrl.endsWith(".gif") &&
                                    !lowerUrl.endsWith(".css") &&
                                    !lowerUrl.endsWith(".js")

                            if (isVideo) {
                                val streamHeaders = mutableMapOf<String, String>()
                                request?.requestHeaders?.forEach { (k, v) -> streamHeaders[k] = v }
                                if (!streamHeaders.containsKey("Referer") && !streamHeaders.containsKey("referer")) {
                                    streamHeaders["Referer"] = effectiveReferer
                                }
                                if (!streamHeaders.containsKey("User-Agent") && !streamHeaders.containsKey("user-agent")) {
                                    streamHeaders["User-Agent"] = effectiveUa
                                }
                                val currentCookie = cookieManager.getCookie(reqUrl) ?: cookieManager.getCookie(url) ?: effectiveCookie
                                if (!currentCookie.isNullOrBlank() && !streamHeaders.containsKey("Cookie") && !streamHeaders.containsKey("cookie")) {
                                    streamHeaders["Cookie"] = currentCookie
                                }
                                finishWithResult(
                                    InterceptedStreamResult(
                                        url = reqUrl,
                                        isM3U8 = lowerUrl.contains(".m3u8") || lowerUrl.contains("/hls/"),
                                        headers = streamHeaders,
                                        cookies = currentCookie,
                                        userAgent = effectiveUa
                                    )
                                )
                            }
                            
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, currentUrl: String?) {
                            super.onPageFinished(view, currentUrl)
                            view?.evaluateJavascript(jsXhrFetchInterceptor, null)
                            view?.evaluateJavascript(jsExtractAndPlay) { directSrc ->
                                var unescaped = directSrc ?: ""
                                if (unescaped.startsWith("\"") && unescaped.endsWith("\"")) {
                                    unescaped = unescaped.substring(1, unescaped.length - 1)
                                        .replace("\\\"", "\"")
                                        .replace("\\\\", "\\")
                                }
                                if (unescaped.isNotBlank() && unescaped.startsWith("http")) {
                                    val currentCookie = cookieManager.getCookie(unescaped) ?: cookieManager.getCookie(url) ?: effectiveCookie
                                    val streamHeaders = mutableMapOf<String, String>(
                                        "Referer" to effectiveReferer,
                                        "User-Agent" to effectiveUa
                                    )
                                    if (!currentCookie.isNullOrBlank()) streamHeaders["Cookie"] = currentCookie
                                    finishWithResult(
                                        InterceptedStreamResult(
                                            url = unescaped,
                                            isM3U8 = unescaped.contains(".m3u8", ignoreCase = true),
                                            headers = streamHeaders,
                                            cookies = currentCookie,
                                            userAgent = effectiveUa
                                        )
                                    )
                                }
                            }
                        }

                        @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
                        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                            com.example.engine.pool.WebViewPool.destroyInstance(view)
                            finishWithResult(null)
                            return true
                        }
                    }
                    
                    val additionalHeaders = mutableMapOf<String, String>()
                    if (effectiveReferer.isNotBlank()) additionalHeaders["Referer"] = effectiveReferer
                    if (effectiveUa.isNotBlank()) additionalHeaders["User-Agent"] = effectiveUa
                    if (!effectiveCookie.isNullOrBlank()) additionalHeaders["Cookie"] = effectiveCookie

                    if (additionalHeaders.isNotEmpty()) {
                        webView!!.loadUrl(url, additionalHeaders)
                    } else {
                        webView!!.loadUrl(url)
                    }
                    
                    kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                        delay(timeoutMs)
                        finishWithResult(null)
                    }

                    continuation.invokeOnCancellation {
                        webView?.stopLoading()
                        com.example.engine.pool.WebViewPool.releaseInstance(webView)
                    }
                }
            } finally {
                com.example.engine.pool.WebViewPool.releaseInstance(webView)
            }
        }
    }

    /**
     * Backward-compatible simple stream interception (returns stream URL).
     */
    suspend fun interceptMediaStream(
        url: String, 
        timeoutMs: Long = 18000L,
        headers: Map<String, String> = emptyMap()
    ): String? {
        return interceptMediaStreamAdvanced(url, timeoutMs, headers)?.url
    }
}
