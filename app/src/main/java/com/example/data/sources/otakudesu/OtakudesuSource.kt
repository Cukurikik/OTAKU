package com.example.data.sources.otakudesu

import android.content.Context
import com.example.data.sources.templates.AnimeScraperTemplate
import com.example.data.sources.VideoStreamInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException

class OtakudesuSource(context: Context) : AnimeScraperTemplate(context) {
    override val sourceId: String = "otakudesu"
    override val sourceName: String = "Otakudesu"
    override val baseUrl: String = "https://otakudesu.blog"

    override val selectors = AnimeSelectors(
        card = ".venz ul li, .epztipe ul li",
        title = ".jdlse, h2, .judul",
        link = "a",
        image = "img",
        description = ".sinopsis p, .dtl-right .sinopc p",
        genres = ".genre-list a, .dtl-right .genre a",
        episode = ".episodelist ul li",
        episodeTitle = "a",
        episodeDate = ".zeebr",
        searchCard = ".chivsrc li"
    )

    override fun searchUrl(query: String, page: Int): String =
        "${currentBaseUrl()}/?s=${java.net.URLEncoder.encode(query, "UTF-8")}&post_type=anime"

}
