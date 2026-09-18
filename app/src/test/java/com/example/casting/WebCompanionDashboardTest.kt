package com.example.casting

import com.example.network.server.LocalMediaServer
import fi.iki.elonen.NanoHTTPD
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class WebCompanionDashboardTest {

    private class MockSession(
        private val mockUri: String,
        private val mockMethod: NanoHTTPD.Method = NanoHTTPD.Method.GET,
        private val mockHeaders: Map<String, String> = emptyMap(),
        private val mockParams: Map<String, List<String>> = emptyMap()
    ) : NanoHTTPD.IHTTPSession {
        override fun execute() {}
        override fun getCookies(): NanoHTTPD.CookieHandler? = null
        override fun getHeaders(): Map<String, String> = mockHeaders
        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun getMethod(): NanoHTTPD.Method = mockMethod
        override fun getParms(): Map<String, String> = mockParams.mapValues { it.value.firstOrNull() ?: "" }
        override fun getParameters(): Map<String, List<String>> = mockParams
        override fun getQueryParameterString(): String = ""
        override fun getUri(): String = mockUri
        override fun parseBody(files: MutableMap<String, String>?) {}
        override fun getRemoteIpAddress(): String = "127.0.0.1"
        override fun getRemoteHostName(): String = "localhost"
    }

    @Test
    fun testWebCompanionDashboardHtmlServing() {
        val server = LocalMediaServer(8080)
        val session = MockSession(mockUri = "/")
        val response = server.serve(session)

        assertNotNull(response)
        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertTrue(response.mimeType.contains("text/html"))
        assertNotNull(response.getHeader("access-control-allow-origin"))
    }

    @Test
    fun testWebCompanionStatusApi() {
        val server = LocalMediaServer(8080)
        val session = MockSession(mockUri = "/api/status")
        val response = server.serve(session)

        assertNotNull(response)
        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertTrue(response.mimeType.contains("application/json"))
        assertNotNull(response.getHeader("access-control-allow-origin"))
    }

    @Test
    fun testCorsPreflightOptionsMethod() {
        val server = LocalMediaServer(8080)
        val session = MockSession(
            mockUri = "/api/library",
            mockMethod = NanoHTTPD.Method.OPTIONS
        )
        val response = server.serve(session)

        assertNotNull(response)
        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals("*", response.getHeader("access-control-allow-origin"))
        assertNotNull(response.getHeader("access-control-allow-methods"))
    }

    @Test
    fun testNotFoundEndpointHandling() {
        val server = LocalMediaServer(8080)
        val session = MockSession(mockUri = "/api/unknown_endpoint_404")
        val response = server.serve(session)

        assertNotNull(response)
        assertEquals(NanoHTTPD.Response.Status.NOT_FOUND, response.status)
    }
}
