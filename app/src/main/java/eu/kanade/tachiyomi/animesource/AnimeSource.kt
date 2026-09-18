package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video

interface AnimeSource {
    val id: Long
    val name: String
    val lang: String get() = ""

    fun fetchPopularAnime(page: Int): rx.Observable<AnimesPage>
    fun fetchLatestUpdates(page: Int): rx.Observable<AnimesPage>
    fun fetchSearchAnime(page: Int, query: String, filters: Any): rx.Observable<AnimesPage>
    fun fetchAnimeDetails(anime: SAnime): rx.Observable<SAnime>
    fun fetchEpisodeList(anime: SAnime): rx.Observable<List<SEpisode>>
    fun fetchVideoList(episode: SEpisode): rx.Observable<List<Video>>
}

data class AnimesPage(val animes: List<SAnime>, val hasNextPage: Boolean)
