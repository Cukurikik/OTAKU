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

    /**
     * Extracts external subtitle tracks (.vtt, .srt, WebVTT) from API responses,
     * JWPlayer configurations, HTML5 track definitions, or JSON payloads.
     */
    fun extractSubtitleTracks(requestUrl: String, responseBody: String): List<com.example.data.sources.Subtitle> {
        if (responseBody.isBlank()) return emptyList()
        val subtitles = mutableListOf<com.example.data.sources.Subtitle>()
        val seenUrls = mutableSetOf<String>()

        var text = responseBody

        // Unpack packed JS if present
        if (text.contains("eval(function(p,a,c,k,e,", ignoreCase = true) ||
            text.contains("eval(function(p,a,c,k,e,r)", ignoreCase = true)
        ) {
            val unpacked = JsPackerUnpacker.unpack(text)
            if (!unpacked.isNullOrBlank()) {
                text += "\n" + unpacked
            }
        }

        val cleaned = text
            .replace("\\/", "/")
            .replace("\\u002f", "/")
            .replace("\\u003a", ":")
            .replace("\\\"", "\"")

        fun addSubtitle(url: String, rawLabel: String? = null, rawKind: String? = null) {
            val cleanUrl = url.replace("\\/", "/").trim().removeSurrounding("\"").removeSurrounding("'")
            if (cleanUrl.isNotBlank() && (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://"))) {
                val format = when {
                    cleanUrl.contains(".srt", ignoreCase = true) -> "srt"
                    cleanUrl.contains(".ass", ignoreCase = true) -> "ass"
                    else -> "vtt"
                }
                val label = when {
                    !rawLabel.isNullOrBlank() -> rawLabel
                    cleanUrl.contains("indonesia", ignoreCase = true) || cleanUrl.contains("indo", ignoreCase = true) || cleanUrl.contains("/id.", ignoreCase = true) -> "Indonesian"
                    cleanUrl.contains("english", ignoreCase = true) || cleanUrl.contains("eng", ignoreCase = true) || cleanUrl.contains("/en.", ignoreCase = true) -> "English"
                    else -> "Indonesian"
                }

                if (seenUrls.add(cleanUrl)) {
                    subtitles.add(com.example.data.sources.Subtitle(language = label, url = cleanUrl, format = format))
                } else if (!rawLabel.isNullOrBlank()) {
                    val idx = subtitles.indexOfFirst { it.url == cleanUrl }
                    if (idx != -1) {
                        subtitles[idx] = subtitles[idx].copy(language = rawLabel)
                    }
                }
            }
        }

        // 1. JSON Object recursive check for tracks / subtitles arrays first
        val trimmed = cleaned.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                val json = JSONTokener(trimmed).nextValue()
                parseSubtitlesJsonRecursively(json, ::addSubtitle)
            } catch (_: Exception) {}
        }

        // 2. Regex for JWPlayer / Plyr tracks: [ { file: "...", label: "...", kind: "captions" } ]
        val tracksRegex = Regex("""tracks\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        tracksRegex.findAll(cleaned).forEach { m ->
            val block = m.groupValues[1]
            val itemRegex = Regex("""\{([^}]*)\}""")
            itemRegex.findAll(block).forEach { itemMatch ->
                val itemContent = itemMatch.groupValues[1]
                val file = Regex("""(?:file|src|url)\s*:\s*["']([^"']+)["']""").find(itemContent)?.groupValues?.get(1)
                val label = Regex("""(?:label|name|language|lang|srclang)\s*:\s*["']([^"']+)["']""").find(itemContent)?.groupValues?.get(1)
                val kind = Regex("""kind\s*:\s*["']([^"']+)["']""").find(itemContent)?.groupValues?.get(1)
                if (file != null) {
                    addSubtitle(file, label, kind)
                }
            }
        }

        // 3. HTML <track> elements: <track src="..." label="..." kind="subtitles|captions" srclang="...">
        val trackTagRegex = Regex("""<track\s+([^>]*?)/*>""", RegexOption.IGNORE_CASE)
        trackTagRegex.findAll(cleaned).forEach { match ->
            val attrs = match.groupValues[1]
            val src = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)
            val label = Regex("""label\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)
                ?: Regex("""srclang\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)
            val kind = Regex("""kind\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)
            if (!src.isNullOrBlank()) {
                val fullUrl = if (src.startsWith("http://") || src.startsWith("https://")) {
                    src
                } else if (src.startsWith("//")) {
                    "https:$src"
                } else if (requestUrl.isNotBlank() && src.startsWith("/")) {
                    val base = requestUrl.substringBefore("://") + "://" + requestUrl.substringAfter("://").substringBefore("/")
                    "$base$src"
                } else {
                    src
                }
                addSubtitle(fullUrl, label, kind)
            }
        }

        // 4. Fallback direct regex search for WebVTT, SRT, ASS, and SSA URLs
        val subtitleUrlRegex = Regex(
            """https?://[^\s"'<>\\]+?\.(?:vtt|srt|ass|ssa)(?:[^\s"'<>\\]*)""",
            RegexOption.IGNORE_CASE
        )
        subtitleUrlRegex.findAll(cleaned).forEach { match ->
            addSubtitle(match.value)
        }

        return subtitles
    }

    /**
     * Alias for extractSubtitleTracks for backwards compatibility and generic naming.
     */
    fun extractSubtitleUrls(requestUrl: String, responseBody: String): List<com.example.data.sources.Subtitle> {
        return extractSubtitleTracks(requestUrl, responseBody)
    }

    private fun parseSubtitlesJsonRecursively(element: Any, onSubtitle: (url: String, label: String?, kind: String?) -> Unit) {
        when (element) {
            is JSONObject -> {
                val keys = element.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = element.opt(key) ?: continue
                    if ((key.equals("subtitles", ignoreCase = true) ||
                         key.equals("tracks", ignoreCase = true) ||
                         key.equals("captions", ignoreCase = true)) && value is JSONArray) {
                        for (i in 0 until value.length()) {
                            val item = value.opt(i)
                            if (item is JSONObject) {
                                val file = item.optString("file", item.optString("url", item.optString("src", "")))
                                val label = item.optString("label", item.optString("name", item.optString("lang", "")))
                                val kind = item.optString("kind", "")
                                if (file.isNotBlank()) {
                                    onSubtitle(file, label.ifBlank { null }, kind.ifBlank { null })
                                }
                            } else if (item is String && (item.contains(".vtt") || item.contains(".srt"))) {
                                onSubtitle(item, null, null)
                            }
                        }
                    } else if (value is String) {
                        if (value.startsWith("http") && (value.contains(".vtt") || value.contains(".srt"))) {
                            val label = element.optString("label", element.optString("name", ""))
                            val kind = element.optString("kind", "")
                            onSubtitle(value, label.ifBlank { null }, kind.ifBlank { null })
                        }
                    } else {
                        parseSubtitlesJsonRecursively(value, onSubtitle)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until element.length()) {
                    val value = element.opt(i) ?: continue
                    parseSubtitlesJsonRecursively(value, onSubtitle)
                }
            }
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
