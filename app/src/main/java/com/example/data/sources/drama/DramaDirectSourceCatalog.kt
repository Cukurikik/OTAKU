package com.example.data.sources.drama

import com.example.data.sources.SourceBaseUrlStore
import com.example.data.sources.direct.ScraperSourceConfig
import com.example.data.sources.direct.Selectors

/** Direct Drama source catalogue. */
object DramaDirectSourceCatalog {
    val sources: List<ScraperSourceConfig> = listOf(
        gmrTheme("licil", "Licil", "https://digitalepiphanies.org"),
        gmrTheme("licil-barat", "Licil Barat", "https://digitalepiphanies.org/category/western-series"),
        gmrTheme("ip-178", "Streaming 1", "http://178.128.91.191"),
        gmrTheme("jucelebrity", "JuCelebrity", "https://jucelebrity.com"),
        gmrTheme("ip-134", "Streaming 2", "http://134.209.105.11"),
        lokalFilmTheme("lokalfilm", "LokalFilm", "https://lokalfilm.id"),
        genericTheme("viki", "Viki", "https://www.viki.com")
    )

    private fun gmrTheme(id: String, name: String, base: String) = ScraperSourceConfig(
        sourceId = id,
        sourceName = name,
        defaultBaseUrl = base,
        baseUrlProvider = { SourceBaseUrlStore.getBaseUrl(id, base) ?: base },
        searchUrl = { q, _ ->
            val curBase = SourceBaseUrlStore.getBaseUrl(id, base) ?: base
            "$curBase/?s=${java.net.URLEncoder.encode(q, "UTF-8")}"
        },
        popularUrl = { p ->
            val curBase = SourceBaseUrlStore.getBaseUrl(id, base) ?: base
            if (p <= 1) curBase else "$curBase/page/$p/"
        },
        selectors = Selectors(
            card = ".item-infinite, .gmr-box-content, .item, article, .gmr-item-modulepost",
            cardLink = "a.gmr-watch-btn, a[itemprop='url'], a",
            cardTitle = ".entry-title, h2, h3",
            cardImage = "img",
            description = ".entry-content, .gmr-movie-synop",
            genres = ".gmr-movie-genre a, .gmr-movie-tags a",
            episode = ".gmr-listseries a, .episodelist li",
            episodeLink = "a",
            episodeTitle = ".num",
            player = ".gmr-embed-responsive iframe, iframe[src], iframe[data-src]"
        )
    )

    private fun lokalFilmTheme(id: String, name: String, base: String) = ScraperSourceConfig(
        sourceId = id,
        sourceName = name,
        defaultBaseUrl = base,
        baseUrlProvider = { SourceBaseUrlStore.getBaseUrl(id, base) ?: base },
        searchUrl = { q, _ ->
            val curBase = SourceBaseUrlStore.getBaseUrl(id, base) ?: base
            "$curBase/?s=${java.net.URLEncoder.encode(q, "UTF-8")}"
        },
        popularUrl = { p -> 
            val curBase = SourceBaseUrlStore.getBaseUrl(id, base) ?: base
            when (p) {
                1 -> curBase
                2 -> "$curBase/sub-category/12"
                3 -> "$curBase/sub-category/2"
                4 -> "$curBase/sub-category/3"
                5 -> "$curBase/sub-category/7"
                6 -> "$curBase/sub-category/8"
                else -> curBase
            }
        },
        selectors = Selectors(
            card = ".movie-item",
            cardLink = "a",
            cardTitle = "h3, .title, a",
            cardImage = "img",
            description = ".movie-content, meta[name='description']",
            genres = ".cat, .sub-title",
            episode = "li[data-video-source]",
            episodeLink = "a",
            episodeTitle = ".fwduvp-thumbnail-title",
            player = "li[data-video-source]"
        )
    )

    private fun genericTheme(id: String, name: String, base: String) = ScraperSourceConfig(
        sourceId = id,
        sourceName = name,
        defaultBaseUrl = base,
        baseUrlProvider = { SourceBaseUrlStore.getBaseUrl(id, base) ?: base },
        searchUrl = { q, _ ->
            val curBase = SourceBaseUrlStore.getBaseUrl(id, base) ?: base
            "$curBase/?s=${java.net.URLEncoder.encode(q, "UTF-8")}"
        },
        popularUrl = { p ->
            val curBase = SourceBaseUrlStore.getBaseUrl(id, base) ?: base
            if (p <= 1) curBase else "$curBase/page/$p/"
        },
        selectors = Selectors(
            card = ".film-list .item, .listupd .bs, .bsx, article, .post-item, .item, .gmr-box-content, .movie-item, .album, .video-block, .post, .video-post, .flw-item",
            cardLink = "a",
            cardTitle = ".tt, .title, .entry-title, h2, h3, h5, .album-title, .video-title, .titlelist",
            cardImage = "img, .thumb img, .kover img, .video-thumb img",
            description = ".entry-content, .desc, .description, .synp, .synopsis",
            genres = ".genres a, .genre a, .genxed a, .info a[rel='tag']",
            episode = ".episodelist li, .eplister li, .episode-list li, .epslist li, .episodes a, .video-links a, .server-list li, ul.servers li",
            episodeLink = "a",
            episodeTitle = ".epl-title, .ep-title, .episodi, .num, .server-name",
            player = "iframe[src], iframe[data-src], video source[src], .player-video iframe, #pembed iframe, .video-content iframe, .player-area iframe, #player iframe"
        )
    )
}
