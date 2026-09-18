package com.example.engine.recommendation

import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.models.HistoryItem
import com.example.data.models.LibraryEntry
import java.util.Locale

/**
 * Result model untuk rekomendasi media dengan scoring dan penjelasan relevansi.
 */
data class RecommendationResult(
    val item: ContentItem,
    val score: Float,
    val reason: String,
    val matchedTags: List<String> = emptyList()
)

/**
 * Profil preferensi pengguna yang diekstraksi dari Library (favorit) dan History tonton/baca.
 */
data class UserPreferenceProfile(
    val genreWeights: Map<String, Float> = emptyMap(),
    val favoriteKeywords: Set<String> = emptySet(),
    val favoriteTitles: List<String> = emptyList(),
    val sourceAffinities: Map<String, Float> = emptyMap(),
    val preferredType: ContentType? = null,
    val existingLibraryIds: Set<String> = emptySet(),
    val finishedItemIds: Set<String> = emptySet()
)

/**
 * LocalRecommendationEngine:
 * Mesin Collaborative Filtering dan Content-Based Matching lokal (0 backend server)
 * yang menganalisis riwayat dan pustaka pengguna untuk menghasilkan skor relevansi dan
 * menyaring daftar resume yang belum selesai.
 */
object LocalRecommendationEngine {

