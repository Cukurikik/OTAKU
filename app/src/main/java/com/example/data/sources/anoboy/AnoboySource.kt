package com.example.data.sources.anoboy

import android.content.Context
import com.example.data.sources.templates.AnimeScraperTemplate
import com.example.data.sources.VideoStreamInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException

class AnoboySource(context: Context) : AnimeScraperTemplate(context) {
    override val sourceId: String = "anoboy"
    override val sourceName: String = "Anoboy"
    override val baseUrl: String = "https://anoboy.be"

    override val selectors = AnimeSelectors(
        card = ".amv, article",
        title = "h3, .title",
        link = "a",
        image = "amp-img, img",
        description = ".contentdeks",
        genres = ".tag a, .tags a",
        episode = ".episodelist ul li",
        episodeTitle = "a",
        episodeDate = "time",
        searchCard = ".amv, article"
    )

}
