package com.example.data.sources.jucelebrity

import android.content.Context
import com.example.data.models.ContentType
import com.example.data.sources.templates.DramaScraperTemplate

class JuCelebritySource(context: Context) : DramaScraperTemplate(context) {
    override val sourceId: String = "jucelebrity"
    override val sourceName: String = "JuCelebrity"
    override val baseUrl: String = "https://jucelebrity.com"
    override val contentType = ContentType.DRAMA
    
    override val selectors = DramaSelectors(
        card = "article, .post, .item",
        title = "h2, .title, .entry-title",
        link = "a",
        image = "img",
        description = ".entry-content p, .desc",
        genres = ".category a, .genres a",
        episode = ".episodelist a, .episodes a",
        episodeTitle = "span, .title",
        episodeDate = "",
        searchCard = "article, .post, .item"
    )
}

