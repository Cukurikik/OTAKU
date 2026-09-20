package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.sources.SourceBaseUrlStore
import com.example.data.sources.registry.HealthResult
import com.example.data.sources.registry.SourceEndpointRegistry
import com.example.data.sources.registry.SourceHealthProbe
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DynamicDomainRotatorTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        SourceBaseUrlStore.clear(context, "kuramanime")
        SourceBaseUrlStore.clear(context, "anichin")
        SourceBaseUrlStore.clear(context, "licil")
        SourceBaseUrlStore.clear(context, "komikcast")
    }

    @Test
    fun testSourceEndpointRegistryLookupAndMirrors() {
        // 1. Kuramanime mirrors
        val kuramanime = SourceEndpointRegistry.findById("kuramanime")
        assertNotNull("Kuramanime endpoint should exist", kuramanime)
        val kuramaMirrors = SourceEndpointRegistry.getAllMirrors("kuramanime")
        assertTrue("Kuramanime should have multiple mirrors", kuramaMirrors.size >= 3)
        assertTrue("Should contain primary", kuramaMirrors.any { it.contains("v19.kuramanime.ing") })
        assertTrue("Should contain blog mirror", kuramaMirrors.any { it.contains("kuramanime.blog") })
        assertTrue("Should contain work mirror", kuramaMirrors.any { it.contains("kuramanime.work") })

        val nextKurama = SourceEndpointRegistry.getNextMirror("kuramanime", "https://v19.kuramanime.ing/")
        assertNotNull(nextKurama)
        assertFalse("Next mirror must not be the failing one", nextKurama!!.contains("v19.kuramanime.ing"))

        // 2. Anichin mirrors
        val anichin = SourceEndpointRegistry.findById("anichin")
        assertNotNull("Anichin endpoint should exist", anichin)
        val anichinMirrors = SourceEndpointRegistry.getAllMirrors("anichin")
        assertTrue("Anichin should have mirrors", anichinMirrors.size >= 2)
        assertTrue("Should contain watch/cafe/com.co", anichinMirrors.any { it.contains("anichin.cafe") || it.contains("anichin.com.co") })

        // 3. Licil drama mirrors
        val licil = SourceEndpointRegistry.findById("licil")
        assertNotNull("Licil endpoint should exist", licil)
        val licilMirrors = SourceEndpointRegistry.getAllMirrors("licil")
        assertTrue("Licil should have mirrors", licilMirrors.size >= 2)

        // 4. Komikcast comic mirrors
        val komikcast = SourceEndpointRegistry.findById("komikcast")
        assertNotNull("Komikcast endpoint should exist", komikcast)
        val komikcastMirrors = SourceEndpointRegistry.getAllMirrors("komikcast")
        assertTrue("Komikcast should have mirrors", komikcastMirrors.size >= 2)
    }

    @Test
    fun testRewriteUrlPreservesPathQueryAndFragment() {
        val original = "https://v19.kuramanime.ing/anime/one-piece/episode-1000?filter=hd#player"
        val newBase = "https://v9.kuramanime.blog/"

        val rewritten = SourceHealthProbe.rewriteUrl(original, newBase)
        assertEquals(
            "https://v9.kuramanime.blog/anime/one-piece/episode-1000?filter=hd#player",
            rewritten
        )

        val originalWithoutSlash = "https://licil.org/drama/vincenzo"
        val newBaseWithSlash = "https://digitalepiphanies.org/"
        val rewrittenDrama = SourceHealthProbe.rewriteUrl(originalWithoutSlash, newBaseWithSlash)
        assertEquals(
            "https://digitalepiphanies.org/drama/vincenzo",
            rewrittenDrama
        )
    }

    @Test
    fun testSourceBaseUrlStorePersistence() {
        val defaultUrl = "https://v19.kuramanime.ing/"
        val initial = SourceBaseUrlStore.get(context, "kuramanime", defaultUrl)
        assertEquals(defaultUrl.trimEnd('/'), initial)

        // Save override
        val mirrorUrl = "https://v9.kuramanime.blog"
        SourceBaseUrlStore.save(context, "kuramanime", mirrorUrl)

        val retrieved = SourceBaseUrlStore.get(context, "kuramanime", defaultUrl)
        assertEquals(mirrorUrl, retrieved)

        // Clear
        SourceBaseUrlStore.clear(context, "kuramanime")
        val afterClear = SourceBaseUrlStore.get(context, "kuramanime", defaultUrl)
        assertEquals(defaultUrl.trimEnd('/'), afterClear)
    }

    @Test
    fun testHealthProbeEvaluation() = runBlocking {
        // Use client with interceptor to simulate server responses without MockWebServer dependency
        val testClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val path = request.url.encodedPath
                val code = when {
                    path.contains("status200") -> 200
                    path.contains("status403") -> 403
                    path.contains("status500") -> 500
                    else -> 404
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("Test response")
                    .body("test".toResponseBody("text/plain".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val probe = SourceHealthProbe(client = testClient)

        val result1 = probe.probeUrl("https://example.com/status200")
        assertTrue("200 OK should be healthy", probe.isHealthy(result1))

        val result2 = probe.probeUrl("https://example.com/status403")
        assertTrue("403 CF challenge should be reachable", probe.isHealthy(result2))

        val result3 = probe.probeUrl("https://example.com/status500")
        assertFalse("500 Server Error should not be healthy", probe.isHealthy(result3))
        assertTrue("Should be HTTP 500", result3 is HealthResult.HTTP && result3.code == 500)
    }

    @Test
    fun testProbeAndFailover() = runBlocking {
        // Simulate:
        // v19.kuramanime.ing -> 500 (Dead)
        // v9.kuramanime.blog -> 200 (Alive)
        val testClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val host = chain.request().url.host
                val code = if (host.contains("v19.kuramanime.ing")) 500 else 200
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("OK")
                    .body("healthy".toResponseBody("text/plain".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val probe = SourceHealthProbe(client = testClient)

        val healthyMirror = probe.probeAndFailover(
            context = context,
            sourceId = "kuramanime",
            failingUrlOrBase = "https://v19.kuramanime.ing/"
        )

        assertNotNull("Should find a healthy mirror", healthyMirror)
        assertFalse("Healthy mirror should not be the dead domain", healthyMirror!!.contains("v19.kuramanime.ing"))

        // Verify it was persisted to SourceBaseUrlStore
        val activeUrl = SourceBaseUrlStore.get(context, "kuramanime", "https://v19.kuramanime.ing/")
        assertEquals("Store should persist the newly discovered healthy mirror", healthyMirror.trimEnd('/'), activeUrl)
    }

    @Test
    fun testDeadIpFastFailoverAndBypass() = runBlocking {
        val deadIp1 = "http://178.128.91.191/"
        val deadIp2 = "http://134.209.105.11/"

        // 1. Static checks
        assertTrue("Dead IP 178 should be flagged as known dead", SourceHealthProbe.isKnownDeadUrl(deadIp1))
        assertTrue("Dead IP 134 should be flagged as known dead", SourceHealthProbe.isKnownDeadUrl(deadIp2))
        assertFalse("Dead IP 178 should not be healthy", SourceHealthProbe.isHealthy(deadIp1))
        assertFalse("Dead IP 134 should not be healthy", SourceHealthProbe.isHealthy(deadIp2))

        // 2. Fast failover probe check (should return immediately without TCP connection attempt)
        val probe = SourceHealthProbe()
        val result1 = probe.probeUrl(deadIp1)
        assertTrue("Should return DeadEndpoint error instantly", result1 is HealthResult.ERROR && result1.type == "DeadEndpoint")

        val result2 = probe.probeUrl(deadIp2)
        assertTrue("Should return DeadEndpoint error instantly", result2 is HealthResult.ERROR && result2.type == "DeadEndpoint")
    }

    @Test
    fun testActiveHttpsDramaReplacementsAndLegacyAliases() {
        // 1. DrakorID (replacement for 178 / dramaindo)
        val drakorid = SourceEndpointRegistry.findById("drakorid")
        assertNotNull("DrakorID endpoint should exist", drakorid)
        assertEquals("https://drakorid.net/", drakorid!!.baseUrl)
        assertTrue(drakorid.mirrors.isNotEmpty())

        // Legacy lookup aliases
        val legacy178 = SourceEndpointRegistry.findById("drama-ip-178")
        assertNotNull("Legacy 178 lookup should alias to drakorid", legacy178)
        assertEquals("drakorid", legacy178!!.id)

        val legacyDramaindo = SourceEndpointRegistry.findById("dramaindo")
        assertNotNull("Legacy dramaindo lookup should alias to drakorid", legacyDramaindo)
        assertEquals("drakorid", legacyDramaindo!!.id)

        // 2. NoDrakor (replacement for 134 / drakor)
        val nodrakor = SourceEndpointRegistry.findById("nodrakor")
        assertNotNull("NoDrakor endpoint should exist", nodrakor)
        assertEquals("https://nodrakor.icu/", nodrakor!!.baseUrl)
        assertTrue(nodrakor.mirrors.isNotEmpty())

        val legacy134 = SourceEndpointRegistry.findById("drama-ip-134")
        assertNotNull("Legacy 134 lookup should alias to nodrakor", legacy134)
        assertEquals("nodrakor", legacy134!!.id)

        val legacyDrakor = SourceEndpointRegistry.findById("drakor")
        assertNotNull("Legacy drakor lookup should alias to nodrakor", legacyDrakor)
        assertEquals("nodrakor", legacyDrakor!!.id)

        // 3. DramaQu
        val dramaqu = SourceEndpointRegistry.findById("dramaqu")
        assertNotNull("DramaQu endpoint should exist", dramaqu)
        assertEquals("https://dramaqu.is/", dramaqu!!.baseUrl)

        // 4. Available endpoints filter check
        val dramaEndpoints = SourceEndpointRegistry.getAvailableEndpoints(com.example.data.sources.registry.SourceMode.DRAMA)
        assertTrue("Should have available drama endpoints", dramaEndpoints.isNotEmpty())
        assertFalse("No dead raw IPs should be in available endpoints", dramaEndpoints.any { SourceEndpointRegistry.isRawDeadIp(it.baseUrl) })
        assertTrue("Available endpoints should include drakorid", dramaEndpoints.any { it.id == "drakorid" })
        assertTrue("Available endpoints should include nodrakor", dramaEndpoints.any { it.id == "nodrakor" })
        assertTrue("Available endpoints should include dramaqu", dramaEndpoints.any { it.id == "dramaqu" })
    }
}
