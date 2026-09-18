package com.example.data.sources.core

interface ContentSource {
    val id: String
    val name: String
    val baseUrl: String

    suspend fun getHome(): SourceResult<HomePage>
    
    suspend fun search(
        query: String,
        page: Int
    ): SourceResult<SearchPage>

    suspend fun getPopular(
        page: Int
    ): SourceResult<BrowsePage>

    suspend fun getLatest(
        page: Int
    ): SourceResult<BrowsePage>

    suspend fun getDetail(
        url: String
    ): SourceResult<ComicDetail>

    suspend fun getChapters(
        url: String
    ): SourceResult<List<Chapter>>

    suspend fun getChapterPages(
        chapter: Chapter
    ): SourceResult<List<PageImage>>
}

interface AnimeSource {
    val id: String
    val name: String
    val baseUrl: String

    suspend fun getHome(): SourceResult<AnimeHome>
    
    suspend fun search(
        query: String,
        page: Int
    ): SourceResult<AnimeSearchPage>

    suspend fun getDetail(
        url: String
    ): SourceResult<AnimeDetail>

    suspend fun getEpisodes(
        url: String
    ): SourceResult<List<Episode>>

    suspend fun getStreams(
        episode: Episode
    ): SourceResult<List<VideoStream>>
}

interface DramaSource {
    val id: String
    val name: String
    val baseUrl: String

    suspend fun search(
        query: String,
        page: Int
    ): SourceResult<DramaSearchPage>

    suspend fun getDetail(
        url: String
    ): SourceResult<DramaDetail>

    suspend fun getEpisodes(
        url: String
    ): SourceResult<List<DramaEpisode>>

    suspend fun getStreams(
        episode: DramaEpisode
    ): SourceResult<List<VideoStream>>
}
