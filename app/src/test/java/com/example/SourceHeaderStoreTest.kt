package com.example.data.sources

import org.junit.Assert.*
import org.junit.Test
import okhttp3.Request

class SourceHeaderStoreTest {

    @Test
    fun testSourceHeadersDataClass() {
        val headers = SourceHeaders(
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            cookie = "cf_clearance=abc123xyz; session=test",
            referer = "https://v19.kuramanime.ing/"
        )

        assertFalse(headers.isEmpty())
        assertEquals("Mozilla/5.0 (Windows NT 10.0; Win64; x64)", headers.userAgent)
        assertEquals("cf_clearance=abc123xyz; session=test", headers.cookie)
        assertEquals("https://v19.kuramanime.ing/", headers.referer)

        val emptyHeaders = SourceHeaders()
        assertTrue(emptyHeaders.isEmpty())
    }

    @Test
    fun testRequestTagSourceId() {
        val req = Request.Builder()
            .url("https://v19.kuramanime.ing/anime/123")
            .tag(String::class.java, "kuramanime")
            .build()

        val sourceId = req.tag(String::class.java)
        assertEquals("kuramanime", sourceId)
    }
}
