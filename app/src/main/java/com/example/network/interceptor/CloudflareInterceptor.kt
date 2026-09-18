package com.example.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.example.OtakuHubApp
import com.example.data.NetworkModule
import com.example.data.sources.SourceHeaderStore
import com.example.network.webview.StealthWebViewClient
import com.example.util.WebViewHelper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * CloudflareInterceptor:
 * Mengimplementasikan alur hardening Cloudflare Anti-Bot Bypass:
 * 1. Deteksi HTTP 403/503 atau indikasi Cloudflare Turnstile / Error 1020.
 * 2. Coba Cloudflare Worker Proxy terlebih dahulu.
 * 3. Jika Worker Proxy gagal / Error 1020: Jalankan Stealth WebView Client (< 5.0 detik).
 * 4. Jika timeout >= 5.0 detik: Pemicu Interactive Challenge BottomSheet (pengguna selesaikan Turnstile).
 * 5. Ekstrak cookie (cf_clearance, __cf_bm) dan simpan ke CookieManager serta SourceHeaderStore.
 * 6. Lakukan auto-retry request dengan header terverifikasi.
 */
class CloudflareInterceptor(
    private val context: Context? = null
) : Interceptor {

    private val handler = Handler(Looper.getMainLooper())

    private fun getAppContext(): Context {
        return context ?: OtakuHubApp.instance
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url.toString()

        // Jangan cegat jika request ditujukan ke Cloudflare Worker Proxy itu sendiri
        if (originalUrl.startsWith(NetworkModule.WORKER_BASE_URL)) {
            return chain.proceed(originalRequest)
        }

        val response: Response = try {
            chain.proceed(originalRequest)
        } catch (e: IOException) {
            // Jika koneksi langsung di-drop saat handshake TLS oleh anti-bot
            return handleBypassPipeline(chain, originalRequest) ?: throw e
        }

        if (response.code !in listOf(403, 503)) {
            return response
        }

        val responseBody = try {
            response.peekBody(Long.MAX_VALUE).string()
        } catch (_: Exception) {
            ""
        }

        if (!isCloudflareChallenge(response, responseBody)) {
            return response
        }

        response.close()
        return handleBypassPipeline(chain, originalRequest)
            ?: throw IOException("Gagal menyelesaikan Cloudflare Challenge untuk: ${originalRequest.url}")
    }

    /**
     * Memeriksa apakah response merupakan tantangan Cloudflare / Turnstile
     */
    private fun isCloudflareChallenge(response: Response, body: String): Boolean {
        val serverHeader = response.header("Server") ?: ""
        val cfRay = response.header("cf-ray") ?: ""
        val lowerBody = body.lowercase()

        val isCfServer = serverHeader.contains("cloudflare", ignoreCase = true) || cfRay.isNotBlank()
        val hasChallengeKeywords = lowerBody.contains("cf-browser-verification") ||
                lowerBody.contains("just a moment") ||
                lowerBody.contains("attention required") ||
                lowerBody.contains("security check") ||
                lowerBody.contains("ray_id") ||
                lowerBody.contains("ray id:") ||
                lowerBody.contains("cf-challenge") ||
                lowerBody.contains("challenge-running") ||
                lowerBody.contains("turnstile") ||
                lowerBody.contains("error 1020")

        return (response.code in listOf(403, 503)) && (isCfServer || hasChallengeKeywords)
    }

    /**
     * Pipeline Eksekusi Bypass Bertingkat:
     * Step C -> Step E, F, G -> Step I, J -> Step H, K -> Step L
     */
    private fun handleBypassPipeline(chain: Interceptor.Chain, request: Request): Response? {
        val targetUrl = request.url.toString()

        // 1. Cobakan Cloudflare Worker Proxy terlebih dahulu
        val workerResult = NetworkModule.fetchViaCloudflareWorker(targetUrl)
        if (!workerResult.isNullOrBlank() && !NetworkModule.isWafChallenge(workerResult)) {
            val mediaType = "text/html; charset=utf-8".toMediaTypeOrNull()
            return Response.Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK (Cloudflare Worker Proxy)")
                .body(workerResult.toResponseBody(mediaType))
                .build()
        }

        // 2. Evaluasi Bypass Otomatis dengan Stealth WebView (Ambang batas T = 8.0 detik)
        val automatedCookies = attemptStealthAutomatedBypass(targetUrl, timeoutSeconds = 8)

        val solvedCookies = if (!automatedCookies.isNullOrBlank()) {
            automatedCookies
        } else {
            // 3. Jika T >= 5.0s, alihkan ke Interactive Challenge BottomSheet
            triggerInteractiveSolver(targetUrl)
        }

        if (solvedCookies.isNullOrBlank()) {
            return null
        }

        // 4. Sinkronkan cookie ke CookieManager & SourceHeaderStore
        persistCookies(request.url.host, targetUrl, solvedCookies)

        // 5. Retry original request dengan cookie dan headers yang sudah terverifikasi
        val retriedRequest = request.newBuilder()
            .header("User-Agent", NetworkModule.userAgent)
            .header("Cookie", solvedCookies)
            .header("Referer", "${request.url.scheme}://${request.url.host}/")
            .build()

        return chain.proceed(retriedRequest)
    }

    /**
     * Menjalankan WebView di background dengan Stealth.js untuk mencoba menyelesaikan
     * challenge secara otomatis dalam kurun waktu 5.0 detik.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun attemptStealthAutomatedBypass(url: String, timeoutSeconds: Long = 5): String? {
        val latch = CountDownLatch(1)
        val isResolved = AtomicBoolean(false)
        val capturedCookies = AtomicReference<String?>(null)
        var webViewInstance: WebView? = null

        handler.post {
            try {
                val ctx = getAppContext()
                val webView = WebView(ctx)
                webViewInstance = webView
                WebViewHelper.configureSecureSettings(webView, NetworkModule.userAgent, blockImages = true)

                webView.webChromeClient = WebChromeClient()
                webView.webViewClient = StealthWebViewClient(
                    onChallengeSolved = { cookies ->
                        if (isResolved.compareAndSet(false, true)) {
                            capturedCookies.set(cookies)
                            CookieManager.getInstance().flush()
                            WebViewHelper.safeDestroy(webView)
                            latch.countDown()
                        }
                    }
                )

                webView.loadUrl(url)
            } catch (_: Throwable) {
                latch.countDown()
            }
        }

        try {
            latch.await(timeoutSeconds, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
        } finally {
            if (!isResolved.get()) {
                handler.post {
                    WebViewHelper.safeDestroy(webViewInstance)
                }
            }
        }

        return if (isResolved.get()) capturedCookies.get() else null
    }

    /**
     * Memicu Interactive Challenge BottomSheet dan menunggu hingga pengguna menyelesaikan
     * centang Turnstile secara manual (maksimal 60 detik).
     */
    private fun triggerInteractiveSolver(url: String): String? {
        val deferred = ChallengeStateManager.requestInteractiveSolver(
            url = url,
            userAgent = NetworkModule.userAgent,
            timeoutSeconds = 60
        )

        return try {
            runBlocking {
                withTimeoutOrNull(60_000L) {
                    deferred.await()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Menyimpan clearance cookie ke CookieManager dan SourceHeaderStore
     */
    private fun persistCookies(host: String, url: String, cookies: String) {
        try {
            val cookieManager = CookieManager.getInstance()
            if (cookieManager != null) {
                val cookieParts = cookies.split(";")
                for (part in cookieParts) {
                    val trimmed = part.trim()
                    if (trimmed.isNotEmpty()) {
                        cookieManager.setCookie(url, trimmed)
                    }
                }
                cookieManager.flush()
            }
        } catch (_: Throwable) {}

        try {
            val ctx = getAppContext()
            SourceHeaderStore.setCookie(ctx, host, cookies)
            SourceHeaderStore.setUserAgent(ctx, host, NetworkModule.userAgent)
        } catch (_: Throwable) {}
    }
}
