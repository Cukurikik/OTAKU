package com.example.data.trackers

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AniSkipApi {
    @GET("v2/skip-times/{malId}/{episodeNumber}")
    suspend fun getSkipTimes(
        @Path("malId") malId: Long,
        @Path("episodeNumber") episodeNumber: Float,
        @Query("types") types: List<String> = listOf("op", "ed", "mixed-op", "mixed-ed", "recap"),
        @Query("episodeLength") episodeLength: Float = 0f
    ): AniSkipResponse
}

data class AniSkipResponse(
    val found: Boolean,
    val results: List<AniSkipResult>?
)

data class AniSkipResult(
    val interval: AniSkipInterval,
    val skipType: String,
    val skipId: String,
    val episodeLength: Float
)

data class AniSkipInterval(
    val startTime: Float,
    val endTime: Float
)

object AniSkipClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val api: AniSkipApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.aniskip.com/")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AniSkipApi::class.java)
    }
}
