package com.example.network

import com.example.data.NetworkModule
import com.example.network.interceptor.CloudflareBypassInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class CloudflareBypassInterceptorTest {

    private val testWorkerUrl = "https://anime-aggregator.njirlah.workers.dev"
    private val testToken = "test_token_12345"

    @Test
    fun testReconstructRequestPreservesHeadersAndInjectsWorkerCredentials() {
        val interceptor = CloudflareBypassInterceptor(testWorkerUrl, testToken)

        val original = Request.Builder()
            .url("https://v19.kuramanime.ing/ajax/episode/server?id=123")
            .header("Referer", "https://v19.kuramanime.ing/anime/100")
            .header("User-Agent", "Custom-Android-Agent/1.0")
            .header("Cookie", "session_token=abcxyz123; cf_clearance=valid")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json")
            .build()

        val reconstructed = interceptor.reconstructRequest(original)

        // 1. Worker URL check
        val decodedUrl = URLDecoder.decode(reconstructed.url.toString(), "UTF-8")
        assertTrue(reconstructed.url.toString().startsWith(testWorkerUrl))
        assertTrue(decodedUrl.contains("https://v19.kuramanime.ing/ajax/episode/server?id=123"))

        // 2. Header preservation check
        assertEquals("https://v19.kuramanime.ing/anime/100", reconstructed.header("Referer"))
        assertEquals("Custom-Android-Agent/1.0", reconstructed.header("User-Agent"))
        assertEquals("session_token=abcxyz123; cf_clearance=valid", reconstructed.header("Cookie"))
        assertEquals("XMLHttpRequest", reconstructed.header("X-Requested-With"))
        assertEquals("application/json", reconstructed.header("Accept"))

        // 3. Worker Auth injection check
        assertEquals("Bearer $testToken", reconstructed.header("Authorization"))
    }

    @Test
    fun testReconstructRequestClonesPostRequestBody() {
        val interceptor = CloudflareBypassInterceptor(testWorkerUrl, testToken)

        val payload = """{"action":"get_stream","token":"secret123"}"""
        val original = Request.Builder()
            .url("https://v2.animasu.work/api/stream")
            .header("Referer", "https://v2.animasu.work/")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val reconstructed = interceptor.reconstructRequest(original)

        assertEquals("POST", reconstructed.method)
        assertNotNull(reconstructed.body)

        val buffer = okio.Buffer()
        reconstructed.body!!.writeTo(buffer)
        val bodyContent = buffer.readUtf8()

        assertEquals(payload, bodyContent)
        assertEquals("application/json; charset=utf-8", reconstructed.body!!.contentType().toString().lowercase())
    }

    @Test
    fun testInterceptorReplaysOn403And503() {
        val interceptor = CloudflareBypassInterceptor(testWorkerUrl, testToken)
        val testTargetHost = "blocked-anime-test-domain.com"
        val targetUrl = "https://$testTargetHost/ajax/source"

        val recordedRequests = mutableListOf<Request>()

        // Mock OkHttpClient simulating 403 on target host and 200 on Worker proxy
        val testClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                val req = chain.request()
                recordedRequests.add(req)
                if (req.url.host == testTargetHost) {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(403)
                        .message("Forbidden - Cloudflare Turnstile")
                        .body("<html>Just a moment...</html>".toResponseBody("text/html".toMediaType()))
                        .build()
                } else if (req.url.host == "anime-aggregator.njirlah.workers.dev") {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"status":"success","stream":"https://cdn.example.com/play.m3u8"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody(null))
                        .build()
                }
            }
            .build()

        val request = Request.Builder()
            .url(targetUrl)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "https://$testTargetHost/watch/1")
            .build()

        val response = testClient.newCall(request).execute()

        // Verifikasi bahwa pemanggil mendapatkan response sukses dari Worker replay
        assertEquals(200, response.code)
        val bodyText = response.body?.string() ?: ""
        assertTrue(bodyText.contains("https://cdn.example.com/play.m3u8"))

        // Verifikasi bahwa ada 2 request yang tercatat: request pertama (gagal 403), request kedua (replay terkonstruksi ke Worker)
        assertEquals(2, recordedRequests.size)
        val replayRequest = recordedRequests[1]
        assertTrue(replayRequest.url.toString().startsWith(testWorkerUrl))
        assertEquals("XMLHttpRequest", replayRequest.header("X-Requested-With"))
        assertEquals("https://$testTargetHost/watch/1", replayRequest.header("Referer"))
        assertEquals("Bearer $testToken", replayRequest.header("Authorization"))

        // Verifikasi bahwa host didaftarkan ke blockedDomains registry
        assertTrue(NetworkModule.isHostBlocked(testTargetHost))
    }

    @Test
    fun testPreBlockedDomainRoutesDirectlyWithoutWaitingFor403() {
        val interceptor = CloudflareBypassInterceptor(testWorkerUrl, testToken)
        val preBlockedHost = "already-blocked-host.org"
        NetworkModule.markAsBlocked(preBlockedHost)

        val recordedRequests = mutableListOf<Request>()

        val testClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                val req = chain.request()
                recordedRequests.add(req)
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"bypassed":true}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val request = Request.Builder()
            .url("https://$preBlockedHost/api/token")
            .header("Referer", "https://$preBlockedHost/home")
            .build()

        val response = testClient.newCall(request).execute()

        assertEquals(200, response.code)
        // Karena host sudah terdaftar di blockedDomains, langsung 1 request ke Worker tanpa round-trip 403
        assertEquals(1, recordedRequests.size)
        assertTrue(recordedRequests[0].url.toString().startsWith(testWorkerUrl))
    }

    @Test
    fun testDepthHeaderIncrementationInReconstructRequest() {
        val interceptor = CloudflareBypassInterceptor(testWorkerUrl, testToken)

        // Depth 0 -> 1
        val req0 = Request.Builder().url("https://v19.kuramanime.ing/anime/1").build()
        val recon1 = interceptor.reconstructRequest(req0)
        assertEquals("1", recon1.header(CloudflareBypassInterceptor.HEADER_BYPASS_DEPTH))

        // Depth 1 -> 2
        val recon2 = interceptor.reconstructRequest(recon1)
        assertEquals("2", recon2.header(CloudflareBypassInterceptor.HEADER_BYPASS_DEPTH))

        // Depth 2 -> 3
        val recon3 = interceptor.reconstructRequest(recon2)
        assertEquals("3", recon3.header(CloudflareBypassInterceptor.HEADER_BYPASS_DEPTH))
    }

    @Test(expected = java.io.IOException::class)
    fun testRecursionLoopTerminationAtMaxDepth() {
        val interceptor = CloudflareBypassInterceptor(testWorkerUrl, testToken)

        val testClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        // Request with max depth (>= 3)
        val request = Request.Builder()
            .url("https://blocked-anime-site.org/stream")
            .header(CloudflareBypassInterceptor.HEADER_BYPASS_DEPTH, "3")
            .build()

        // Should throw IOException("Cloudflare bypass failed: maximum retry limit exceeded")
        testClient.newCall(request).execute()
    }

    @Test
    fun testWorkerError503TerminatesWithoutInfiniteLoop() {
        val interceptor = CloudflareBypassInterceptor(testWorkerUrl, testToken)
        var workerCallCount = 0

        val testClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.url.toString().startsWith(testWorkerUrl)) {
                    workerCallCount++
                    // Simulate Cloudflare Worker proxy experiencing 503 Service Unavailable / Rate Limit
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("Worker Service Unavailable")
                        .body("Worker limit reached".toResponseBody("text/plain".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody(null))
                        .build()
                }
            }
            .build()

        val request = Request.Builder()
            .url("$testWorkerUrl/?url=https%3A%2F%2Ftarget.com")
            .build()

        var caughtException: java.io.IOException? = null
        try {
            testClient.newCall(request).execute()
        } catch (e: java.io.IOException) {
            caughtException = e
        }

        assertNotNull("Must throw IOException when Worker returns 503 instead of looping forever", caughtException)
        assertTrue(caughtException!!.message!!.contains("maximum retry limit exceeded"))
        assertEquals("Worker must be called exactly once without recursive looping", 1, workerCallCount)
    }
}
