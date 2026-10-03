package com.example.data.sources.core

import com.example.data.sources.manga.base.UniversalMangaDomExtractor
import org.jsoup.nodes.Element

object ParserUtils {
    fun extractImageUrl(element: Element?): String? {
        if (element == null) return null
        val url = UniversalMangaDomExtractor.extractImageUrl(element)
        return url.ifBlank { null }
    }

    fun extractSrcSet(element: Element?): List<String> {
        if (element == null) return emptyList()
        val raw = element.attr("srcset").ifBlank { element.attr("data-srcset") }
        if (raw.isBlank()) return emptyList()
        return raw
            .split(",")
            .map { it.trim().substringBefore(" ") }
            .filter { it.isNotBlank() && UniversalMangaDomExtractor.isValidMangaImage(it) }
    }
}

