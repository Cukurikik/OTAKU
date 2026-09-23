package com.example.data.sources.drama

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.example.data.sources.Subtitle
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.Locale

/**
 * Metadata information for a normalized subtitle language.
 */
data class SubtitleLanguageInfo(
    val iso639_1: String,
    val displayName: String,
    val isDefault: Boolean = false,
    val isForced: Boolean = false
)

/**
 * Standardized engine for extracting, parsing, normalising, and syncing external subtitle tracks
 * (WebVTT, SRT, ASS, TTML) across multi-source drama providers (HTML <track>, JSON embeds, Viki API).
 */
object DramaSubtitleExtractorEngine {

    private val HTML_TRACK_REGEX = Regex(
        """<track[^>]+(?:src|data-src)=["']([^"']+)["'][^>]*>""",
        RegexOption.IGNORE_CASE
    )

    private val JSON_TRACK_ARRAY_REGEX = Regex(
        """["']?(?:tracks|subtitles|captions|sources)["']?\s*:\s*(\[\s*\{.*?\}\s*\])""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    private val JWPLAYER_TRACK_REGEX = Regex(
        """\{\s*["']?file["']?\s*:\s*["']([^"']+\.(?:vtt|srt|ass))["']\s*,\s*["']?label["']?\s*:\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    private val TIMECODE_VTT_SRT_REGEX = Regex(
        """(\d{2,}:\d{2}:\d{2}[.,]\d{2,3})\s*-->\s*(\d{2,}:\d{2}:\d{2}[.,]\d{2,3})"""
    )

    /**
     * Extracts all external subtitles from an HTML page (DOM <track> elements, regex fallbacks, and embedded JSON configurations).
     */
    fun extractSubtitlesFromHtml(htmlContent: String, baseUri: String = ""): List<Subtitle> {
        val results = mutableListOf<Subtitle>()
        val seenUrls = mutableSetOf<String>()

        if (htmlContent.isBlank()) return emptyList()

        val doc: Document = runCatching { Jsoup.parse(htmlContent, baseUri) }.getOrElse { Jsoup.parse(htmlContent) }

        // 1. Scan DOM <track> tags
        val trackElements = doc.select("track[src], track[data-src], track[data-url]")
        for (track in trackElements) {
            val src = track.absUrl("src")
                .ifBlank { track.absUrl("data-src") }
                .ifBlank { track.absUrl("data-url") }
                .ifBlank { track.attr("src") }
                .ifBlank { track.attr("data-src") }
            if (src.isBlank()) continue

            val rawLang = track.attr("srclang")
                .ifBlank { track.attr("label") }
                .ifBlank { track.attr("lang") }
                .ifBlank { "Indonesian" }

            val rawKind = track.attr("kind").lowercase()
            if (rawKind.isNotBlank() && rawKind != "subtitles" && rawKind != "captions") {
                // Ignore thumbnails or chapters tracks
                continue
            }

            val langInfo = normalizeLanguage(rawLang)
            val format = inferSubtitleFormat(src)

            if (seenUrls.add(src)) {
                results.add(
                    Subtitle(
                        language = langInfo.displayName,
                        url = src,
                        format = format
                    )
                )
            }
        }

        // 2. Regex fallback for inline tracks
        if (results.isEmpty()) {
            HTML_TRACK_REGEX.findAll(htmlContent).forEach { match ->
                val fullTag = match.value
                val src = match.groupValues[1]
                val absSrc = resolveUrl(src, baseUri)
                if (absSrc.isNotBlank() && seenUrls.add(absSrc)) {
                    val langMatch = Regex("""srclang=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(fullTag)
                        ?: Regex("""label=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(fullTag)
                    val rawLang = langMatch?.groupValues?.get(1) ?: "id"
                    val langInfo = normalizeLanguage(rawLang)
                    val format = inferSubtitleFormat(absSrc)
                    results.add(
                        Subtitle(
                            language = langInfo.displayName,
                            url = absSrc,
                            format = format
                        )
                    )
                }
            }
        }

        // 3. Scan Embedded JSON scripts (JWPlayer, VideoJS, Plyr configs)
        val scriptElements = doc.select("script")
        for (script in scriptElements) {
            val scriptContent = script.data().ifBlank { script.html() }
            if (scriptContent.isBlank() || (!scriptContent.contains("track", ignoreCase = true) && !scriptContent.contains("caption", ignoreCase = true) && !scriptContent.contains("subtitle", ignoreCase = true))) {
                continue
            }

            // Extract JWPlayer style tracks
            JWPLAYER_TRACK_REGEX.findAll(scriptContent).forEach { jwMatch ->
                val trackUrl = resolveUrl(jwMatch.groupValues[1], baseUri)
                val label = jwMatch.groupValues[2]
                if (trackUrl.isNotBlank() && seenUrls.add(trackUrl)) {
                    val langInfo = normalizeLanguage(label)
                    results.add(
                        Subtitle(
                            language = langInfo.displayName,
                            url = trackUrl,
                            format = inferSubtitleFormat(trackUrl)
                        )
                    )
                }
            }

            // Extract JSON array tracks: [{"file": "...", "label": "...", "kind": "captions"}]
            JSON_TRACK_ARRAY_REGEX.findAll(scriptContent).forEach { arrayMatch ->
                val rawJson = arrayMatch.groupValues[1]
                runCatching {
                    val arr = JSONArray(rawJson)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val file = obj.optString("file").ifBlank { obj.optString("src").ifBlank { obj.optString("url") } }
                        if (file.isBlank()) continue
                        val absFile = resolveUrl(file, baseUri)
                        val label = obj.optString("label").ifBlank { obj.optString("language").ifBlank { obj.optString("srclang") } }
                        val kind = obj.optString("kind", "captions")
                        if (kind != "subtitles" && kind != "captions" && kind.isNotBlank()) continue

                        if (seenUrls.add(absFile)) {
                            val langInfo = normalizeLanguage(label)
                            results.add(
                                Subtitle(
                                    language = langInfo.displayName,
                                    url = absFile,
                                    format = inferSubtitleFormat(absFile)
                                )
                            )
                        }
                    }
                }
            }
        }

        return results
    }

    /**
     * Generates standardized Viki API subtitle endpoints for available languages.
     */
    fun extractVikiSubtitles(
        videoId: String,
        apiBase: String = "https://api.viki.io/v4",
        appId: String = "100005a",
        targetLangs: List<String> = listOf("id", "en", "ko", "ja", "zh", "ms", "th", "es", "fr", "ar")
    ): List<Subtitle> {
        if (videoId.isBlank()) return emptyList()

        return targetLangs.map { langCode ->
            val langInfo = normalizeLanguage(langCode)
            Subtitle(
                language = langInfo.displayName,
                url = "$apiBase/videos/$videoId/subtitles/$langCode.vtt?app=$appId",
                format = "vtt"
            )
        }
    }

    /**
     * Normalizes raw language strings, full names, or code dialects to standardized ISO-639-1 code and display name.
     */
    fun normalizeLanguage(rawLanguage: String): SubtitleLanguageInfo {
        val clean = rawLanguage.trim().lowercase(Locale.ROOT)
        return when {
            clean.contains("indonesia") || clean == "id" || clean == "ind" || clean == "in" || clean.contains("sub indo") || clean.contains("bahasa") ->
                SubtitleLanguageInfo("id", "Indonesian", isDefault = true)

            clean.contains("english") || clean == "en" || clean == "eng" || clean == "en-us" || clean == "en-gb" || clean.contains("eng sub") ->
                SubtitleLanguageInfo("en", "English", isDefault = false)

            clean.contains("korean") || clean.contains("korea") || clean == "ko" || clean == "kor" || clean == "ko-kr" ->
                SubtitleLanguageInfo("ko", "Korean", isDefault = false)

            clean.contains("japanese") || clean.contains("jepang") || clean == "ja" || clean == "jpn" || clean == "jp" ->
                SubtitleLanguageInfo("ja", "Japanese", isDefault = false)

            clean.contains("chinese") || clean.contains("mandarin") || clean == "zh" || clean == "zho" || clean == "chi" || clean == "zh-cn" || clean == "zh-tw" ->
                SubtitleLanguageInfo("zh", "Chinese", isDefault = false)

            clean.contains("thai") || clean == "th" || clean == "tha" ->
                SubtitleLanguageInfo("th", "Thai", isDefault = false)

            clean.contains("spanish") || clean.contains("espanyol") || clean == "es" || clean == "spa" ->
                SubtitleLanguageInfo("es", "Spanish", isDefault = false)

            clean.contains("french") || clean.contains("prancis") || clean == "fr" || clean == "fra" || clean == "fre" ->
                SubtitleLanguageInfo("fr", "French", isDefault = false)

            clean.contains("arabic") || clean.contains("arab") || clean == "ar" || clean == "ara" ->
                SubtitleLanguageInfo("ar", "Arabic", isDefault = false)

            clean.contains("malay") || clean.contains("melayu") || clean == "ms" || clean == "msa" || clean == "may" ->
                SubtitleLanguageInfo("ms", "Malay", isDefault = false)

            clean.contains("vietnamese") || clean.contains("vietnam") || clean == "vi" || clean == "vie" ->
                SubtitleLanguageInfo("vi", "Vietnamese", isDefault = false)

            clean.contains("german") || clean.contains("jerman") || clean == "de" || clean == "deu" || clean == "ger" ->
                SubtitleLanguageInfo("de", "German", isDefault = false)

            clean.contains("portuguese") || clean.contains("portugis") || clean == "pt" || clean == "por" || clean == "pt-br" ->
                SubtitleLanguageInfo("pt", "Portuguese", isDefault = false)

            clean.isNotBlank() ->
                SubtitleLanguageInfo(clean.take(2), rawLanguage.replaceFirstChar { it.uppercase() }, isDefault = false)

            else ->
                SubtitleLanguageInfo("id", "Indonesian", isDefault = true)
        }
    }

    /**
     * Resolves appropriate Media3 MIME type for a given format or URL extension.
     */
    fun resolveMimeType(formatOrUrl: String): String {
        val lower = formatOrUrl.lowercase()
        return when {
            lower.contains(".vtt") || lower == "vtt" || lower.contains("text/vtt") -> MimeTypes.TEXT_VTT
            lower.contains(".srt") || lower == "srt" || lower.contains("subrip") -> MimeTypes.APPLICATION_SUBRIP
            lower.contains(".ass") || lower.contains(".ssa") || lower == "ass" || lower == "ssa" -> MimeTypes.TEXT_SSA
            lower.contains(".ttml") || lower.contains(".dfxp") || lower.contains(".xml") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.TEXT_VTT
        }
    }

    private fun inferSubtitleFormat(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".srt") -> "srt"
            lower.contains(".ass") || lower.contains(".ssa") -> "ass"
            lower.contains(".ttml") || lower.contains(".dfxp") -> "ttml"
            else -> "vtt"
        }
    }

    /**
     * Converts a list of standardized [Subtitle] models into ExoPlayer [MediaItem.SubtitleConfiguration].
     */
    fun buildSubtitleConfigurations(
        subtitles: List<Subtitle>,
        preferredLanguageCode: String = "id"
    ): List<MediaItem.SubtitleConfiguration> {
        if (subtitles.isEmpty()) return emptyList()

        return subtitles.mapIndexed { index, sub ->
            val langInfo = normalizeLanguage(sub.language)
            val mimeType = resolveMimeType(sub.format.ifBlank { sub.url })
            val isPreferred = langInfo.iso639_1.equals(preferredLanguageCode, ignoreCase = true) ||
                    (index == 0 && preferredLanguageCode.isBlank())

            val selectionFlags = if (isPreferred) {
                C.SELECTION_FLAG_DEFAULT
            } else {
                0
            }

            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setMimeType(mimeType)
                .setLanguage(langInfo.iso639_1)
                .setLabel(langInfo.displayName)
                .setSelectionFlags(selectionFlags)
                .build()
        }
    }

    /**
     * Normalizes an SRT string content into standard UTF-8 WebVTT format and applies timecode delta offset:
     * Timecode_normalized = Timecode_raw + Delta_t
     */
    fun convertSrtToWebVtt(srtContent: String, offsetMs: Long = 0L): String {
        val normalizedLineEndings = srtContent.replace("\r\n", "\n").replace("\r", "\n")
        val blocks = normalizedLineEndings.split(Regex("""\n[ \t]*\n"""))
        val sb = StringBuilder("WEBVTT\n\n")

        for (block in blocks) {
            val trimmedBlock = block.trim()
            if (trimmedBlock.isBlank()) continue

            val match = TIMECODE_VTT_SRT_REGEX.find(trimmedBlock) ?: continue
            val startRaw = match.groupValues[1]
            val endRaw = match.groupValues[2]

            val startMs = (parseTimestampToMs(startRaw) + offsetMs).coerceAtLeast(0L)
            val endMs = (parseTimestampToMs(endRaw) + offsetMs).coerceAtLeast(startMs)

            val formattedStart = formatMsToVttTimestamp(startMs)
            val formattedEnd = formatMsToVttTimestamp(endMs)

            val textAfterTimecode = trimmedBlock.substring(match.range.last + 1).trim()
            if (textAfterTimecode.isNotBlank()) {
                sb.append("$formattedStart --> $formattedEnd\n")
                sb.append("$textAfterTimecode\n\n")
            }
        }

        return sb.toString().trimEnd() + "\n"
    }

    /**
     * Applies timecode offset (Delta t) to existing WebVTT content.
     */
    fun applyTimecodeOffsetToVtt(vttContent: String, offsetMs: Long): String {
        if (offsetMs == 0L) return vttContent

        val normalized = vttContent.replace("\r\n", "\n").replace("\r", "\n")
        return TIMECODE_VTT_SRT_REGEX.replace(normalized) { matchResult ->
            val startRaw = matchResult.groupValues[1]
            val endRaw = matchResult.groupValues[2]

            val startMs = (parseTimestampToMs(startRaw) + offsetMs).coerceAtLeast(0L)
            val endMs = (parseTimestampToMs(endRaw) + offsetMs).coerceAtLeast(startMs)

            "${formatMsToVttTimestamp(startMs)} --> ${formatMsToVttTimestamp(endMs)}"
        }
    }

    private fun parseTimestampToMs(timeStr: String): Long {
        val normalized = timeStr.replace(",", ".")
        val parts = normalized.split(":")
        return when (parts.size) {
            3 -> {
                val hours = parts[0].toLongOrNull() ?: 0L
                val minutes = parts[1].toLongOrNull() ?: 0L
                val secParts = parts[2].split(".")
                val seconds = secParts[0].toLongOrNull() ?: 0L
                val msStr = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3) else "000"
                val ms = msStr.toLongOrNull() ?: 0L
                (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L) + ms
            }
            2 -> {
                val minutes = parts[0].toLongOrNull() ?: 0L
                val secParts = parts[1].split(".")
                val seconds = secParts[0].toLongOrNull() ?: 0L
                val msStr = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3) else "000"
                val ms = msStr.toLongOrNull() ?: 0L
                (minutes * 60000L) + (seconds * 1000L) + ms
            }
            else -> 0L
        }
    }

    private fun formatMsToVttTimestamp(ms: Long): String {
        val hours = ms / 3600000L
        val minutes = (ms % 3600000L) / 60000L
        val seconds = (ms % 60000L) / 1000L
        val millis = ms % 1000L
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }

    private fun resolveUrl(url: String, baseUri: String): String {
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("content://") || url.startsWith("file://")) {
            return url
        }
        if (baseUri.isBlank()) return url
        return runCatching {
            val base = java.net.URI(baseUri)
            base.resolve(url).toString()
        }.getOrElse { url }
    }
}
