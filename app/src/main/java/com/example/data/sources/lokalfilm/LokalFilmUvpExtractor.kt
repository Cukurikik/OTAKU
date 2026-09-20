package com.example.data.sources.lokalfilm

import android.util.Base64
import com.example.data.NetworkModule
import com.example.data.sources.Subtitle
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.anime.extractors.CryptoJsDecryptor
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder

/**
 * Extractor khusus untuk FWD Ultra Video Player (UVP) dan Encrypted Sources pada LokalFilm.id.
 * Mendekode Base64 / AES payload UVP, parsing multi-kualitas HLS/MP4, dan mengekstrak subtitle VTT.
 */
class LokalFilmUvpExtractor {

    companion object {
        private const val DEFAULT_REFERER = "https://lokalfilm.id/"
    }

    /**
     * Ekstraksi URL stream dari raw HTML content atau Document.
     * Mengembalikan URL stream utama (HLS / MP4) jika ditemukan.
     */
    fun extractStreamUrl(htmlContent: String): String? {
        val streamInfo = extractVideoStreamInfo(htmlContent, DEFAULT_REFERER)
        return streamInfo?.bestQuality?.url ?: streamInfo?.embedUrl
    }

    /**
     * Ekstraksi komprehensif VideoStreamInfo dari HTML string.
     */
    fun extractVideoStreamInfo(htmlContent: String, episodeUrl: String = DEFAULT_REFERER): VideoStreamInfo? {
        if (htmlContent.isBlank()) return null
        val doc = Jsoup.parse(htmlContent, episodeUrl)
        return extractVideoStreamInfo(doc, episodeUrl)
    }

    /**
     * Ekstraksi komprehensif VideoStreamInfo dari Jsoup Document.
     */
    fun extractVideoStreamInfo(doc: Document, episodeUrl: String = DEFAULT_REFERER): VideoStreamInfo? {
        val qualities = mutableListOf<VideoQuality>()
        val subtitles = mutableListOf<Subtitle>()
        val servers = mutableListOf<VideoServer>()
        val headers = mapOf(
            "Referer" to episodeUrl,
            "User-Agent" to NetworkModule.userAgent,
            "Origin" to "https://lokalfilm.id"
        )

        // 1. Scan semua elemen UVP (li[data-video-source], li[data-source], [data-video-source], etc.)
        val uvpElements = doc.select("li[data-video-source], li[data-source], [data-video-source], [data-mp4], [data-hls], .fwduvp-item")
        for (element in uvpElements) {
            val rawSource = element.attr("data-video-source")
                .ifEmpty { element.attr("data-source") }
                .ifEmpty { element.attr("data-mp4") }
                .ifEmpty { element.attr("data-hls") }

            if (rawSource.isNotBlank()) {
                val decrypted = decryptUvpPayload(rawSource)
                if (decrypted.isNotBlank()) {
                    val parsed = parseUvpDecryptedData(decrypted, headers)
                    qualities.addAll(parsed.qualities)
                    subtitles.addAll(parsed.subtitles)
                }
            }

            // Extract subtitle attribute jika ada
            val rawSub = element.attr("data-subtitles").ifEmpty { element.attr("data-tracks") }
            if (rawSub.isNotBlank()) {
                val decryptedSub = decryptUvpPayload(rawSub)
                subtitles.addAll(parseSubtitles(decryptedSub))
            }
        }

        // 2. Scan inline scripts untuk konfigurasi FWD UVP / videoSources / playlist
        if (qualities.isEmpty()) {
            val scriptElements = doc.select("script")
            for (script in scriptElements) {
                val data = script.data()
                if (data.isBlank()) continue

                if (data.contains("fwduvp", ignoreCase = true) ||
                    data.contains("videoSources", ignoreCase = true) ||
                    data.contains("data-video-source", ignoreCase = true) ||
                    data.contains("playlist", ignoreCase = true)
                ) {
                    // Cek pola videoSources: [...]
                    val videoSourcesRegex = Regex("""(?:videoSources|sourceList|sources)\s*:\s*(\[[^\]]+\])""")
                    val match = videoSourcesRegex.find(data)
                    if (match != null) {
                        val jsonArrStr = match.groupValues[1]
                        val parsed = parseUvpDecryptedData(jsonArrStr, headers)
                        qualities.addAll(parsed.qualities)
                        subtitles.addAll(parsed.subtitles)
                    }

                    // Cek pola data-video-source di dalam JS string
                    val dataAttrRegex = Regex("""data-video-source\s*=\s*\\?["']([^"'\\]+)\\?["']""")
                    for (m in dataAttrRegex.findAll(data)) {
                        val raw = m.groupValues[1]
                        val decrypted = decryptUvpPayload(raw)
                        val parsed = parseUvpDecryptedData(decrypted, headers)
                        qualities.addAll(parsed.qualities)
                        subtitles.addAll(parsed.subtitles)
                    }

                    // Cek direct stream URL dalam script
                    val streamRegex = Regex("""https?://[^"'\s<>\\]+?\.(?:m3u8|mp4)[^"'\s<>\\]*""")
                    for (m in streamRegex.findAll(data)) {
                        val url = m.value
                        val label = if (url.contains("1080")) "1080p" else if (url.contains("720")) "720p" else if (url.contains("480")) "480p" else "Auto"
                        qualities.add(VideoQuality(label, url, url.contains(".m3u8"), headers))
                    }
                }
            }
        }

        // 3. Scan tag <video> atau <source> standar
        if (qualities.isEmpty()) {
            val videoTags = doc.select("video source[src], video[src]")
            for (v in videoTags) {
                val src = v.absUrl("src").ifEmpty { v.attr("src") }
                if (src.isNotBlank() && (src.startsWith("http://") || src.startsWith("https://"))) {
                    val label = v.attr("label").ifEmpty { v.attr("size") }.ifEmpty { "Auto" }
                    qualities.add(VideoQuality(label, src, src.contains(".m3u8"), headers))
                }
            }
        }

        val distinctQualities = qualities.distinctBy { it.url }
        val distinctSubtitles = subtitles.distinctBy { it.url }

        if (distinctQualities.isEmpty()) {
            return null
        }

        val primaryEmbed = distinctQualities.first().url
        servers.add(
            VideoServer(
                name = "LokalFilm UVP Server",
                embedUrl = primaryEmbed,
                qualities = distinctQualities,
                subtitles = distinctSubtitles,
                headers = headers
            )
        )

        return VideoStreamInfo(
            episodeId = episodeUrl,
            embedUrl = primaryEmbed,
            qualities = distinctQualities,
            subtitles = distinctSubtitles,
            headers = headers,
            servers = servers
        )
    }

