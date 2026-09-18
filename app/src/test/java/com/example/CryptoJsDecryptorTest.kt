package com.example

import com.example.data.sources.anime.extractors.CryptoJsDecryptor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CryptoJsDecryptorTest {

    // Helper to generate CryptoJS-compatible AES ciphertext
    private fun encryptCryptoJs(plaintext: String, passphrase: String): String {
        val salt = ByteArray(8)
        SecureRandom().nextBytes(salt)

        // EVP_BytesToKey
        val md = MessageDigest.getInstance("MD5")
        var derived = ByteArray(0)
        var block = ByteArray(0)
        val passBytes = passphrase.toByteArray(Charsets.UTF_8)
        while (derived.size < 48) {
            md.reset()
            md.update(block)
            md.update(passBytes)
            md.update(salt)
            block = md.digest()
            derived += block
        }
        val key = derived.copyOfRange(0, 32)
        val iv = derived.copyOfRange(32, 48)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val fullPayload = "Salted__".toByteArray(Charsets.UTF_8) + salt + ciphertext
        return Base64.getEncoder().encodeToString(fullPayload)
    }

    @Test
    fun testDecryptCryptoJsStandard() {
        val secret = "kuramanime_secret_key_2024"
        val payload = "https://stream.example.com/master.m3u8"
        val encrypted = encryptCryptoJs(payload, secret)

        assertTrue("Encrypted string must start with U2FsdGVkX1", encrypted.startsWith("U2FsdGVkX1"))

        // Decrypt using our CryptoJsDecryptor
        // Note: in unit tests without android.jar, android.util.Base64 needs java.util.Base64 fallback or Robolectric
        // Let's test decrypt
        val decrypted = CryptoJsDecryptor.decrypt(encrypted, secret)
        assertEquals(payload, decrypted)
    }

    @Test
    fun testParseDecryptedJsonObjectWithSources() {
        val json = """
            {
                "status": "success",
                "sources": [
                    {"file": "https://cdn.example.com/video_720p.mp4", "label": "720p", "type": "mp4"},
                    {"file": "https://cdn.example.com/video_1080p.m3u8", "label": "1080p", "type": "hls"}
                ],
                "tracks": [
                    {"file": "https://cdn.example.com/sub_id.vtt", "label": "Indonesian", "kind": "captions"}
                ]
            }
        """.trimIndent()

        val result = CryptoJsDecryptor.parseDecryptedPayload(json, "https://anime.example.com/")
        assertEquals(2, result.qualities.size)
        assertEquals("720p", result.qualities[0].qualityLabel)
        assertEquals("https://cdn.example.com/video_720p.mp4", result.qualities[0].url)
        assertFalse(result.qualities[0].isM3U8)

        assertEquals("1080p", result.qualities[1].qualityLabel)
        assertEquals("https://cdn.example.com/video_1080p.m3u8", result.qualities[1].url)
        assertTrue(result.qualities[1].isM3U8)

        assertEquals(1, result.subtitles.size)
        assertEquals("Indonesian", result.subtitles[0].language)
        assertEquals("https://cdn.example.com/sub_id.vtt", result.subtitles[0].url)
    }

    @Test
    fun testParseDecryptedSingleFileAndEscapedUrls() {
        val raw = """{"file":"https:\/\/storage.server.net\/stream.m3u8","label":"HD"}"""
        val result = CryptoJsDecryptor.parseDecryptedPayload(raw, "https://anime.example.com/")
        assertEquals(1, result.qualities.size)
        assertEquals("https://storage.server.net/stream.m3u8", result.qualities[0].url)
        assertTrue(result.qualities[0].isM3U8)
    }

    @Test
    fun testParseDecryptedDirectUrl() {
        val url = "https://cdn.animestream.org/episodes/ep10.mp4?token=xyz123"
        val result = CryptoJsDecryptor.parseDecryptedPayload(url, "https://animasuid.com/")
        assertEquals(1, result.qualities.size)
        assertEquals(url, result.qualities[0].url)
        assertFalse(result.qualities[0].isM3U8)
    }

    @Test
    fun testFullEncryptionAndExtractionCycle() {
        val passphrase = "animasu_passkey_test"
        val jsonPayload = """{"sources":[{"file":"https://faststream.me/hls/anime1.m3u8","label":"Auto"}]}"""
        val encrypted = encryptCryptoJs(jsonPayload, passphrase)

        val decrypted = CryptoJsDecryptor.decrypt(encrypted, passphrase)
        assertNotNull(decrypted)
        assertEquals(jsonPayload, decrypted)

        val streamResult = CryptoJsDecryptor.parseDecryptedPayload(decrypted!!, "https://v2.animasu.work/")
        assertEquals(1, streamResult.qualities.size)
        assertEquals("https://faststream.me/hls/anime1.m3u8", streamResult.qualities[0].url)
        assertTrue(streamResult.qualities[0].isM3U8)
    }
}