    const val WEIGHT_GENRE = 2.5f
    const val WEIGHT_KEYWORD = 1.8f
    const val WEIGHT_TYPE = 1.5f
    const val WEIGHT_SOURCE = 1.2f
    const val WEIGHT_NOVELTY = 1.0f

    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "of", "to", "in", "for", "with",
        "on", "at", "by", "season", "episode", "chapter", "ch", "ep",
        "dan", "di", "ke", "dari", "yang", "ini", "itu", "untuk"
    )

    /**
     * Memfilter 5 entri riwayat terakhir yang belum selesai ditonton atau dibaca
     * Batasan: 0% < Progres < 100%
     */
    fun getUnfinishedHistory(historyList: List<HistoryItem>, maxCount: Int = 5): List<HistoryItem> {
        return historyList
            .filter { it.progress > 0.0f && it.progress < 1.0f }
            .sortedByDescending { it.timestamp }
            .take(maxCount)
    }

    /**
     * Membangun profil preferensi pengguna berdasarkan library (favorit) dan history.
     */
    fun buildUserProfile(
        libraryEntries: List<LibraryEntry>,
        historyItems: List<HistoryItem>,
        knownCatalogItems: List<ContentItem> = emptyList()
    ): UserPreferenceProfile {
        val genreFrequency = mutableMapOf<String, Float>()
        val sourceFrequency = mutableMapOf<String, Float>()
        val keywords = mutableSetOf<String>()
        val favoriteTitles = mutableListOf<String>()
        val existingLibraryIds = libraryEntries.map { it.mangaId.lowercase() }.toSet()
        val finishedItemIds = historyItems.filter { it.progress >= 1.0f }.map { it.id.lowercase() }.toSet()

        // 1. Ekstraksi dari Library (Bobot tinggi karena eksplisit difavoritkan)
        libraryEntries.forEach { entry ->
            if (entry.favorite) {
                favoriteTitles.add(entry.title)
                extractKeywords(entry.title).forEach { keywords.add(it) }
                sourceFrequency[entry.sourceId] = (sourceFrequency[entry.sourceId] ?: 0f) + 2.0f
            }
        }

        // 2. Ekstraksi dari History tonton / baca
        val typeCount = mutableMapOf<ContentType, Int>()
        historyItems.forEach { history ->
            typeCount[history.type] = (typeCount[history.type] ?: 0) + 1
            sourceFrequency[history.sourceId] = (sourceFrequency[history.sourceId] ?: 0f) + 1.0f
            extractKeywords(history.title).forEach { keywords.add(it) }
        }

        // 3. Ekstraksi genre dari item katalog yang cocok dengan item library/history
        val idToCatalog = knownCatalogItems.associateBy { it.id.lowercase() }
        libraryEntries.forEach { entry ->
            val catalogMatch = idToCatalog[entry.mangaId.lowercase()]
            catalogMatch?.genres?.forEach { g ->
                val normalized = g.trim().lowercase(Locale.ROOT)
                if (normalized.isNotBlank()) {
                    genreFrequency[normalized] = (genreFrequency[normalized] ?: 0f) + 3.0f
                }
            }
        }

        historyItems.forEach { hist ->
            val catalogMatch = idToCatalog[hist.id.lowercase()]
            catalogMatch?.genres?.forEach { g ->
                val normalized = g.trim().lowercase(Locale.ROOT)
                if (normalized.isNotBlank()) {
                    genreFrequency[normalized] = (genreFrequency[normalized] ?: 0f) + 1.5f
                }
            }
        }

        val preferredType = typeCount.maxByOrNull { it.value }?.key

        return UserPreferenceProfile(
            genreWeights = genreFrequency,
            favoriteKeywords = keywords,
            favoriteTitles = favoriteTitles,
            sourceAffinities = sourceFrequency,
            preferredType = preferredType,
            existingLibraryIds = existingLibraryIds,
            finishedItemIds = finishedItemIds
        )
    }

    /**
     * Menghitung skor kecocokan satu kandidat ContentItem berdasarkan profil preferensi pengguna.
     * Formula: Score(item) = Sum(w_i * Match(Meta_item, Fav_user))
     */
    fun calculateItemScore(
        item: ContentItem,
        profile: UserPreferenceProfile
    ): Pair<Float, String> {
        // Jika sudah ditamatkan atau sudah ada di library, kurangi skor agar memprioritaskan penemuan baru
        val isFinished = profile.finishedItemIds.contains(item.id.lowercase())
        val isInLibrary = profile.existingLibraryIds.contains(item.id.lowercase())

        var score = 0f
        val matchedReasons = mutableListOf<String>()
        val matchedGenres = mutableListOf<String>()

        // 1. Genre Matching (Bobot 2.5)
        item.genres.forEach { genre ->
            val norm = genre.trim().lowercase(Locale.ROOT)
            val weight = profile.genreWeights[norm]
            if (weight != null && weight > 0f) {
                score += weight * WEIGHT_GENRE
                matchedGenres.add(genre)
            }
        }

        if (matchedGenres.isNotEmpty()) {
            val topMatched = matchedGenres.take(2).joinToString(", ")
            matchedReasons.add("Cocok dengan minat genre $topMatched")
        }

        // 2. Keyword & Title Matching (Bobot 1.8)
        val itemKeywords = extractKeywords(item.title)
        val sharedKeywords = itemKeywords.intersect(profile.favoriteKeywords)
        if (sharedKeywords.isNotEmpty()) {
            score += sharedKeywords.size * 5.0f * WEIGHT_KEYWORD
            matchedReasons.add("Tema serupa: ${sharedKeywords.first().replaceFirstChar { it.uppercase() }}")
        }

        // 3. Content Type Alignment (Bobot 1.5)
        if (profile.preferredType != null && item.type == profile.preferredType) {
            score += 10.0f * WEIGHT_TYPE
        }

        // 4. Source Affinity (Bobot 1.2)
        val srcAffinity = profile.sourceAffinities[item.sourceId] ?: 0f
        if (srcAffinity > 0f) {
            score += (srcAffinity.coerceAtMost(5f)) * WEIGHT_SOURCE
        }

        // 5. Freshness / Novelty Boost (Bobot 1.0)
        if (System.currentTimeMillis() - item.lastUpdated < 7 * 24 * 60 * 60 * 1000L) {
            score += 5.0f * WEIGHT_NOVELTY
        }

        // Penalti untuk item yang sudah dibaca habis
        if (isFinished) {
            score -= 50f
        } else if (isInLibrary) {
            score -= 20f
        }

        val finalReason = when {
            matchedReasons.isNotEmpty() -> matchedReasons.first()
            profile.favoriteTitles.isNotEmpty() -> "Karena Anda menyukai ${profile.favoriteTitles.first()}"
            item.genres.isNotEmpty() -> "Populer di genre ${item.genres.first()}"
            else -> "Rekomendasi pilihan untuk Anda"
        }

        return Pair(score, finalReason)
    }

    /**
     * Mengurutkan dan menghasilkan rekomendasi terbaik dari sekumpulan kandidat katalog.
     */
    fun generateRecommendations(
        candidates: List<ContentItem>,
        profile: UserPreferenceProfile,
        limit: Int = 10
    ): List<RecommendationResult> {
        if (candidates.isEmpty()) return emptyList()

        return candidates
            .map { item ->
                val (score, reason) = calculateItemScore(item, profile)
                val matched = item.genres.filter { g ->
                    profile.genreWeights.containsKey(g.trim().lowercase(Locale.ROOT))
                }
                RecommendationResult(
                    item = item,
                    score = score,
                    reason = reason,
                    matchedTags = matched
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun extractKeywords(text: String): Set<String> {
        return text.lowercase(Locale.ROOT)
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 2 && !STOP_WORDS.contains(it) }
            .toSet()
    }
}