    /**
     * Mendekode payload data-video-source UVP.
     * Mendukung plain string, Base64 encoding, URL-encoded payload, dan AES ciphertext.
     */
    fun decryptUvpPayload(data: String): String {
        val trimmed = data.trim()
        if (trimmed.isEmpty()) return ""

        // 1. Jika sudah plain URL atau JSON
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
            trimmed.startsWith("[") || trimmed.startsWith("{")
        ) {
            return trimmed
        }

        // 2. Coba URL Decode jika ada %
        val unencoded = if (trimmed.contains("%")) {
            try { URLDecoder.decode(trimmed, "UTF-8") } catch (e: Exception) { trimmed }
        } else {
            trimmed
        }

        if (unencoded.startsWith("http://") || unencoded.startsWith("https://") ||
            unencoded.startsWith("[") || unencoded.startsWith("{")
        ) {
            return unencoded
        }

        // 3. Base64 Decoding
        val decodedBase64 = try {
            val cleaned = unencoded.replace(" ", "+").replace("\n", "").replace("\r", "")
            val bytes = try {
                java.util.Base64.getDecoder().decode(cleaned)
            } catch (e: Throwable) {
                Base64.decode(cleaned, Base64.DEFAULT)
            }
            String(bytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            ""
        }

        if (decodedBase64.isNotBlank()) {
            // Check jika hasil decode adalah plain URL atau JSON
            if (decodedBase64.startsWith("http://") || decodedBase64.startsWith("https://") ||
                decodedBase64.startsWith("[") || decodedBase64.startsWith("{") ||
                decodedBase64.contains(".m3u8") || decodedBase64.contains(".mp4")
            ) {
                return decodedBase64
            }

            // Jika masih terenkripsi CryptoJS AES ("Salted__")
            if (decodedBase64.startsWith("Salted__") || unencoded.startsWith("Salted__")) {
                val passphrases = listOf("lokalfilm", "fwduvp", "ultra_video_player", "secret", "video_key")
                for (pass in passphrases) {
                    val decrypted = CryptoJsDecryptor.decrypt(unencoded, pass)
                        ?: CryptoJsDecryptor.decrypt(decodedBase64, pass)
                    if (!decrypted.isNullOrBlank()) {
                        return decrypted
                    }
                }
            }
        }

        return decodedBase64.ifEmpty { unencoded }
    }

    private data class ParsedUvpData(
        val qualities: List<VideoQuality> = emptyList(),
        val subtitles: List<Subtitle> = emptyList()
    )

