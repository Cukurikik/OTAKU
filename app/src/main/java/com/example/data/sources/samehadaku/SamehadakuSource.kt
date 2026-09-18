package com.example.data.sources.samehadaku

import android.content.Context
import com.example.data.sources.templates.AnimeScraperTemplate
import com.example.data.sources.VideoStreamInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException

class SamehadakuSource(context: Context) : AnimeScraperTemplate(context) {
    override val sourceId: String = "samehadaku"
    override val sourceName: String = "Samehadaku"
    override val baseUrl: String = "https://v2.samehadaku.how"

    override val selectors = AnimeSelectors(
        card = ".listupd .bs, article.animpost",
        title = ".tt, h2",
        link = "a",
        image = "img",
        description = ".entry-content p",
        genres = ".genxed a",
        episode = ".eplister li",
        episodeTitle = ".epiNum",
        episodeDate = ".epidate",
        searchCard = ".listupd .bs"
    )

    override fun recommendationsUrl(page: Int): String =
        "${currentBaseUrl()}/anime/?page=$page&status=ongoing&sub=sub&order=update"

    override fun searchUrl(query: String, page: Int): String =
        "${currentBaseUrl()}/page/$page/?s=${java.net.URLEncoder.encode(query, "UTF-8")}"

    override fun getChapters(itemId: String): Flow<List<com.example.data.models.Chapter>> = flow {
        try {
            val doc = fetcher.fetchHtml(itemId)
            val eps = doc.select("#episode_page li a").mapNotNull { a ->
                com.example.data.models.Chapter(id = a.absUrl("href"), title = a.text(), url = a.absUrl("href"))
            }
            if (eps.isNotEmpty()) {
                val allEps = mutableListOf<com.example.data.models.Chapter>()
                for (epPageLink in eps) {
                    val epDoc = fetcher.fetchHtml(epPageLink.url)
                    val pageEps = epDoc.select(".eplister li").mapNotNull { el ->
                        val a = el.selectFirst("a") ?: return@mapNotNull null
                        com.example.data.models.Chapter(id = a.absUrl("href"), title = el.selectFirst(".epiNum")?.text() ?: a.text(),
                            url = a.absUrl("href"), date = el.selectFirst(".epidate")?.text() ?: "")
                    }
                    allEps.addAll(pageEps)
                }
                emit(allEps.reversed())
            } else {
                val episodes = doc.select(".eplister li").mapNotNull { el ->
                    val a = el.selectFirst("a") ?: return@mapNotNull null
                    com.example.data.models.Chapter(id = a.absUrl("href"), title = el.selectFirst(".epiNum")?.text() ?: a.text(),
                        url = a.absUrl("href"), date = el.selectFirst(".epidate")?.text() ?: "")
                }.reversed()
                emit(episodes)
            }
        } catch (e: CancellationException) { throw e
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { emit(emptyList()) }
    }


}
