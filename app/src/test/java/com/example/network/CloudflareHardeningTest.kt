package com.example.network

import com.example.data.NetworkModule
import com.example.network.interceptor.ChallengeStateManager
import com.example.network.webview.STEALTH_JS
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CloudflareHardeningTest {

    @Test
    fun testStealthJsContainsRequiredAntiBotMasking() {
        // 1. Mask navigator.webdriver
        assertTrue(STEALTH_JS.contains("webdriver"))
        assertTrue(STEALTH_JS.contains("Object.defineProperty(navigator, 'webdriver'"))

        // 2. Mock chrome runtime
        assertTrue(STEALTH_JS.contains("window.navigator.chrome"))

        // 3. Spoof WebGL Vendor (37445) & Renderer (37446)
        assertTrue(STEALTH_JS.contains("37445"))
        assertTrue(STEALTH_JS.contains("Intel Inc."))
        assertTrue(STEALTH_JS.contains("37446"))
        assertTrue(STEALTH_JS.contains("Intel Iris OpenGL Engine"))

        // 4. Canvas Fingerprint Micro-noise
        assertTrue(STEALTH_JS.contains("getImageData"))
        assertTrue(STEALTH_JS.contains("CanvasRenderingContext2D.prototype.getImageData"))

        // 5. Spoof Languages & Hardware Concurrency
        assertTrue(STEALTH_JS.contains("languages"))
        assertTrue(STEALTH_JS.contains("plugins"))
        assertTrue(STEALTH_JS.contains("hardwareConcurrency"))
    }

    @Test
    fun testChallengeStateManagerLifecycle() = runBlocking {
        val testUrl = "https://v19.kuramanime.ing/anime/naruto"
        val userAgent = "Mozilla/5.0 TestUA"

        val deferred1 = ChallengeStateManager.requestInteractiveSolver(testUrl, userAgent)
        assertNotNull(ChallengeStateManager.activeChallenge.value)
        assertEquals("v19.kuramanime.ing", ChallengeStateManager.activeChallenge.value?.host)
        assertEquals(userAgent, ChallengeStateManager.activeChallenge.value?.userAgent)

        // Test Deduplication for concurrent requests on the same host
        val deferred2 = ChallengeStateManager.requestInteractiveSolver(testUrl, userAgent)
        assertSame("Concurrent requests on the same host must share the same deferred solver", deferred1, deferred2)

        // Solve challenge
        val testCookies = "cf_clearance=test_token_12345; __cf_bm=bm_token_67890"
        ChallengeStateManager.onChallengeSolved(testCookies)

        // Should resolve deferred and clear active challenge
        assertTrue(deferred1.isCompleted)
        assertEquals(testCookies, deferred1.await())
        assertNull(ChallengeStateManager.activeChallenge.value)
    }

    @Test
    fun testChallengeStateManagerCancellation() = runBlocking {
        val testUrl = "https://animasu.cc/test"
        val userAgent = "Mozilla/5.0 TestUA"

        val deferred = ChallengeStateManager.requestInteractiveSolver(testUrl, userAgent)
        assertNotNull(ChallengeStateManager.activeChallenge.value)

        // Cancel challenge
        ChallengeStateManager.onChallengeCancelled()

        assertNull(ChallengeStateManager.activeChallenge.value)
        assertTrue(deferred.isCompleted)

        try {
            deferred.await()
            fail("Deferred should have thrown IOException on cancel")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("dibatalkan") == true)
        }
    }

    @Test
    fun testWafChallengeDetectionKeywords() {
        assertTrue(NetworkModule.isWafChallenge("Just a moment..."))
        assertTrue(NetworkModule.isWafChallenge("<title>Attention Required! | Cloudflare</title>"))
        assertTrue(NetworkModule.isWafChallenge("Please complete the security check to access"))
        assertTrue(NetworkModule.isWafChallenge("Cloudflare Ray ID: 89347891237"))
        assertTrue(NetworkModule.isWafChallenge("cf-challenge-running"))
        assertTrue(NetworkModule.isWafChallenge("turnstile-wrapper"))

        // Normal HTML content should not trigger WAF challenge
        assertFalse(NetworkModule.isWafChallenge("<html><head><title>Nonton Anime Sub Indo Gratis</title></head><body>Content</body></html>"))
    }
}
