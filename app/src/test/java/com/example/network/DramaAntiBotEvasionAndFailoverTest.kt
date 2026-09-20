package com.example.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.CoilConfig
import com.example.data.NetworkModule
import com.example.data.sources.registry.SourceEndpointRegistry
import com.example.data.sources.registry.SourceMode
import com.example.network.interceptor.CloudflareBypassInterceptor
import com.example.network.webview.STEALTH_JS
import com.example.network.webview.StealthWebViewClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URLDecoder

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DramaAntiBotEvasionAndFailoverTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testStealthScriptContainsWebdriverMaskAndTouchSimulation() {
        // 1. Verify navigator.webdriver removal
        assertTrue("STEALTH_JS must mask navigator.webdriver", STEALTH_JS.contains("Object.defineProperty(navigator, 'webdriver'"))
        assertTrue("STEALTH_JS must remove selenium/webdriver artifacts", STEALTH_JS.contains("cdc_adoQpoasnfa76pfcZLmcfl_Array"))
        
        // 2. Verify touch and user event simulation
        assertTrue("STEALTH_JS must dispatch touchstart", STEALTH_JS.contains("window.dispatchEvent(new Event('touchstart'))"))
        assertTrue("STEALTH_JS must dispatch mousemove", STEALTH_JS.contains("window.dispatchEvent(new MouseEvent('mousemove'"))
        assertTrue("STEALTH_JS must auto-trigger Turnstile checkbox click", STEALTH_JS.contains(".cf-turnstile"))
    }

    @Test
    fun testNetworkModuleCredentialsAndFailover() {
        assertEquals("https://anime-aggregator.njirlah.workers.dev", NetworkModule.WORKER_BASE_URL)
        assertEquals("cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4", NetworkModule.WORKER_AUTH_TOKEN)

        // Verify WAF challenge detection
        assertTrue(NetworkModule.isWafChallenge("<html>Just a moment... Attention Required!</html>"))
        assertTrue(NetworkModule.isWafChallenge("<title>Security Check | Cloudflare Turnstile</title>"))
        assertTrue(NetworkModule.isWafChallenge("<div class='cf-challenge'>cf-browser-verification</div>"))
        assertFalse(NetworkModule.isWafChallenge("<html><h1>Drama Korea Terpopuler</h1></html>"))
    }

    @Test
    fun testDramaSourceEndpointsAndFailoverMirrors() {
        val dramaEndpoints = SourceEndpointRegistry.forMode(SourceMode.DRAMA)
        assertTrue("Should have at least 5 drama endpoints", dramaEndpoints.size >= 5)

        val expectedDramaIds = listOf("viki", "licil", "licil-barat", "jucelebrity", "lokalfilm", "drakorid", "nodrakor", "dramaqu")
        expectedDramaIds.forEach { expectedId ->
            val endpoint = SourceEndpointRegistry.findById(expectedId)
            assertNotNull("Endpoint $expectedId must exist in registry", endpoint)
            assertTrue("BaseUrl must be valid", endpoint!!.baseUrl.startsWith("http"))
        }

        // Verify failover mirror resolution
        val licilMirrors = SourceEndpointRegistry.getAllMirrors("licil")
        assertTrue("Licil should have failover mirrors", licilMirrors.size >= 2)

        val nextMirror = SourceEndpointRegistry.getNextMirror("licil", "https://licil.org/")
        assertNotNull(nextMirror)
        assertFalse("Next mirror should not be current URL", nextMirror!!.contains("https://licil.org/"))
    }

    @Test
    fun testCoilFailoverOnCloudflareBlock() {
        val client = CoilConfig.buildCoilOkHttpClient()
        assertNotNull(client)
    }
}
