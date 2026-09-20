package com.example.data.sources.drakor

import android.content.Context
import com.example.data.models.ContentType
import com.example.data.sources.templates.DramaScraperTemplate

class DrakorSource(context: Context) : DramaScraperTemplate(context) {
    override val sourceId: String = "drakor"
    override val sourceName: String = "Drakor"
    override val baseUrl: String = "https://nodrakor.icu"
    override val contentType = ContentType.DRAMA
    
    override val selectors = DramaSelectors(
        card = "article, .animepost, .item",
        title = "h2, h3, .title",
        link = "a",
        image = "img",
        description = ".entry-content",
        genres = ".genres a",
        episode = ".episodelist li, .ep-list a",
        episodeTitle = "a, .num",
        episodeDate = "time",
        searchCard = "article, .animepost, .item"
    )
}

