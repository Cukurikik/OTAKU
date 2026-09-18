package com.example.data.sources.anichin

import android.content.Context
import com.example.data.sources.templates.AnimeScraperTemplate
import com.example.data.sources.VideoStreamInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException

class AnichinSource(context: Context) : AnimeScraperTemplate(context) {
    override val sourceId: String = "anichin"
    override val sourceName: String = "AniChin"
    override val baseUrl: String = "https://anichin.com.co"

    override val selectors = AnimeSelectors(
        card = ".listupd .bs, .bsx",
        title = ".tt, h2",
        link = "a",
        image = "img",
        description = ".entry-content, .desc",
        genres = ".genxed a",
        episode = ".eplister li",
        episodeTitle = ".epiNum, .epl-num",
        episodeDate = ".epidate",
        searchCard = ".listupd .bs"
    )

}
