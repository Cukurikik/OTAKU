package com.example.data.sources.anime.specific

import android.content.Context
import com.example.data.models.Chapter
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.templates.AnimeScraperTemplate
import com.example.network.interceptor.VideoUrlInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jsoup.Jsoup
import java.net.URLEncoder

class KuraAnimeSource(context: Context) : AnimeScraperTemplate(context) {
    override val sourceId: String = "kuraanime"
    override val sourceName: String = "KuraAnime"
    override val baseUrl: String = "https://v19.kuramanime.ing"

    override fun searchUrl(query: String, page: Int): String =
        "${currentBaseUrl()}/anime?search=${URLEncoder.encode(query, "UTF-8")}&order_by=latest" + if (page > 1) "&page=$page" else ""

    override val selectors = AnimeSelectors(
        card = ".product__sidebar__view__item, .product__item",
        title = ".anime__details__title h3, h5, .sidebar-title-h5",
        link = "a",
        image = ".set-bg",
        description = ".synopsis, .desc, .anime__details__text p",
        genres = ".genres a, .genre a, .anime__details__widget ul li:contains(Genre) a",
        episode = ".episodelist li, .eplister li",
        episodeTitle = ".epl-title, .ep-title",
        episodeDate = ".date, time",
        searchCard = "a:has(.product__sidebar__view__item), a:has(.product__item), .product__sidebar__view__item, .product__item"
    )

    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        try {
            val doc = fetcher.fetchHtml(itemId)
            val episodeListsElem = doc.selectFirst("#episodeLists")
            val dataContent = episodeListsElem?.attr("data-content")
            if (!dataContent.isNullOrEmpty()) {
                val epDoc = Jsoup.parse(dataContent)
                val chapters = epDoc.select("a").mapNotNull { a ->
                    val url = a.absUrl("href")
                    val title = a.text().trim()
                    if (url.isNotEmpty()) {
                        Chapter(id = url, title = title, url = url)
                    } else null
                }.reversed()
                emit(chapters)
            } else {
                emit(emptyList())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override fun getVideoStream(episodeId: String): Flow<VideoStreamInfo> = flow {
        val interceptor = VideoUrlInterceptor(context)
        val info = interceptor.interceptVideo(episodeId, episodeId)
        if (info != null && info.qualities.isNotEmpty()) {
            emit(info)
            return@flow
        }
        val streamUrl = interceptor.interceptStreamUrl(episodeId)
        if (!streamUrl.isNullOrBlank()) {
            emit(
                VideoStreamInfo(
                    episodeId = episodeId,
                    embedUrl = streamUrl,
                    qualities = listOf(VideoQuality("Auto", streamUrl, isM3U8 = streamUrl.contains(".m3u8"))),
                    headers = mapOf("Referer" to episodeId)
                )
            )
            return@flow
        }
        throw java.io.IOException("Gagal mengekstrak stream video dari $episodeId")
    }
}