    private fun parseUvpDecryptedData(decryptedText: String, headers: Map<String, String>): ParsedUvpData {
        var text = decryptedText.trim()
        if (text.startsWith("\"") && text.endsWith("\"") && text.length > 2) {
            text = text.substring(1, text.length - 1)
        }
        text = text.replace("\\/", "/").replace("\\\"", "\"")

        val qualities = mutableListOf<VideoQuality>()
        val subtitles = mutableListOf<Subtitle>()

        // 1. JSON Array format: [{"source": "...", "label": "1080p"}, ...]
        if (text.startsWith("[") && text.endsWith("]")) {
            try {
                val array = JSONArray(text)
                for (i in 0 until array.length()) {
                    val item = array.opt(i)
                    if (item is JSONObject) {
                        val src = item.optString("source")
                            .ifEmpty { item.optString("file") }
                            .ifEmpty { item.optString("src") }
                            .ifEmpty { item.optString("url") }
                        val label = item.optString("label")
                            .ifEmpty { item.optString("quality") }
                            .ifEmpty { item.optString("res") }
                            .ifEmpty { "Auto" }

                        if (src.isNotBlank() && (src.startsWith("http://") || src.startsWith("https://") || src.startsWith("//"))) {
                            val fixedUrl = if (src.startsWith("//")) "https:$src" else src
                            qualities.add(VideoQuality(label, fixedUrl, fixedUrl.contains(".m3u8"), headers))
                        }
                    } else if (item is String && (item.startsWith("http://") || item.startsWith("https://"))) {
                        qualities.add(VideoQuality("Auto", item, item.contains(".m3u8"), headers))
                    }
                }
            } catch (e: Exception) {
                // Fallthrough to regex
            }
        }

        // 2. JSON Object format: {"sources": [...], "tracks": [...]}
        if (text.startsWith("{") && text.endsWith("}")) {
            try {
                val obj = JSONObject(text)
                val sources = obj.optJSONArray("sources") ?: obj.optJSONArray("videoSources") ?: obj.optJSONArray("playlist")
                if (sources != null) {
                    for (i in 0 until sources.length()) {
                        val item = sources.optJSONObject(i) ?: continue
                        val src = item.optString("source").ifEmpty { item.optString("file") }.ifEmpty { item.optString("src") }
                        val label = item.optString("label").ifEmpty { item.optString("quality") }.ifEmpty { "Auto" }
                        if (src.isNotBlank() && (src.startsWith("http://") || src.startsWith("https://") || src.startsWith("//"))) {
                            val fixedUrl = if (src.startsWith("//")) "https:$src" else src
                            qualities.add(VideoQuality(label, fixedUrl, fixedUrl.contains(".m3u8"), headers))
                        }
                    }
                }

                val singleSrc = obj.optString("source").ifEmpty { obj.optString("file") }.ifEmpty { obj.optString("url") }
                if (singleSrc.isNotBlank() && (singleSrc.startsWith("http://") || singleSrc.startsWith("https://"))) {
                    qualities.add(VideoQuality("Auto", singleSrc, singleSrc.contains(".m3u8"), headers))
                }

                val tracks = obj.optJSONArray("subtitles") ?: obj.optJSONArray("tracks")
                if (tracks != null) {
                    for (i in 0 until tracks.length()) {
                        val track = tracks.optJSONObject(i) ?: continue
                        val file = track.optString("source").ifEmpty { track.optString("file") }.ifEmpty { track.optString("url") }
                        val label = track.optString("label").ifEmpty { track.optString("name") }.ifEmpty { "Indonesian" }
                        if (file.isNotBlank() && (file.startsWith("http://") || file.startsWith("https://"))) {
                            subtitles.add(Subtitle(language = label, url = file, format = file.substringAfterLast(".", "vtt")))
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallthrough to regex
            }
        }

        // 3. Direct URL or Regex extraction
        if (qualities.isEmpty()) {
            val streamRegex = Regex("""https?://[^"'\s<>\\]+?\.(?:m3u8|mp4)[^"'\s<>\\]*""")
            for (m in streamRegex.findAll(text)) {
                val url = m.value
                val label = if (url.contains("1080")) "1080p" else if (url.contains("720")) "720p" else if (url.contains("480")) "480p" else "Auto"
                qualities.add(VideoQuality(label, url, url.contains(".m3u8"), headers))
            }
        }

        return ParsedUvpData(qualities, subtitles)
    }

    private fun parseSubtitles(subText: String): List<Subtitle> {
        val list = mutableListOf<Subtitle>()
        val trimmed = subText.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val file = obj.optString("source").ifEmpty { obj.optString("file") }.ifEmpty { obj.optString("url") }
                    val label = obj.optString("label").ifEmpty { obj.optString("name") }.ifEmpty { "Subtitle" }
                    if (file.isNotBlank()) {
                        list.add(Subtitle(language = label, url = file, format = file.substringAfterLast(".", "vtt")))
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            list.add(Subtitle(language = "Indonesian", url = trimmed, format = trimmed.substringAfterLast(".", "vtt")))
        }
        return list
    }
}
