package com.example.data.sources.anime.extractors

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

object ApiResponseParser {

    /**
     * Extracts stream URLs (.m3u8, .mp4, .webm, .mpd, /hls/, or decrypted payloads)
     * from any intercepted XHR/Fetch API response body or JS string.
     */
    fun extractStreamUrls(requestUrl: String, responseBody: String): List<String> {
        if (responseBody.isBlank()) return emptyList()
        val results = mutableSetOf<String>()

        var text = responseBody

        // 1. If payload contains packed JS, unpack it first
        if (text.contains("eval(function(p,a,c,k,e,", ignoreCase = true) ||
            text.contains("eval(function(p,a,c,k,e,r)", ignoreCase = true) ||
            text.contains("eval(function(p,a,c,k,e,d)", ignoreCase = true)
        ) {
            val unpacked = JsPackerUnpacker.unpack(text)
            if (!unpacked.isNullOrBlank()) {
                text += "\n" + unpacked
            }
        }

        // Clean escaped slashes and unicode escapes
        val cleaned = text
            .replace("\\/", "/")
            .replace("\\u002f", "/")
            .replace("\\u003a", ":")
            .replace("\\\"", "\"")

        // 2. Direct regex search for stream URLs
        val directStreamRegex = Regex(
            """https?://[^\s"'<>\\]+?\.(?:m3u8|mp4|webm|mpd)(?:[^\s"'<>\\]*)""",
            RegexOption.IGNORE_CASE
        )
        directStreamRegex.findAll(cleaned).forEach { match ->
            results.add(match.value)
        }

        // 3. Regex for HLS / streaming path patterns
        val hlsRegex = Regex(
            """https?://[^\s"'<>\\]+?/(?:hls|stream|video|embed|videoplayback|get_video|m3u8)/[^\s"'<>\\]*""",
            RegexOption.IGNORE_CASE
        )
        hlsRegex.findAll(cleaned).forEach { match ->
            results.add(match.value)
        }

        // 4. Base64 encoded URLs (aHR0c = "http", aHR0cH = "https")
        val b64Regex = Regex("""aHR0c[A-Za-z0-9+/=]+""")
        b64Regex.findAll(text).forEach { match ->
            try {
                val decoded = String(Base64.decode(match.value, Base64.DEFAULT)).replace("\\/", "/")
                if (decoded.startsWith("http")) {
                    val subMatches = directStreamRegex.findAll(decoded).map { it.value }.toList()
                    if (subMatches.isNotEmpty()) {
                        results.addAll(subMatches)
                    } else if (decoded.contains(".m3u8") || decoded.contains(".mp4") || decoded.contains("/hls/")) {
                        results.add(decoded)
                    }
                }
            } catch (_: Exception) {}
        }

        // 5. JSON parsing
        val trimmed = cleaned.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                val json = JSONTokener(trimmed).nextValue()
                parseJsonRecursively(json, results)
            } catch (_: Exception) {}
        }

        // 6. Common encrypted CryptoJS fields ("data", "payload", "encrypted") with common keys
        val cryptoKeys = listOf("kurama", "animasu", "nimegami", "otakudesu", "anichin", "kazefuri", "secret", "stream")
        if (trimmed.startsWith("{")) {
            try {
                val jsonObj = JSONObject(trimmed)
                val encData = jsonObj.optString("data", jsonObj.optString("payload", jsonObj.optString("encrypted", "")))
                if (encData.startsWith("Salted__") || encData.length > 20) {
                    for (passphrase in cryptoKeys) {
                        val dec = CryptoJsDecryptor.decrypt(encData, passphrase)
                        if (!dec.isNullOrBlank() && dec.contains("http")) {
                            directStreamRegex.findAll(dec).forEach { results.add(it.value) }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Filter out non-video assets (e.g., images, css, js)
        return results.filter { url ->
            val lower = url.lowercase()
            !lower.endsWith(".jpg") && !lower.endsWith(".jpeg") &&
            !lower.endsWith(".png") && !lower.endsWith(".webp") &&
            !lower.endsWith(".gif") && !lower.endsWith(".css") &&
            !lower.endsWith(".js") && !lower.endsWith(".vtt") &&
            !lower.endsWith(".srt")
        }
    }

    private fun parseJsonRecursively(element: Any, results: MutableSet<String>) {
        when (element) {
            is JSONObject -> {
                val keys = element.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = element.opt(key) ?: continue
                    if (value is String) {
                        checkAndAddString(value, results)
                    } else {
                        parseJsonRecursively(value, results)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until element.length()) {
                    val value = element.opt(i) ?: continue
                    if (value is String) {
                        checkAndAddString(value, results)
                    } else {
                        parseJsonRecursively(value, results)
                    }
                }
            }
        }
    }

    private fun checkAndAddString(value: String, results: MutableSet<String>) {
        val cleaned = value.replace("\\/", "/")
        if (cleaned.startsWith("http") && (
            cleaned.contains(".m3u8") || cleaned.contains(".mp4") ||
            cleaned.contains(".webm") || cleaned.contains(".mpd") ||
            cleaned.contains("/hls/") || cleaned.contains("videoplayback")
        )) {
            results.add(cleaned)
        } else if (cleaned.startsWith("aHR0c")) {
            try {
                val decoded = String(Base64.decode(cleaned, Base64.DEFAULT)).replace("\\/", "/")
                if (decoded.startsWith("http") && (decoded.contains(".m3u8") || decoded.contains(".mp4"))) {
                    results.add(decoded)
                }
            } catch (_: Exception) {}
        }
    }
}
