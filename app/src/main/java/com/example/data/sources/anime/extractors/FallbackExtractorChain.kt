package com.example.data.sources.anime.extractors

import com.example.data.NetworkModule
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoStreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FallbackExtractorChain:
 * Membangun rantaian dekoder terstruktur yang secara sekuensial mengurai string terenkripsi
 * atau obfuscated code dari berbagai penyedia layanan embed player dan stream provider
 * (Packer, CryptoJS, VidHide, StreamWish, Dood, FileLions, Mp4Upload, Streamtape, Blogger, Gofile, dll.)
 * hingga menemukan direct HLS (.m3u8) atau MP4 stream URL.
 */
class FallbackExtractorChain {

    class PackerExtractor {
        fun canExtract(htmlPayload: String): Boolean {
            return htmlPayload.contains("eval(function(p,a,c,k,e,", ignoreCase = true) ||
                   htmlPayload.contains("eval(function(p,a,c,k,e,r)", ignoreCase = true) ||
                   htmlPayload.contains("eval(function(p,a,c,k,e,d)", ignoreCase = true)
        }

        fun extract(htmlPayload: String): String? {
            val evalRegex = Regex("""eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k[\s\S]*?\)\s*\)""")
            for (evalMatch in evalRegex.findAll(htmlPayload)) {
                val unpacked = JsPackerUnpacker.unpack(evalMatch.value) ?: continue
                val streamRegexes = listOf(
                    Regex("""(?:source|file|src)\s*:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""", RegexOption.IGNORE_CASE),
                    Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
                    Regex("""["'](https?://[^"']+\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE)
                )
                for (regex in streamRegexes) {
                    val match = regex.find(unpacked)
                    if (match != null) {
                        return match.groupValues[1].replace("\\/", "/")
                    }
                }
            }
            return null
        }
    }

    class CryptoJsExtractor {
        fun canExtract(htmlPayload: String): Boolean {
            return htmlPayload.contains("CryptoJS") ||
                   htmlPayload.contains("U2FsdGVkX1") ||
                   htmlPayload.contains("data-token") ||
                   htmlPayload.contains("decrypt")
        }

        fun extract(htmlPayload: String): String? {
            val saltedRegex = Regex("""U2FsdGVkX1[A-Za-z0-9+/=]{16,}""")
            val candidateKeys = listOf("kurama", "animasu", "nimegami", "otakudesu", "anichin", "kazefuri", "secret", "stream")
            for (match in saltedRegex.findAll(htmlPayload)) {
                val ciphertext = match.value
                for (key in candidateKeys) {
                    val decrypted = CryptoJsDecryptor.decrypt(ciphertext, key)
                    if (!decrypted.isNullOrBlank()) {
                        val parsed = CryptoJsDecryptor.parseDecryptedPayload(decrypted, "")
                        val directUrl = parsed.qualities.firstOrNull()?.url
                        if (!directUrl.isNullOrBlank()) return directUrl
                    }
                }
            }
            return null
        }
    }

    private val packerExtractor = PackerExtractor()
    private val cryptoJsExtractor = CryptoJsExtractor()
    private val vidHideExtractor = VidHideExtractor()
    private val streamWishExtractor = StreamWishExtractor()
    private val doodExtractor = DoodExtractor()
    private val fileLionsExtractor = FileLionsExtractor()
    private val mp4UploadExtractor = Mp4UploadExtractor()
    private val streamtapeExtractor = StreamtapeExtractor()
    private val bloggerExtractor = BloggerExtractor()
    private val gofileExtractor = GofileExtractor()
    private val krakenfilesExtractor = KrakenfilesExtractor()
    private val hxFileExtractor = com.example.data.sources.drama.extractors.HxFileExtractor()
    private val mixdropExtractor = com.example.data.sources.drama.extractors.MixdropExtractor()
    private val fembedExtractor = com.example.data.sources.drama.extractors.FembedExtractor()
    private val streamSBExtractor = com.example.data.sources.drama.extractors.StreamSBExtractor()

    /**
     * Mengekstrak tautan stream video HLS/MP4 langsung dari payload HTML terenkripsi.
     */
    fun extractDirectStream(htmlPayload: String): String? {
        if (htmlPayload.isBlank()) return null

        // 1. Direct match m3u8 or mp4
        val directStreamRegex = Regex("""["'](https?://[^"'\s<>]+\.(?:m3u8|mp4)(?:\?[^"'\s<>]*)?)["']""", RegexOption.IGNORE_CASE)
        val directMatch = directStreamRegex.find(htmlPayload)
        if (directMatch != null) {
            val url = directMatch.groupValues[1].replace("\\/", "/")
            if (!url.contains(".jpg") && !url.contains(".png") && !url.contains("favicon")) {
                return url
            }
        }

        // 2. StreamWish
        if (streamWishExtractor.canExtract(htmlPayload)) {
            val wishQualities = streamWishExtractor.extractFromHtml(htmlPayload)
            val wishStream = wishQualities.firstOrNull()?.url
            if (!wishStream.isNullOrBlank()) return wishStream
        }

        // 3. Packer Extractor
        if (packerExtractor.canExtract(htmlPayload)) {
            val packerStream = packerExtractor.extract(htmlPayload)
            if (!packerStream.isNullOrBlank()) return packerStream
        }

        // 4. CryptoJS Extractor
        if (cryptoJsExtractor.canExtract(htmlPayload)) {
            val cryptoStream = cryptoJsExtractor.extract(htmlPayload)
            if (!cryptoStream.isNullOrBlank()) return cryptoStream
        }

        return null
    }

    /**
     * Resolusi komprehensif mengembalikan seluruh VideoQuality dari payload HTML atau embed URL.
     */
    suspend fun extractQualities(
        htmlPayload: String,
        pageUrl: String,
        headers: Map<String, String> = mapOf("Referer" to pageUrl, "User-Agent" to NetworkModule.userAgent)
    ): List<VideoQuality> = withContext(Dispatchers.IO) {
        val qualities = mutableListOf<VideoQuality>()

        // 1. Check direct HTML stream links
        val directUrl = extractDirectStream(htmlPayload)
        if (!directUrl.isNullOrBlank()) {
            val isM3u8 = directUrl.contains(".m3u8")
            qualities.add(VideoQuality(if (isM3u8) "Direct HLS" else "Direct MP4", directUrl, isM3u8, headers))
        }

        // 2. Extract embedded server iframes
        val iframeRegex = Regex("""<iframe[^>]+src=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
        for (match in iframeRegex.findAll(htmlPayload)) {
            val iframeSrc = match.groupValues[1]
            val lower = iframeSrc.lowercase()
            try {
                when {
                    lower.contains("vidhide") || lower.contains("streamhide") -> {
                        qualities.addAll(vidHideExtractor.extract(iframeSrc))
                    }
                    lower.contains("streamwish") || lower.contains("wishembed") || lower.contains("strwish") -> {
                        qualities.addAll(streamWishExtractor.extract(iframeSrc))
                    }
                    lower.contains("dood") || lower.contains("ds2play") || lower.contains("doodstream") -> {
                        qualities.addAll(doodExtractor.extract(iframeSrc))
                    }
                    lower.contains("filelions") || lower.contains("lion") -> {
                        qualities.addAll(fileLionsExtractor.extract(iframeSrc))
                    }
                    lower.contains("streamtape") -> {
                        qualities.addAll(streamtapeExtractor.extract(iframeSrc))
                    }
                    lower.contains("mp4upload") -> {
                        qualities.addAll(mp4UploadExtractor.extract(iframeSrc))
                    }
                    lower.contains("blogger") || lower.contains("blogspot") -> {
                        qualities.addAll(bloggerExtractor.extract(iframeSrc))
                    }
                    lower.contains("gofile") -> {
                        qualities.addAll(gofileExtractor.extract(iframeSrc))
                    }
                    lower.contains("krakenfiles") -> {
                        qualities.addAll(krakenfilesExtractor.extract(iframeSrc))
                    }
                    lower.contains("hxfile") -> {
                        qualities.addAll(hxFileExtractor.extract(iframeSrc))
                    }
                    lower.contains("mixdrop") -> {
                        qualities.addAll(mixdropExtractor.extract(iframeSrc))
                    }
                    lower.contains("fembed") || lower.contains("feurl") || lower.contains("dutrag") || lower.contains("fcdn") -> {
                        qualities.addAll(fembedExtractor.extract(iframeSrc))
                    }
                    lower.contains("streamsb") || lower.contains("sbembed") || lower.contains("sbvideo") || lower.contains("watchsb") -> {
                        qualities.addAll(streamSBExtractor.extract(iframeSrc))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        qualities.distinctBy { it.url }
    }

    /**
     * Resolusi full VideoStreamInfo dari payload HTML.
     */
    suspend fun resolveStreamInfo(
        htmlPayload: String,
        episodeUrl: String,
        customHeaders: Map<String, String> = mapOf("Referer" to episodeUrl, "User-Agent" to NetworkModule.userAgent)
    ): VideoStreamInfo? {
        val qualities = extractQualities(htmlPayload, episodeUrl, customHeaders)
        if (qualities.isEmpty()) return null

        val primary = qualities.first()
        return VideoStreamInfo(
            episodeId = episodeUrl,
            embedUrl = primary.url,
            qualities = qualities,
            headers = primary.headers.ifEmpty { customHeaders }
        )
    }
}
