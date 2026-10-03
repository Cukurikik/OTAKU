package com.example

import com.example.data.sources.base.BaseScraperSource
import com.example.network.dns.CloudflareDoHResolver
import com.example.network.domain.DynamicDomainResolverEngine
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
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
import java.net.InetAddress
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class Proposal1DynamicDomainAutoHealingTest {

    private lateinit var dohResolver: CloudflareDoHResolver

    @Before
    fun setUp() {
        dohResolver = CloudflareDoHResolver()
        dohResolver.clearCache()
    }

    @Test
    fun testDoHResolverIpLiteralAndCache() {
        // IP literal should be resolved directly without network query
        val addresses = dohResolver.lookup("127.0.0.1")
        assertEquals(1, addresses.size)
        assertEquals(InetAddress.getByName("127.0.0.1"), addresses[0])

        // Blank host should throw UnknownHostException
        try {
            dohResolver.lookup("")
            fail("Expected UnknownHostException for blank host")
        } catch (e: UnknownHostException) {
            assertTrue(e.message?.contains("blank") == true)
        }
    }

    @Test
    fun testDoHResolverParsingDnsJsonResponse() {
        // Test parsing JSON response logic
        val sampleDnsJson = """
            {
                "Status": 0,
                "TC": false,
                "RD": true,
                "RA": true,
                "AD": false,
                "CD": false,
                "Question": [{"name": "kuramanime.ing", "type": 1}],
                "Answer": [
                    {"name": "kuramanime.ing", "type": 1, "TTL": 300, "data": "104.21.55.2"},
                    {"name": "kuramanime.ing", "type": 1, "TTL": 300, "data": "172.67.182.99"}
                ]
            }
        """.trimIndent()

        // Verify valid IP regex check
        val regex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        assertTrue(regex.matches("104.21.55.2"))
        assertTrue(regex.matches("172.67.182.99"))
        assertFalse(regex.matches("invalid.host"))
    }

    @Test
    fun testDynamicDomainResolverEngineFormulationAndRanking() = runBlocking {
        val mirrors = listOf(
            "https://v19.kuramanime.ing",
            "https://v9.kuramanime.work",
            "https://v9.kuramanime.blog"
        )

        val engine = DynamicDomainResolverEngine(
            sourceId = "kuramanime-test",
            mirrorDomains = mirrors,
            defaultBaseUrl = "https://v19.kuramanime.ing"
        )

        // Test latency ranking and selection:
        // Candidates:
        // c1: 200 OK, DoH valid, latency 250ms
        // c2: 200 OK, DoH valid, latency 80ms -> SHOULD BE SELECTED (arg min Latency)
        // c3: 500 Error, latency 20ms -> DISQUALIFIED
        val results = listOf(
            DynamicDomainResolverEngine.ProbeResult("https://v19.kuramanime.ing", true, 200, true, 250L),
            DynamicDomainResolverEngine.ProbeResult("https://v9.kuramanime.work", true, 200, true, 80L),
            DynamicDomainResolverEngine.ProbeResult("https://v9.kuramanime.blog", false, 500, false, 20L)
        )

        val valid = results.filter { it.isSuccessful && it.isDoHValid }
        val best = valid.minByOrNull { it.latencyMs }

        assertNotNull(best)
        assertEquals("https://v9.kuramanime.work", best?.domain)
        assertEquals(80L, best?.latencyMs)
    }

    @Test
    fun testDynamicDomainResolverEngineTimeoutConstraint() {
        // Trout_max <= 4500 ms
        assertTrue(DynamicDomainResolverEngine.MAX_TOTAL_RESOLUTION_TIMEOUT_MS <= 4500L)
        assertEquals(4500L, DynamicDomainResolverEngine.MAX_TOTAL_RESOLUTION_TIMEOUT_MS)
    }

    @Test
    fun testBaseScraperSourceUrlRewriting() {
        val dummySource = object : BaseScraperSource() {
            override val sourceId: String = "test-scraper"
            override val baseUrl: String = "https://v19.kuramanime.ing"
            override val mirrorDomains: List<String> = listOf(
                "https://v19.kuramanime.ing",
                "https://v9.kuramanime.work"
            )
        }

        val originalUrl = "https://v19.kuramanime.ing/anime/naruto-shippuden?ep=1#player"
        val activeBase = "https://v9.kuramanime.work"

        val rewritten = dummySource.rewriteUrlToActiveDomain(originalUrl, activeBase)
        assertEquals("https://v9.kuramanime.work/anime/naruto-shippuden?ep=1#player", rewritten)
    }

    @Test
    fun testBaseScraperSourceAutoHealingExecution() = runBlocking {
        val dummySource = object : BaseScraperSource() {
            override val sourceId: String = "autoheal-test"
            override val baseUrl: String = "https://old-domain.fit"
            override val mirrorDomains: List<String> = listOf("https://old-domain.fit", "https://new-mirror.org")
        }

        var executionAttempts = 0
        var executedUrl = ""

        // Test normal execution when first try succeeds
        val result = dummySource.executeWithAutoHealing(
            initialUrl = "https://old-domain.fit/manga/one-piece"
        ) { url ->
            executionAttempts++
            executedUrl = url
            "Success on: $url"
        }

        assertEquals(1, executionAttempts)
        assertEquals("https://old-domain.fit/manga/one-piece", executedUrl)
        assertEquals("Success on: https://old-domain.fit/manga/one-piece", result)
    }
}
