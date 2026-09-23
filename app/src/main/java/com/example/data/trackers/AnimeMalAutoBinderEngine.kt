package com.example.data.trackers

import android.util.Log
import com.example.data.NetworkModule
import com.example.data.db.AnimeMalMappingDao
import com.example.data.models.AnimeMalMapping
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

@JsonClass(generateAdapter = true)
data class JikanSearchResponse(val data: List<JikanAnimeNode>?)

@JsonClass(generateAdapter = true)
data class JikanAnimeNode(
    val mal_id: Long,
    val title: String,
    val title_english: String?,
    val title_japanese: String?
)

object AnimeMalAutoBinderEngine {
    private const val TAG = "AnimeMalAutoBinderEngine"
    private const val MATCH_THRESHOLD = 0.70

    // In-memory cache for fast lookup without disk I/O
    private val memoryCache = ConcurrentHashMap<String, Long>()

    private val moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val jikanAdapter by lazy {
        moshi.adapter(JikanSearchResponse::class.java)
    }

    /**
     * Cleans raw title by stripping Indonesian tags, resolution tags, episode numbers, brackets, etc.
     */
    fun sanitizeTitle(rawTitle: String): String {
        if (rawTitle.isBlank()) return ""
        val regex = Regex(
            "(?i)(sub\\s*indo|subtitle\\s*indonesia|eng\\s*sub|raw|episode\\s*\\d+|ep\\.?\\s*\\d+|batch|bd|uncensored|uncen|hd|4k|720p|1080p|season\\s*\\d+|s\\d+|\\[.*?\\]|\\(.*?\\))"
        )
        val cleaned = rawTitle.replace(regex, " ").replace(Regex("\\s+"), " ").trim()
        return if (cleaned.length >= 2) cleaned else rawTitle.trim()
    }

    fun calculateLevenshteinDistance(a: String, b: String): Int {
        val lhs = a.lowercase().trim()
        val rhs = b.lowercase().trim()
        var cost = IntArray(lhs.length + 1) { it }
        var newCost = IntArray(lhs.length + 1)

        for (i in 1..rhs.length) {
            newCost[0] = i
            for (j in 1..lhs.length) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhs.length]
    }

    fun calculateSimilarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val distance = calculateLevenshteinDistance(a, b)
        val maxLength = max(a.length, b.length)
        if (maxLength == 0) return 1.0
        return 1.0 - (distance.toDouble() / maxLength.toDouble())
    }

    /**
     * Resolves MyAnimeList (MAL) ID for a given raw anime title using Room cache, memory cache, and Jikan API.
     */
    suspend fun resolveMalId(
        rawTitle: String,
        mappingDao: AnimeMalMappingDao? = null,
        client: OkHttpClient = NetworkModule.okHttpClient
    ): Long? = withContext(Dispatchers.IO) {
        val cleanTitle = sanitizeTitle(rawTitle)
        if (cleanTitle.isBlank()) return@withContext null

        // 1. Check in-memory cache
        memoryCache[cleanTitle]?.let {
            Log.d(TAG, "MAL ID found in memory cache for '$cleanTitle': $it")
            return@withContext it
        }

        // 2. Check Room Database cache
        if (mappingDao != null) {
            try {
                val cachedId = mappingDao.getMalIdByTitle(cleanTitle)
                if (cachedId != null && cachedId > 0) {
                    memoryCache[cleanTitle] = cachedId
                    Log.d(TAG, "MAL ID found in Room cache for '$cleanTitle': $cachedId")
                    return@withContext cachedId
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading Room cache: ${e.message}")
            }
        }

        // 3. Query Jikan API (Public MyAnimeList REST API)
        try {
            val encodedQuery = URLEncoder.encode(cleanTitle, "UTF-8")
            val jikanUrl = "https://api.jikan.moe/v4/anime?q=$encodedQuery&limit=5"
            val request = Request.Builder()
                .url(jikanUrl)
                .header("User-Agent", NetworkModule.userAgent)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNull_or_blank()) {
                        val parsed = jikanAdapter.fromJson(bodyStr)
                        val items = parsed?.data ?: emptyList()

                        var bestMatchId: Long? = null
                        var bestMatchTitle = ""
                        var bestScore = 0.0

                        for (item in items) {
                            val scoreOriginal = calculateSimilarity(cleanTitle, item.title)
                            val scoreEnglish = item.title_english?.let { calculateSimilarity(cleanTitle, it) } ?: 0.0
                            val maxScore = max(scoreOriginal, scoreEnglish)

                            if (maxScore > bestScore) {
                                bestScore = maxScore
                                bestMatchId = item.mal_id
                                bestMatchTitle = item.title
                            }
                        }

                        if (bestMatchId != null && bestScore >= MATCH_THRESHOLD) {
                            Log.i(TAG, "Resolved MAL ID via Jikan: '$cleanTitle' -> '$bestMatchTitle' (ID: $bestMatchId, Score: ${String.format("%.2f", bestScore)})")
                            memoryCache[cleanTitle] = bestMatchId

                            if (mappingDao != null) {
                                try {
                                    mappingDao.insertMapping(
                                        AnimeMalMapping(
                                            cleanTitle = cleanTitle,
                                            malId = bestMatchId,
                                            animeTitle = bestMatchTitle
                                        )
                                    )
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed saving mapping to Room: ${e.message}")
                                }
                            }
                            return@withContext bestMatchId
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Jikan API lookup failed for '$cleanTitle': ${e.message}")
        }

        // 4. Fallback to MyAnimeList Search API
        try {
            val encodedQuery = URLEncoder.encode(cleanTitle, "UTF-8")
            val malUrl = "https://api.myanimelist.net/v2/anime?q=$encodedQuery&limit=5"
            val request = Request.Builder()
                .url(malUrl)
                .header("X-MAL-CLIENT-ID", "6114d00ca681b7701d1e15fe11a4987e")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val adapter = moshi.adapter(MalResponse::class.java)
                    val data = adapter.fromJson(bodyStr)?.data ?: emptyList()

                    var bestMatchId: Long? = null
                    var bestMatchTitle = ""
                    var bestScore = 0.0

                    for (item in data) {
                        val node = item.node ?: continue
                        val score = calculateSimilarity(cleanTitle, node.title)
                        if (score > bestScore) {
                            bestScore = score
                            bestMatchId = node.id
                            bestMatchTitle = node.title
                        }
                    }

                    if (bestMatchId != null && bestScore >= MATCH_THRESHOLD) {
                        Log.i(TAG, "Resolved MAL ID via MAL Search: '$cleanTitle' -> '$bestMatchTitle' (ID: $bestMatchId, Score: ${String.format("%.2f", bestScore)})")
                        memoryCache[cleanTitle] = bestMatchId

                        if (mappingDao != null) {
                            try {
                                mappingDao.insertMapping(
                                    AnimeMalMapping(
                                        cleanTitle = cleanTitle,
                                        malId = bestMatchId,
                                        animeTitle = bestMatchTitle
                                    )
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed saving mapping to Room: ${e.message}")
                            }
                        }
                        return@withContext bestMatchId
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MAL Search lookup failed for '$cleanTitle': ${e.message}")
        }

        return@withContext null
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.isBlank()
    }
}
