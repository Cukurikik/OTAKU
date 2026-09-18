package com.example.data.sources.lokalfilm

import android.content.Context
import com.example.data.models.ContentType
import com.example.data.sources.templates.DramaScraperTemplate

class LokalFilmSource(context: Context) : DramaScraperTemplate(context) {
    override val sourceId: String = "lokalfilm"
    override val sourceName: String = "LokalFilm"
    override val baseUrl: String = "https://lokalfilm.id"
    override val contentType = ContentType.DRAMA
    
    override val selectors = DramaSelectors(
        card = ".film-item, article, .movie-item",
        title = "h2, .title, h3",
        link = "a",
        image = "img",
        description = ".desc p, .movie-content",
        genres = ".genres a, .cat, .sub-title",
        episode = ".episodes a, li[data-video-source]",
        episodeTitle = "span, .fwduvp-thumbnail-title",
        episodeDate = "",
        searchCard = ".film-item, article, .movie-item"
    )
}

