package com.example.data.sources.kazefuri

import android.content.Context
import com.example.data.sources.templates.AnimeScraperTemplate
import com.example.data.sources.VideoStreamInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException

class KazefuriSource(context: Context) : AnimeScraperTemplate(context) {
    override val sourceId: String = "kazefuri"
    override val sourceName: String = "Kazefuri"
    override val baseUrl: String = "https://sv4.kazefuri.cloud"

    override val selectors = AnimeSelectors(
        card = "article, .animepost",
        title = "h2, h3",
        link = "a",
        image = "img",
        description = ".entry-content",
        genres = ".genres a",
        episode = ".episodelist li",
        episodeTitle = "a",
        episodeDate = "time",
        searchCard = "article, .animepost"
    )

}
