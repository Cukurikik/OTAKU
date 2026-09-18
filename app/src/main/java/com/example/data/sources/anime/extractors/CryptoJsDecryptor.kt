package com.example.data.sources.anime.extractors

import com.example.data.sources.Subtitle
import com.example.data.sources.VideoQuality
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class DecryptedStreamResult(
    val qualities: List<VideoQuality> = emptyList(),
    val subtitles: List<Subtitle> = emptyList(),
    val embedUrl: String = ""
)

object CryptoJsDecryptor {

    private fun decodeBase64(input: String): ByteArray {
        val cleaned = input.trim()
            .replace(" ", "+")
            .replace("\n", "")
            .replace("\r", "")
            .replace("\t", "")

        val decodedInput = if (cleaned.contains("%")) {
            try { URLDecoder.decode(cleaned, "UTF-8") } catch (e: Exception) { cleaned }
        } else {
            cleaned
        }

        return try {
            java.util.Base64.getDecoder().decode(decodedInput)
        } catch (e: Throwable) {
            android.util.Base64.decode(decodedInput, android.util.Base64.DEFAULT)
        }
    }

    /**
     * Simulates CryptoJS.AES.decrypt(ciphertext, passphrase)
     * Handles the "Salted__" prefix commonly used by CryptoJS.
     */
    fun decrypt(encryptedBase64: String, passphrase: String): String? {
        if (encryptedBase64.isBlank() || passphrase.isBlank()) return null
        return try {
            val encryptedBytes = decodeBase64(encryptedBase64)
            
            // Check for "Salted__" prefix
            if (encryptedBytes.size < 16 || 
                String(encryptedBytes.copyOfRange(0, 8), Charsets.UTF_8) != "Salted__") {
                return null
            }
            
            val salt = encryptedBytes.copyOfRange(8, 16)
            val ciphertext = encryptedBytes.copyOfRange(16, encryptedBytes.size)
            
            // Derive key and IV using EVP_BytesToKey (MD5), which is what CryptoJS uses by default
            val (key, iv) = evpBytesToKey(passphrase.toByteArray(Charsets.UTF_8), salt, 32, 16)
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            
            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decrypts ciphertext when salt is provided separately (not prefixed with "Salted__").
     */
    fun decryptWithSalt(encryptedBase64: String, passphrase: String, salt: ByteArray): String? {
        if (encryptedBase64.isBlank() || passphrase.isBlank()) return null
        return try {
            val ciphertext = decodeBase64(encryptedBase64)
            val (key, iv) = evpBytesToKey(passphrase.toByteArray(Charsets.UTF_8), salt, 32, 16)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decrypts ciphertext when raw AES key and IV are provided directly.
     */
    fun decryptWithKeyIv(encryptedBase64: String, key: ByteArray, iv: ByteArray): String? {
        if (encryptedBase64.isBlank()) return null
        return try {
            val ciphertext = decodeBase64(encryptedBase64)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun evpBytesToKey(password: ByteArray, salt: ByteArray, keyLen: Int, ivLen: Int): Pair<ByteArray, ByteArray> {
        val digest = MessageDigest.getInstance("MD5")
        var derivedBytes = ByteArray(0)
        var block = ByteArray(0)
        val resultLen = keyLen + ivLen
        
        while (derivedBytes.size < resultLen) {
            digest.reset()
            digest.update(block)
            digest.update(password)
            digest.update(salt)
            block = digest.digest()
            derivedBytes += block
        }
        
        val key = derivedBytes.copyOfRange(0, keyLen)
        val iv = derivedBytes.copyOfRange(keyLen, resultLen)
        
        return Pair(key, iv)
    }

    /**
     * Parses decrypted payload into real video qualities (M3U8 / MP4), subtitles, or embed URLs.
     */
    fun parseDecryptedPayload(decrypted: String, referer: String): DecryptedStreamResult {
        var text = decrypted.trim()
        if (text.startsWith("\"") && text.endsWith("\"") && text.length > 2) {
            text = text.substring(1, text.length - 1)
        }
        text = text.replace("\\/", "/").replace("\\\"", "\"").replace("\\u0026", "&")

        val qualities = mutableListOf<VideoQuality>()
        val subtitles = mutableListOf<Subtitle>()
        var embedUrl = ""

        // 1. Try JSON Object parsing
        if (text.startsWith("{") && text.endsWith("}")) {
            try {
                val json = JSONObject(text)
                
                // Check for sources array
                val sources = json.optJSONArray("sources") 
                    ?: json.optJSONArray("data") 
                    ?: json.optJSONArray("streams")
                
                if (sources != null) {
                    for (i in 0 until sources.length()) {
                        val item = sources.optJSONObject(i)
                        if (item != null) {
                            val file = item.optString("file").ifEmpty { item.optString("src") }.ifEmpty { item.optString("url") }
                            val label = item.optString("label").ifEmpty { item.optString("quality") }.ifEmpty { item.optString("res") }.ifEmpty { "Auto" }
                            if (file.isNotBlank() && (file.startsWith("http://") || file.startsWith("https://") || file.startsWith("//"))) {
                                val fixedUrl = if (file.startsWith("//")) "https:$file" else file
                                val isM3u8 = fixedUrl.contains(".m3u8") || item.optString("type").contains("hls", ignoreCase = true)
                                qualities.add(VideoQuality(label, fixedUrl, isM3u8, mapOf("Referer" to referer)))
                            }
                        }
                    }
                }

                // Check for single file / url in root object
                val singleFile = json.optString("file")
                    .ifEmpty { json.optString("src") }
                    .ifEmpty { json.optString("url") }
                    .ifEmpty { json.optString("stream") }
                    .ifEmpty { json.optString("video") }
                    .ifEmpty { json.optString("link") }

                if (singleFile.isNotBlank() && (singleFile.startsWith("http://") || singleFile.startsWith("https://") || singleFile.startsWith("//"))) {
                    val fixedUrl = if (singleFile.startsWith("//")) "https:$singleFile" else singleFile
                    val label = json.optString("label").ifEmpty { json.optString("quality") }.ifEmpty { "Auto" }
                    val isM3u8 = fixedUrl.contains(".m3u8") || json.optString("type").contains("hls", ignoreCase = true)
                    qualities.add(VideoQuality(label, fixedUrl, isM3u8, mapOf("Referer" to referer)))
                }

                val possibleEmbed = json.optString("embed").ifEmpty { json.optString("iframe") }
                if (possibleEmbed.isNotBlank()) {
                    embedUrl = possibleEmbed
                }

                // Check for subtitle tracks
                val tracks = json.optJSONArray("tracks") ?: json.optJSONArray("subtitles")
                if (tracks != null) {
                    for (i in 0 until tracks.length()) {
                        val track = tracks.optJSONObject(i) ?: continue
                        val file = track.optString("file").ifEmpty { track.optString("url") }
                        val label = track.optString("label").ifEmpty { track.optString("name") }.ifEmpty { "Subtitle" }
                        val kind = track.optString("kind")
                        if (file.isNotBlank() && (kind.isEmpty() || kind.contains("caption") || kind.contains("sub"))) {
                            val fixedUrl = if (file.startsWith("//")) "https:$file" else file
                            subtitles.add(Subtitle(language = label, url = fixedUrl, format = fixedUrl.substringAfterLast(".", "vtt")))
                        }
                    }
                }

                if (qualities.isNotEmpty() || embedUrl.isNotBlank()) {
                    return DecryptedStreamResult(qualities.distinctBy { it.url }, subtitles, embedUrl)
                }
            } catch (e: Exception) {
                // Ignore and fall through to next format
            }
        }

        // 2. Try JSON Array parsing
        if (text.startsWith("[") && text.endsWith("]")) {
            try {
                val array = JSONArray(text)
                for (i in 0 until array.length()) {
                    val item = array.opt(i)
                    if (item is JSONObject) {
                        val file = item.optString("file").ifEmpty { item.optString("src") }.ifEmpty { item.optString("url") }
                        val label = item.optString("label").ifEmpty { item.optString("quality") }.ifEmpty { "Auto" }
                        if (file.isNotBlank() && (file.startsWith("http://") || file.startsWith("https://") || file.startsWith("//"))) {
                            val fixedUrl = if (file.startsWith("//")) "https:$file" else file
                            val isM3u8 = fixedUrl.contains(".m3u8")
                            qualities.add(VideoQuality(label, fixedUrl, isM3u8, mapOf("Referer" to referer)))
                        }
                    } else if (item is String && (item.startsWith("http://") || item.startsWith("https://") || item.startsWith("//"))) {
                        val fixedUrl = if (item.startsWith("//")) "https:$item" else item
                        val isM3u8 = fixedUrl.contains(".m3u8")
                        qualities.add(VideoQuality("Auto", fixedUrl, isM3u8, mapOf("Referer" to referer)))
                    }
                }
                if (qualities.isNotEmpty()) {
                    return DecryptedStreamResult(qualities.distinctBy { it.url }, subtitles, embedUrl)
                }
            } catch (e: Exception) {
                // Ignore and fall through
            }
        }

        // 2.5. Fast Regex parsing for JSON-like sources and tracks
        if (qualities.isEmpty()) {
            val itemBlockRegex = Regex("""\{[^{}]*?["']?(?:file|src|url)["']?\s*:\s*["']([^"']+)["'][^{}]*?\}""")
            for (match in itemBlockRegex.findAll(text)) {
                val block = match.value
                val file = match.groupValues[1]
                if (file.startsWith("http://") || file.startsWith("https://") || file.startsWith("//")) {
                    val fixedUrl = if (file.startsWith("//")) "https:$file" else file
                    val labelMatch = Regex("""["']?(?:label|quality|res)["']?\s*:\s*["']([^"']+)["']""").find(block)
                    val label = labelMatch?.groupValues?.get(1) ?: "Auto"
                    val isM3u8 = fixedUrl.contains(".m3u8") || block.contains("hls", ignoreCase = true)
                    qualities.add(VideoQuality(label, fixedUrl, isM3u8, mapOf("Referer" to referer)))
                }
            }
        }
        if (subtitles.isEmpty()) {
            val subBlockRegex = Regex("""\{[^{}]*?["']?(?:file|url)["']?\s*:\s*["']([^"']+)["'][^{}]*?\}""")
            for (match in subBlockRegex.findAll(text)) {
                val block = match.value
                if (block.contains("caption", ignoreCase = true) || block.contains("sub", ignoreCase = true)) {
                    val file = match.groupValues[1]
                    if (file.startsWith("http://") || file.startsWith("https://") || file.startsWith("//")) {
                        val fixedUrl = if (file.startsWith("//")) "https:$file" else file
                        val labelMatch = Regex("""["']?(?:label|name|lang)["']?\s*:\s*["']([^"']+)["']""").find(block)
                        val label = labelMatch?.groupValues?.get(1) ?: "Subtitle"
                        subtitles.add(Subtitle(language = label, url = fixedUrl, format = fixedUrl.substringAfterLast(".", "vtt")))
                    }
                }
            }
        }
        if (qualities.isNotEmpty()) {
            return DecryptedStreamResult(qualities.distinctBy { it.url }, subtitles, embedUrl)
        }

        // 3. Check for HTML embed/iframe/source
        if (text.contains("<iframe") || text.contains("<source") || text.contains("<video")) {
            val srcMatch = Regex("""(?:src|file)\s*=\s*["']([^"']+)["']""").find(text)
            if (srcMatch != null) {
                val src = srcMatch.groupValues[1]
                val fixedUrl = if (src.startsWith("//")) "https:$src" else src
                if (fixedUrl.contains(".m3u8") || fixedUrl.contains(".mp4")) {
                    qualities.add(VideoQuality("Auto", fixedUrl, fixedUrl.contains(".m3u8"), mapOf("Referer" to referer)))
                } else if (fixedUrl.startsWith("http")) {
                    embedUrl = fixedUrl
                }
            }
            if (qualities.isNotEmpty() || embedUrl.isNotBlank()) {
                return DecryptedStreamResult(qualities.distinctBy { it.url }, subtitles, embedUrl)
            }
        }

        // 4. Plain direct URL
        if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("//")) {
            val fixedUrl = if (text.startsWith("//")) "https:$text" else text
            if (fixedUrl.contains(".m3u8") || fixedUrl.contains(".mp4") || fixedUrl.contains("stream")) {
                qualities.add(VideoQuality("Auto", fixedUrl, fixedUrl.contains(".m3u8"), mapOf("Referer" to referer)))
            } else {
                embedUrl = fixedUrl
            }
            return DecryptedStreamResult(qualities, subtitles, embedUrl)
        }

        // 5. Regex extraction for video stream URLs (.m3u8 / .mp4 / .webm)
        val streamRegex = Regex("""https?://[^"'\s<>\\]+?\.(?:m3u8|mp4|webm|mkv)[^"'\s<>\\]*""")
        for (match in streamRegex.findAll(text)) {
            val url = match.value
            qualities.add(VideoQuality("Auto", url, url.contains(".m3u8"), mapOf("Referer" to referer)))
        }

        // 6. Regex extraction for any http/https URL if still empty
        if (qualities.isEmpty()) {
            val anyUrlMatch = Regex("""https?://[^"'\s<>\\]+""").find(text)
            if (anyUrlMatch != null) {
                val foundUrl = anyUrlMatch.value
                if (foundUrl.contains(".m3u8") || foundUrl.contains(".mp4")) {
                    qualities.add(VideoQuality("Auto", foundUrl, foundUrl.contains(".m3u8"), mapOf("Referer" to referer)))
                } else {
                    embedUrl = foundUrl
                }
            }
        }

        return DecryptedStreamResult(qualities.distinctBy { it.url }, subtitles, embedUrl)
    }
}
