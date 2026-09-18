package com.example.data.sources.core

import org.jsoup.nodes.Element

object ParserUtils {
    fun extractImageUrl(element: Element?): String? {
        if (element == null) return null
        return sequenceOf(
            element.attr("src"),
            element.attr("data-src"),
            element.attr("data-lazy-src"),
            element.attr("data-original"),
            element.attr("data-url")
        )
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
    }

    fun extractSrcSet(element: Element?): List<String> {
        if (element == null) return emptyList()
        return element
            .attr("srcset")
            .split(",")
            .map { it.trim().substringBefore(" ") }
            .filter { it.isNotBlank() }
    }
}
