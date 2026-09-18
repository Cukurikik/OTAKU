package com.example.data.sources.kusonime

import android.content.Context
import com.example.data.sources.templates.AnimeScraperTemplate
import com.example.data.sources.VideoStreamInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException

class KusonimeSource(context: Context) : AnimeScraperTemplate(context) {
    override val sourceId: String = "kusonime"
    override val sourceName: String = "Kusonime"
    override val baseUrl: String = "https://kusonime.com"

    override val selectors = AnimeSelectors(
        card = ".venz ul li, .post-item",
        title = "h2, .title",
        link = "a",
        image = "img",
        description = ".lexot p",
        genres = ".info a",
        episode = ".lexot a", // Kusonime is batch oriented
        episodeTitle = "a",
        episodeDate = "",
        searchCard = ".venz ul li, .post-item"
    )

}
