package com.example.data.sources.direct

import com.example.data.models.ContentItem
import com.example.data.sources.VideoStreamInfo
import org.jsoup.nodes.Document

/** Direct scraper only. Anime/Drama sources must implement this contract.
 * No Mihon/Tachiyomi APK extension is accepted here.
 */
interface DirectScraperContract {
    val sourceId: String
    val sourceName: String
    val baseUrl: String

    suspend fun search(query: String, page: Int = 1): List<ScrapedCard>
    suspend fun recommendations(page: Int = 1): List<ScrapedCard>
    suspend fun detail(itemUrl: String): ScrapedDetail?
    suspend fun episodes(itemUrl: String): List<ScrapedEpisode>
    suspend fun resolveVideo(episodeUrl: String): VideoStreamInfo?
}

data class ScrapedCard(
    val id: String,
    val url: String,
    val title: String,
    val imageUrl: String = "",
    val typeLabel: String = ""
)

data class ScrapedDetail(
    val id: String,
    val url: String,
    val title: String,
    val imageUrl: String = "",
    val description: String = "",
    val genres: List<String> = emptyList(),
    val status: String = "",
    val year: String = "",
    val castList: List<String> = emptyList(),
    val subtitleStatus: String = "Sub Indo",
    val seasonNumber: Int? = null
)

data class ScrapedEpisode(
    val id: String,
    val url: String,
    val number: Double?,
    val title: String,
    val label: String = ""
)

fun interface HtmlDocumentParser {
    fun parse(document: Document): Any?
}
