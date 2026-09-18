package com.example

import com.example.engine.pool.WebViewPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WebViewPoolTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        WebViewPool.clearPool()
    }

    @Test
    fun testAcquireAndReleaseCycle() = runBlocking(Dispatchers.Main) {
        val wv1 = WebViewPool.acquireInstance(context)
        assertNotNull("Acquired WebView should not be null", wv1)
        assertEquals("Pool should be empty after acquiring first instance", 0, WebViewPool.currentPoolSize)

        WebViewPool.releaseInstance(wv1)
        assertEquals("Pool size should be 1 after releasing instance", 1, WebViewPool.currentPoolSize)

        val wv2 = WebViewPool.acquireInstance(context)
        assertEquals("Reacquired WebView should reuse the pooled instance", wv1, wv2)
        assertEquals("Pool size should be 0 after re-acquiring", 0, WebViewPool.currentPoolSize)

        WebViewPool.releaseInstance(wv2)
        assertEquals("Pool size should be 1 after second release", 1, WebViewPool.currentPoolSize)
    }

    @Test
    fun testPoolCapacityCap() = runBlocking(Dispatchers.Main) {
        val instances = List(5) { WebViewPool.acquireInstance(context) }
        instances.forEach { WebViewPool.releaseInstance(it) }

        assertTrue(
            "Pool size should be capped at maxPoolSize (2)",
            WebViewPool.currentPoolSize <= 2
        )
    }

    @Test
    fun testWithWebViewScope() = runBlocking(Dispatchers.Main) {
        val result = WebViewPool.withWebView(context) { wv ->
            assertNotNull("Scoped WebView should be valid", wv)
            "success_result"
        }

        assertEquals("withWebView should return lambda result", "success_result", result)
        assertEquals("withWebView should auto-release instance back to pool", 1, WebViewPool.currentPoolSize)
    }

    @Test
    fun testConcurrentPermitLimiting() = runBlocking {
        val permitsAvailableBefore = WebViewPool.semaphore.availablePermits
        assertEquals("Initial semaphore permits should be 2", 2, permitsAvailableBefore)

        val jobs = (1..4).map { id ->
            async(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    WebViewPool.withWebView(context) { wv ->
                        assertNotNull(wv)
                        "result_$id"
                    }
                }
            }
        }

        val results = jobs.awaitAll()
        assertEquals(4, results.size)
        assertEquals("Available permits should return to 2 after concurrent executions", 2, WebViewPool.semaphore.availablePermits)
    }

    @Test
    fun testDestroyInstanceHandlesBadView() = runBlocking(Dispatchers.Main) {
        val wv = WebViewPool.acquireInstance(context)
        WebViewPool.destroyInstance(wv)

        assertEquals("Destroyed instance should not be in pool", 0, WebViewPool.currentPoolSize)
    }
}
