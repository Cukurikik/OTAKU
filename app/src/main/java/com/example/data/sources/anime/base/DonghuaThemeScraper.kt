package com.example.data.sources.anime.base

import android.content.Context

abstract class DonghuaThemeScraper(
    context: Context,
    override val sourceId: String,
    override val sourceName: String,
    override val baseUrl: String
) : AnimeStreamThemeScraper(context, sourceId, sourceName, baseUrl) {

    override val cardSelector = ".listupd .bs, .listo .bsx, article.bs, .post-show ul li, .bsx, article, .item"
    override val episodeSelector = ".eplister ul li, .eplister li, .episodelist li, .epslist li, .lsteps li, ul.servers li, a.ep-item"
}
