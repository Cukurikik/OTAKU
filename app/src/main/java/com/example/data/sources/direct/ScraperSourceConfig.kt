package com.example.data.sources.direct

import com.example.data.sources.SourceBaseUrlStore
import com.example.data.sources.dynamic.DynamicSourceConfigManager

/** Selector configuration. Keep selectors outside business logic so site redesigns are cheap to fix. */
data class ScraperSourceConfig(
    val sourceId: String,
    val sourceName: String,
    val defaultBaseUrl: String,
    val baseUrlProvider: () -> String = { SourceBaseUrlStore.getBaseUrl(sourceId, defaultBaseUrl) ?: defaultBaseUrl },
    val searchUrl: (String, Int) -> String,
    val popularUrl: (Int) -> String,
    val selectors: Selectors,
    val pagination: Pagination = Pagination()
) {
    // Secondary constructor for backwards compatibility with direct baseUrl: String
    constructor(
        sourceId: String,
        sourceName: String,
        baseUrl: String,
        searchUrl: (String, Int) -> String,
        popularUrl: (Int) -> String,
        selectors: Selectors,
        pagination: Pagination = Pagination()
    ) : this(
        sourceId = sourceId,
        sourceName = sourceName,
        defaultBaseUrl = baseUrl,
        baseUrlProvider = { SourceBaseUrlStore.getBaseUrl(sourceId, baseUrl) ?: baseUrl },
        searchUrl = searchUrl,
        popularUrl = popularUrl,
        selectors = selectors,
        pagination = pagination
    )

    val baseUrl: String
        get() = baseUrlProvider()

    val effectiveSelectors: Selectors
        get() = DynamicSourceConfigManager.getEffectiveSelectors(sourceId, selectors)
}

data class Selectors(
    val card: String,
    val cardLink: String = "a",
    val cardTitle: String = "",
    val cardImage: String = "img",
    val description: String = "",
    val genres: String = "",
    val episode: String,
    val episodeLink: String = "a",
    val episodeTitle: String = "",
    val player: String = "iframe[src], iframe[data-src], video source[src]",
    val videoAttribute: String = "src",
    val page: String = "img",
    val pageAttribute: String = "src"
)

data class Pagination(
    val firstPage: Int = 1,
    val maxPages: Int = 100,
    val stopWhenNoCards: Boolean = true
)
