package com.example.network.interceptor

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.example.engine.pool.WebViewPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoUrlInterceptorSanitizationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WebViewPool.clearPool()
    }

    @Test
    fun testWebViewSanitizationOnRelease() = runBlocking(Dispatchers.Main) {
        val webView = WebViewPool.acquireInstance(context)
        assertNotNull("Acquired WebView should not be null", webView)

        // Simulate attaching listeners and JS bridges with aligned signature
        val webAppInterface = WebAppInterface { videoUrl: String, subtitle: com.example.data.sources.Subtitle?, resolution: String -> }
        webView.addJavascriptInterface(webAppInterface, "AndroidBridge")
        webView.loadUrl("https://example.com/video-embed")

        // Release instance back to pool
        WebViewPool.releaseInstance(webView)
        ShadowLooper.idleMainLooper()

        // Verify pool size and state
        assertTrue("Pool should contain the sanitized instance", WebViewPool.currentPoolSize >= 1)

        val reacquired = WebViewPool.acquireInstance(context)
        assertNotNull("webViewClient should be standard inert client after sanitization", reacquired.webViewClient)
        assertNull("webChromeClient should be cleared after sanitization", reacquired.webChromeClient)

        WebViewPool.releaseInstance(reacquired)
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun testIdempotentDoubleReleaseSafety() = runBlocking(Dispatchers.Main) {
        val webView = WebViewPool.acquireInstance(context)
        assertNotNull(webView)

        // Multiple calls to releaseInstance must not corrupt activeInstancesCount or pool size
        WebViewPool.releaseInstance(webView)
        WebViewPool.releaseInstance(webView)
        ShadowLooper.idleMainLooper()

        assertEquals("Pool size must be capped at 1 for a single instance", 1, WebViewPool.currentPoolSize)
        assertEquals("Active instance count must be 0", 0, WebViewPool.getActiveCount())
    }
}
