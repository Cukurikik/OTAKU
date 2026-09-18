package com.example.data.sources.licil

import android.content.Context
import com.example.data.models.ContentType
import com.example.data.sources.templates.DramaScraperTemplate

class LicilSource(context: Context) : DramaScraperTemplate(context) {
    override val sourceId: String = "licil"
    override val sourceName: String = "Licil"
    override val baseUrl: String = "https://licil.org"
    override val contentType = ContentType.DRAMA
    
    override val selectors = DramaSelectors(
        card = "article, .item, .gmr-item-modulepost",
        title = "h2, h3, .entry-title",
        link = "a",
        image = "img",
        description = ".entry-content p, .gmr-movie-synop",
        genres = ".genres a, .gmr-movie-genre a",
        episode = ".episodelist li, .gmr-listseries a",
        episodeTitle = "a, .num",
        episodeDate = "time",
        searchCard = "article, .item, .gmr-item-modulepost"
    )
}

