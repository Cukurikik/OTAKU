package com.example.data.sources.local

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

data class ComicInfo(
    val title: String? = null,
    val series: String? = null,
    val number: String? = null,
    val volume: String? = null,
    val summary: String? = null,
    val writer: String? = null,
    val penciller: String? = null,
    val genre: String? = null,
    val pageCount: Int? = null,
    val languageIso: String? = null,
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null
) {
    val author: String?
        get() = when {
            !writer.isNullOrBlank() && !penciller.isNullOrBlank() && writer != penciller -> "$writer, $penciller"
            !writer.isNullOrBlank() -> writer
            !penciller.isNullOrBlank() -> penciller
            else -> null
        }
}

object ComicInfoParser {
    fun parse(inputStream: InputStream): ComicInfo? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            var title: String? = null
            var series: String? = null
            var number: String? = null
            var volume: String? = null
            var summary: String? = null
            var writer: String? = null
            var penciller: String? = null
            var genre: String? = null
            var pageCount: Int? = null
            var languageIso: String? = null
            var year: Int? = null
            var month: Int? = null
            var day: Int? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tagName = parser.name
                    when (tagName.lowercase()) {
                        "title" -> title = parser.nextText()
                        "series" -> series = parser.nextText()
                        "number" -> number = parser.nextText()
                        "volume" -> volume = parser.nextText()
                        "summary" -> summary = parser.nextText()
                        "writer" -> writer = parser.nextText()
                        "penciller" -> penciller = parser.nextText()
                        "genre" -> genre = parser.nextText()
                        "pagecount" -> pageCount = parser.nextText().toIntOrNull()
                        "languageiso" -> languageIso = parser.nextText()
                        "year" -> year = parser.nextText().toIntOrNull()
                        "month" -> month = parser.nextText().toIntOrNull()
                        "day" -> day = parser.nextText().toIntOrNull()
                    }
                }
                eventType = parser.next()
            }
            ComicInfo(
                title = title,
                series = series,
                number = number,
                volume = volume,
                summary = summary,
                writer = writer,
                penciller = penciller,
                genre = genre,
                pageCount = pageCount,
                languageIso = languageIso,
                year = year,
                month = month,
                day = day
            )
        } catch (e: Exception) {
            null
        }
    }
}
