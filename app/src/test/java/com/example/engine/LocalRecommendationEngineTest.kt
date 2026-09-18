package com.example.engine

import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.models.HistoryItem
import com.example.data.models.LibraryEntry
import com.example.engine.recommendation.LocalRecommendationEngine
import org.junit.Assert.*
import org.junit.Test

class LocalRecommendationEngineTest {

    @Test
    fun testGetUnfinishedHistoryFiltersStrictly() {
        val now = System.currentTimeMillis()
        val historyList = listOf(
            // Selesai 100% (progress = 1.0f) -> Harus DIABAIKAN
            HistoryItem(id = "1", title = "Manga Selesai", progress = 1.0f, timestamp = now - 100),
            // Belum mulai (progress = 0.0f) -> Harus DIABAIKAN
            HistoryItem(id = "2", title = "Manga Belum Mulai", progress = 0.0f, timestamp = now - 200),
            // Sedang berjalan 45% -> Harus DIAMBIL
            HistoryItem(id = "3", title = "Solo Leveling Ch 45", progress = 0.45f, timestamp = now - 300),
            // Sedang berjalan 80% -> Harus DIAMBIL
            HistoryItem(id = "4", title = "One Piece Ep 1080", progress = 0.80f, timestamp = now - 50),
            // Sedang berjalan 15% -> Harus DIAMBIL
            HistoryItem(id = "5", title = "Jujutsu Kaisen Ep 5", progress = 0.15f, timestamp = now - 400),
            // Sedang berjalan 90% -> Harus DIAMBIL
            HistoryItem(id = "6", title = "Frieren Ep 12", progress = 0.90f, timestamp = now - 20),
            // Sedang berjalan 50% -> Harus DIAMBIL
            HistoryItem(id = "7", title = "Chainsaw Man Ch 20", progress = 0.50f, timestamp = now - 10),
            // Sedang berjalan 30% -> Harus terpotong karena batas maks 5
            HistoryItem(id = "8", title = "Bleach Ep 1", progress = 0.30f, timestamp = now - 500)
        )

        val result = LocalRecommendationEngine.getUnfinishedHistory(historyList, maxCount = 5)

        assertEquals("Harus tepat mengembalikan maksimal 5 entri", 5, result.size)
        // Pastikan terurut berdasarkan timestamp DESC
        assertEquals("Chainsaw Man Ch 20", result[0].title)
        assertEquals("Frieren Ep 12", result[1].title)
        assertEquals("One Piece Ep 1080", result[2].title)
        assertEquals("Solo Leveling Ch 45", result[3].title)
        assertEquals("Jujutsu Kaisen Ep 5", result[4].title)

        // Verifikasi tidak ada yang bernilai 0f atau 1f
        assertTrue(result.all { it.progress > 0.0f && it.progress < 1.0f })
    }

    @Test
    fun testRecommendationScoringWithGenreAndKeyword() {
        val library = listOf(
            LibraryEntry(mangaId = "solo-leveling", title = "Solo Leveling Hunter", favorite = true, sourceId = "komikcast"),
            LibraryEntry(mangaId = "naruto", title = "Naruto Shippuden", favorite = true, sourceId = "otakudesu")
        )

        val history = listOf(
            HistoryItem(id = "solo-leveling", title = "Solo Leveling Hunter", type = ContentType.COMIC, progress = 0.7f)
        )

        val knownCatalog = listOf(
            ContentItem(id = "solo-leveling", title = "Solo Leveling Hunter", genres = listOf("Action", "Fantasy"))
        )

        val userProfile = LocalRecommendationEngine.buildUserProfile(library, history, knownCatalog)

        // Kandidat 1: Bergenre Action & Fantasy dengan kata kunci "Hunter"
        val candidate1 = ContentItem(
            id = "hunter-x-hunter",
            title = "Hunter X Hunter Adventure",
            genres = listOf("Action", "Fantasy"),
            type = ContentType.COMIC,
            sourceId = "komikcast"
        )

        // Kandidat 2: Genre Romance & Slice of Life tanpa kata kunci yang cocok
        val candidate2 = ContentItem(
            id = "horimiya",
            title = "Horimiya Romance",
            genres = listOf("Romance", "Slice of Life"),
            type = ContentType.COMIC,
            sourceId = "other"
        )

        val (score1, reason1) = LocalRecommendationEngine.calculateItemScore(candidate1, userProfile)
        val (score2, reason2) = LocalRecommendationEngine.calculateItemScore(candidate2, userProfile)

        assertTrue("Kandidat dengan genre dan kata kunci yang cocok harus memiliki skor jauh lebih tinggi", score1 > score2)
        assertTrue("Alasan rekomendasi harus menyebutkan genre atau kesamaan", reason1.contains("Action") || reason1.contains("Hunter") || reason1.contains("genre"))
    }

    @Test
    fun testRecommendationPenalizesFinishedItems() {
        val library = listOf(
            LibraryEntry(mangaId = "item-a", title = "Action Story", favorite = true)
        )
        val history = listOf(
            HistoryItem(id = "item-a", title = "Action Story", progress = 1.0f) // Selesai
        )
        val knownCatalog = listOf(
            ContentItem(id = "item-a", title = "Action Story", genres = listOf("Action"))
        )

        val userProfile = LocalRecommendationEngine.buildUserProfile(library, history, knownCatalog)

        val freshCandidate = ContentItem(id = "item-fresh", title = "Brand New Action Adventure", genres = listOf("Action"))
        val finishedCandidate = ContentItem(id = "item-a", title = "Action Story", genres = listOf("Action"))

        val (freshScore, _) = LocalRecommendationEngine.calculateItemScore(freshCandidate, userProfile)
        val (finishedScore, _) = LocalRecommendationEngine.calculateItemScore(finishedCandidate, userProfile)

        assertTrue("Item baru harus mendapatkan prioritas lebih tinggi daripada item yang sudah selesai dibaca", freshScore > finishedScore)
    }
}
