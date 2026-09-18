package com.example.data.sources.mangadex

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MangaDexApi {
    @GET("manga")
    suspend fun getMangaList(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("title") title: String? = null,
        @Query("includes[]") includes: List<String> = listOf("cover_art"),
        @Query("hasAvailableChapters") hasAvailableChapters: Boolean = true,
        @Query("availableTranslatedLanguage[]") languages: List<String> = listOf("en")
    ): MangaListResponse

    @GET("manga/{id}")
    suspend fun getManga(
        @Path("id") mangaId: String,
        @Query("includes[]") includes: List<String> = listOf("cover_art")
    ): MangaSingleResponse

    @GET("manga/{id}/feed")
    suspend fun getMangaFeed(
        @Path("id") mangaId: String,
        @Query("translatedLanguage[]") translatedLanguage: List<String> = listOf("id", "en"),
        @Query("order[chapter]") order: String = "desc",
        @Query("limit") limit: Int = 100
    ): ChapterFeedResponse

    @GET("at-home/server/{chapterId}")
    suspend fun getAtHomeServer(
        @Path("chapterId") chapterId: String
    ): AtHomeResponse
}

@JsonClass(generateAdapter = true)
data class MangaSingleResponse(val data: MangaData?)

@JsonClass(generateAdapter = true)
data class MangaListResponse(val data: List<MangaData>?)

@JsonClass(generateAdapter = true)
data class MangaData(val id: String, val attributes: MangaAttributes?, val relationships: List<Relationship>?)

@JsonClass(generateAdapter = true)
data class MangaAttributes(val title: Map<String, String>?, val description: Map<String, String>?, val status: String?)

@JsonClass(generateAdapter = true)
data class Relationship(val id: String?, val type: String?, val attributes: RelationshipAttributes?)

@JsonClass(generateAdapter = true)
data class RelationshipAttributes(val fileName: String?)

@JsonClass(generateAdapter = true)
data class ChapterFeedResponse(val data: List<ChapterData>?)

@JsonClass(generateAdapter = true)
data class ChapterData(val id: String, val attributes: ChapterAttributes?)

@JsonClass(generateAdapter = true)
data class ChapterAttributes(
    val chapter: String?, 
    val title: String?, 
    val publishAt: String?,
    val pages: Int?,
    val externalUrl: String?
)

@JsonClass(generateAdapter = true)
data class AtHomeResponse(val baseUrl: String?, val chapter: AtHomeChapter?)

@JsonClass(generateAdapter = true)
data class AtHomeChapter(val hash: String?, val data: List<String>?, val dataSaver: List<String>?)
