package com.example.data.sources.registry

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Small reusable HTML extraction layer for new source adapters.
 * Keep selectors in the adapter/config, never in ViewModels.
 */
class HtmlSelectorEngine(
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val timeoutMs: Int = 15_000
) {
    fun fetch(url: String): Document = Jsoup.connect(url)
        .userAgent(userAgent)
        .timeout(timeoutMs)
        .followRedirects(true)
        .get()

    fun text(root: Element, selector: String): String = root.select(selector).firstOrNull()?.text().orEmpty()

    fun attr(root: Element, selector: String, attribute: String): String =
        root.select(selector).firstOrNull()?.attr(attribute).orEmpty()

    fun texts(root: Element, selector: String): List<String> =
        root.select(selector).map { it.text().trim() }.filter { it.isNotEmpty() }

    fun attrs(root: Element, selector: String, attribute: String): List<String> =
        root.select(selector).map { it.attr(attribute).trim() }.filter { it.isNotEmpty() }

    companion object {
        const val DEFAULT_USER_AGENT = "CineManga/1.0 (Android; source-adapter)"
    }
}
