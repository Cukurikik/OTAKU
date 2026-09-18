package com.example.data.sources.meownime

import android.content.Context
import com.example.data.sources.templates.AnimeScraperTemplate
import com.example.data.sources.VideoStreamInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException

class MeownimeSource(context: Context) : AnimeScraperTemplate(context) {
    override val sourceId: String = "meownime"
    override val sourceName: String = "Meownime"
    override val baseUrl: String = "https://meownime.ltd"

    override val selectors = AnimeSelectors(
        card = "article, .post-item",
        title = "h2, h3",
        link = "a",
        image = "img",
        description = ".entry-content, .desc",
        genres = ".genre a, .genres a",
        episode = ".episode-list li, ul li",
        episodeTitle = "a",
        episodeDate = "time",
        searchCard = "article, .post-item"
    )

}
