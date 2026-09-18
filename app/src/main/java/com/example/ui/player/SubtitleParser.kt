package com.example.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import com.example.data.NetworkModule

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

object SubtitleParser {
    suspend fun fetchAndParse(url: String, offsetMs: Long = 0L): List<SubtitleCue> = withContext(Dispatchers.IO) {
        try {
            val content = if (url.startsWith("file://")) {
                java.io.File(url.removePrefix("file://")).readText()
            } else {
                val req = Request.Builder().url(url).build()
                val resp = NetworkModule.okHttpClient.newCall(req).execute()
                resp.body?.string() ?: return@withContext emptyList()
            }
            parse(content, offsetMs)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parse(content: String, offsetMs: Long = 0L): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val timeRegex = Regex("""(\d{2,}:\d{2}:\d{2}[.,]\d{2,3})\s*-->\s*(\d{2,}:\d{2}:\d{2}[.,]\d{2,3})""")
        
        // Normalize line endings
        val normalized = content.replace("\r\n", "\n").replace("\r", "\n")
        val blocks = normalized.split(Regex("""\n[ \t]*\n"""))
        
        for (block in blocks) {
            val match = timeRegex.find(block)
            if (match != null) {
                val startStr = match.groupValues[1]
                val endStr = match.groupValues[2]
                var text = block.substring(match.range.last + 1).trim()
                
                // Strip formatting tags like <b>, <i>, <font color="...">, etc for now
                text = text.replace(Regex("""<[^>]*>"""), "")
                // Strip VTT positioning tags like align:middle line:90%
                text = text.replace(Regex("""^[a-zA-Z0-9\-:]+(\s+[a-zA-Z0-9\-:]+)*\s*\n"""), "")
                
                val start = (parseTimeMs(startStr) + offsetMs).coerceAtLeast(0L)
                val end = (parseTimeMs(endStr) + offsetMs).coerceAtLeast(start)
                cues.add(SubtitleCue(start, end, text))
            }
        }
        return cues
    }

    fun applyOffset(cues: List<SubtitleCue>, offsetMs: Long): List<SubtitleCue> {
        if (offsetMs == 0L) return cues
        return cues.map { cue ->
            val newStart = (cue.startMs + offsetMs).coerceAtLeast(0L)
            val newEnd = (cue.endMs + offsetMs).coerceAtLeast(newStart)
            cue.copy(startMs = newStart, endMs = newEnd)
        }
    }

    private fun parseTimeMs(time: String): Long {
        val parts = time.replace(",", ".").split(":")
        if (parts.size == 3) {
            val h = parts[0].toLongOrNull() ?: 0L
            val m = parts[1].toLongOrNull() ?: 0L
            val sParts = parts[2].split(".")
            val s = sParts[0].toLongOrNull() ?: 0L
            val msStr = if (sParts.size > 1) sParts[1].padEnd(3, '0').substring(0, 3) else "000"
            val ms = msStr.toLongOrNull() ?: 0L
            return (h * 3600000) + (m * 60000) + (s * 1000) + ms
        }
        return 0L
    }
}
