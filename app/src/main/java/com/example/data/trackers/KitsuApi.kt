package com.example.data.trackers

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Body
import retrofit2.http.Header
import com.squareup.moshi.JsonClass
import retrofit2.Response

@JsonClass(generateAdapter = true)
data class KitsuSearchResponse(
    val data: List<KitsuDataNode>
)

@JsonClass(generateAdapter = true)
data class KitsuDataNode(
    val id: String,
    val type: String
)

@JsonClass(generateAdapter = true)
data class KitsuLibraryEntryResponse(
    val data: List<KitsuLibraryEntryNode>
)

@JsonClass(generateAdapter = true)
data class KitsuLibraryEntryNode(
    val id: String
)

@JsonClass(generateAdapter = true)
data class KitsuLibraryEntryRequest(
    val data: KitsuLibraryEntryRequestData
)

@JsonClass(generateAdapter = true)
data class KitsuLibraryEntryRequestData(
    val type: String = "libraryEntries",
    val attributes: KitsuLibraryEntryAttributes,
    val relationships: KitsuLibraryEntryRelationships
)

@JsonClass(generateAdapter = true)
data class KitsuLibraryEntryUpdateRequest(
    val data: KitsuLibraryEntryUpdateRequestData
)

@JsonClass(generateAdapter = true)
data class KitsuLibraryEntryUpdateRequestData(
    val id: String,
    val type: String = "libraryEntries",
    val attributes: KitsuLibraryEntryAttributes
)

@JsonClass(generateAdapter = true)
data class KitsuLibraryEntryAttributes(
    val progress: Int,
    val status: String = "current"
)

@JsonClass(generateAdapter = true)
data class KitsuLibraryEntryRelationships(
    val user: KitsuRelationshipData,
    val media: KitsuRelationshipData
)

@JsonClass(generateAdapter = true)
data class KitsuRelationshipData(
    val data: KitsuDataNode
)

interface KitsuApi {
    @GET("anime")
    suspend fun searchAnime(
        @Query("filter[text]") query: String
    ): Response<KitsuSearchResponse>

    @GET("manga")
    suspend fun searchManga(
        @Query("filter[text]") query: String
    ): Response<KitsuSearchResponse>

    @GET("library-entries")
    suspend fun getLibraryEntry(
        @Header("Authorization") auth: String,
        @Query("filter[userId]") userId: String,
        @Query("filter[animeId]") animeId: String?,
        @Query("filter[mangaId]") mangaId: String?
    ): Response<KitsuLibraryEntryResponse>

    @POST("library-entries")
    suspend fun createLibraryEntry(
        @Header("Authorization") auth: String,
        @Body request: KitsuLibraryEntryRequest
    ): Response<Unit>

    @PATCH("library-entries/{id}")
    suspend fun updateLibraryEntry(
        @Header("Authorization") auth: String,
        @Path("id") entryId: String,
        @Body request: KitsuLibraryEntryUpdateRequest
    ): Response<Unit>
}
