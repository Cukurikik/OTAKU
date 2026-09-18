package com.example.engine.pool

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.NetworkModule
import com.example.util.WebViewHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * Singleton WebView Pool & Resource Manager for Background Headless Extraction.
 * 
 * Prevents Out-Of-Memory (OOM) and Application Not Responding (ANR) errors by:
 * 1. Strictly limiting concurrent headless WebView extractions via Coroutine Semaphore (MAX_CONCURRENT_EXTRACTIONS = 2).
 * 2. Reusing pooled WebView instances across extraction jobs instead of spawning new instances per request.
 * 3. Enforcing full lifecycle cleanup (stopLoading, clearHistory, remove JS interfaces, load about:blank) on release/timeout.
 * 4. Isolating all DOM & WebKit operations onto Dispatchers.Main.immediate / Main Looper.
 * 5. Providing memory trimming / destruction on low memory signals.
 */
object WebViewPool {

    const val MAX_CONCURRENT_EXTRACTIONS = 2
    private const val MAX_POOL_SIZE = 2

    val semaphore = Semaphore(permits = MAX_CONCURRENT_EXTRACTIONS)

    private val pool = ArrayDeque<WebView>()
    private val activeInstancesCount = AtomicInteger(0)

    /**
     * Acquires an idle WebView instance from the pool or creates a new one if available slots exist.
     * Safe to call on Main thread.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun acquireInstance(context: Context): WebView {
        synchronized(pool) {
            while (pool.isNotEmpty()) {
                val candidate = pool.removeFirst()
                if (isAlive(candidate)) {
                    resetWebView(candidate)
                    activeInstancesCount.incrementAndGet()
                    return candidate
                } else {
                    WebViewHelper.safeDestroy(candidate)
                }
            }
        }

        val targetContext = context.applicationContext ?: context
        val newInstance = try {
            WebView(targetContext)
        } catch (_: Throwable) {
            WebView(context)
        }
        WebViewHelper.configureSecureSettings(newInstance, NetworkModule.userAgent, blockImages = true)
        activeInstancesCount.incrementAndGet()
        return newInstance
    }

    /**
     * Resets and returns a WebView instance back to the pool or destroys it if the pool is full.
     * Safe to call from any thread.
     */
    fun releaseInstance(webView: WebView?) {
        if (webView == null) return

        val resetAndStore = Runnable {
            try {
                resetWebView(webView)
                activeInstancesCount.updateAndGet { if (it > 0) it - 1 else 0 }

                synchronized(pool) {
                    if (pool.size < MAX_POOL_SIZE && isAlive(webView) && !pool.contains(webView)) {
                        pool.addLast(webView)
                    } else {
                        WebViewHelper.safeDestroy(webView)
                    }
                }
            } catch (e: Exception) {
                WebViewHelper.safeDestroy(webView)
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            resetAndStore.run()
        } else {
            Handler(Looper.getMainLooper()).post(resetAndStore)
        }
    }

    /**
     * Destroys a specific WebView instance and decrements active count.
     */
    fun destroyInstance(webView: WebView?) {
        if (webView == null) return
        activeInstancesCount.decrementAndGet()
        WebViewHelper.safeDestroy(webView)
    }

    /**
     * Clears and destroys all cached idle WebViews in the pool to release RAM immediately.
     */
    fun clearPool() {
        val cleanup = Runnable {
            val toDestroy = mutableListOf<WebView>()
            synchronized(pool) {
                while (pool.isNotEmpty()) {
                    toDestroy.add(pool.removeFirst())
                }
            }
            toDestroy.forEach { WebViewHelper.safeDestroy(it) }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanup.run()
        } else {
            Handler(Looper.getMainLooper()).post(cleanup)
        }
    }

    /**
     * High-level scoped execution wrapper with automated semaphore permit acquisition,
     * lifecycle recycling, and cancellation safety.
     */
    suspend fun <T> withWebView(
        context: Context,
        block: suspend (WebView) -> T
    ): T = semaphore.withPermit {
        withContext(Dispatchers.Main.immediate) {
            val webView = acquireInstance(context)
            try {
                block(webView)
            } finally {
                releaseInstance(webView)
            }
        }
    }

    private fun resetWebView(webView: WebView) {
        try {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            try {
                webView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            } catch (_: Throwable) {}
            try {
                webView.removeJavascriptInterface("AndroidBridge")
                webView.removeJavascriptInterface("android")
                webView.removeJavascriptInterface("WebAppInterface")
            } catch (_: Throwable) {}
            webView.webViewClient = android.webkit.WebViewClient()
            webView.webChromeClient = null
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
        } catch (_: Throwable) {}
    }

    private fun isAlive(webView: WebView): Boolean {
        return try {
            webView.settings != null
        } catch (e: Exception) {
            false
        }
    }

    val currentPoolSize: Int get() = getPoolSize()
    fun getPoolSize(): Int = synchronized(pool) { pool.size }
    fun getActiveCount(): Int = activeInstancesCount.get()
}
