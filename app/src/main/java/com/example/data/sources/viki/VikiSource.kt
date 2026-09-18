package com.example.data.sources.viki

import android.content.Context
import com.example.data.models.ContentType
import com.example.data.sources.templates.DramaScraperTemplate

class VikiSource(context: Context) : DramaScraperTemplate(context) {
    override val sourceId: String = "viki"
    override val sourceName: String = "Viki"
    override val baseUrl: String = "https://www.viki.com"
    override val contentType = ContentType.DRAMA
    
    override val selectors = DramaSelectors(
        card = ".thumbnail-wrapper, .card, .container, .flw-item",
        title = "h3, .title",
        link = "a",
        image = "img",
        description = ".description, p",
        genres = ".genres a",
        episode = ".episode-list a, .episodelist a",
        episodeTitle = "span.title, span",
        episodeDate = "",
        searchCard = ".thumbnail-wrapper, .card, .flw-item"
    )
}

