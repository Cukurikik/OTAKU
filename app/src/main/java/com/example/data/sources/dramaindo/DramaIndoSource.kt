package com.example.data.sources.dramaindo

import android.content.Context
import com.example.data.models.ContentType
import com.example.data.sources.templates.DramaScraperTemplate

class DramaIndoSource(context: Context) : DramaScraperTemplate(context) {
    override val sourceId: String = "dramaindo"
    override val sourceName: String = "DramaIndo"
    override val baseUrl: String = "http://178.128.91.191"
    override val contentType = ContentType.DRAMA
    
    override val selectors = DramaSelectors(
        card = ".venz ul li, .post-item, .item",
        title = "h2, .title, .entry-title",
        link = "a",
        image = "img",
        description = ".lexot p, .desc",
        genres = ".info a, .genres a",
        episode = ".lexot a, .episodelist li, .ep-list a",
        episodeTitle = "a, .num",
        episodeDate = "",
        searchCard = ".venz ul li, .post-item, .item"
    )
}

