package com.example

import com.example.ui.screens.MalPkceUtil
import org.junit.Assert.*
import org.junit.Test

class MalOAuthPkceTest {

    @Test
    fun testCodeVerifierGeneration() {
        val verifier = MalPkceUtil.generateCodeVerifier(128)
        assertEquals(128, verifier.length)
        
        // Verify only URL safe unreserved characters: [A-Za-z0-9-._~]
        val validCharsRegex = "^[A-Za-z0-9\\-._~]+$".toRegex()
        assertTrue("Verifier should contain only valid PKCE characters", verifier.matches(validCharsRegex))
        
        val verifier2 = MalPkceUtil.generateCodeVerifier(128)
        assertNotEquals("Consecutive generated verifiers must be distinct", verifier, verifier2)
    }

    @Test
    fun testTokenResponseParsing() {
        val jsonResponse = """
            {
                "token_type": "Bearer",
                "expires_in": 2678400,
                "access_token": "mock_access_token_xyz_12345",
                "refresh_token": "mock_refresh_token_abc_67890"
            }
        """.trimIndent()

        val accessMatch = "\"access_token\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonResponse)
        val refreshMatch = "\"refresh_token\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(jsonResponse)
        val expiresMatch = "\"expires_in\"\\s*:\\s*(\\d+)".toRegex().find(jsonResponse)

        assertNotNull(accessMatch)
        assertNotNull(refreshMatch)
        assertNotNull(expiresMatch)

        assertEquals("mock_access_token_xyz_12345", accessMatch?.groupValues?.get(1))
        assertEquals("mock_refresh_token_abc_67890", refreshMatch?.groupValues?.get(1))
        assertEquals(2678400L, expiresMatch?.groupValues?.get(1)?.toLongOrNull())
    }
}
