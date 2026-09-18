package com.example.data.linkrot

import android.content.Context
import android.util.Log
import com.example.OtakuHubApp
import com.example.data.models.ChapterEntry
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.models.LibraryEntry
import com.example.data.sources.ContentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class AutoMigrationResult(
    val oldTitle: String,
    val newTitle: String,
    val fromSourceId: String,
    val toSourceId: String,
    val toSourceName: String,
    val oldMangaId: String,
    val newMangaId: String,
    val transferredChaptersCount: Int,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

data class MigrationCandidate(
    val sourceId: String,
    val sourceName: String,
    val mangaId: String,
    val title: String,
    val imageUrl: String,
    val similarityScore: Float,
    val chaptersCount: Int = 0,
    val latestChapterName: String? = null
)

object AutoMigrationEngine {

    private const val TAG = "AutoMigrationEngine"
    private const val MATCH_SCORE_THRESHOLD = 0.58f

    /**
     * Menghitung jarak Levenshtein antara dua string.
     */
    fun calculateLevenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        if (lhs == rhs) return 0
        if (lhs.isEmpty()) return rhs.length
        if (rhs.isEmpty()) return lhs.length
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1)
        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }

    /**
     * Menormalkan judul dengan membuang tag umum (mis. "[Sub Indo]", "(Batch)", tanda kurung dan karakter non-alfanumerik berlebih).
     */
    fun normalizeTitle(title: String): String {
        return title
            .replace(Regex("\\[.*?\\]|\\(.*?\\)"), "") // Buang teks dalam tanda kurung siku atau bulat
            .replace(Regex("(?i)(sub indo|subtitle indonesia|batch|tamat|end|season|s\\d+|bd|raw|color|warna)"), "")
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ") // Ganti tanda baca dengan spasi
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }

    /**
     * Menghitung skor kemiripan fuzzy antara dua judul (0.0f hingga 1.0f).
     * Mengkombinasikan Normalized Levenshtein Distance dan Token Jaccard Similarity.
     */
    fun calculateFuzzyScore(title1: String, title2: String): Float {
        val norm1 = normalizeTitle(title1)
        val norm2 = normalizeTitle(title2)
        if (norm1.isEmpty() && norm2.isEmpty()) return 1.0f
        if (norm1.isEmpty() || norm2.isEmpty()) return 0.0f
        if (norm1 == norm2) return 1.0f

        val maxLen = maxOf(norm1.length, norm2.length)
        val levDist = calculateLevenshteinDistance(norm1, norm2)
        val levScore = 1.0f - (levDist.toFloat() / maxLen.toFloat())

        // Token Jaccard
        val tokens1 = norm1.split(" ").filter { it.length > 1 }.toSet()
        val tokens2 = norm2.split(" ").filter { it.length > 1 }.toSet()
        val jaccardScore = if (tokens1.isNotEmpty() && tokens2.isNotEmpty()) {
            val intersection = tokens1.intersect(tokens2).size
            val union = tokens1.union(tokens2).size
            if (union > 0) intersection.toFloat() / union.toFloat() else 0f
        } else {
            0f
        }

        // Bobot gabungan: 60% Levenshtein + 40% Token Overlap
        return (levScore * 0.6f) + (jaccardScore * 0.4f)
    }

    fun getContentType(sourceId: String): ContentType {
        val id = sourceId.lowercase()
        return when {
            id == "torrent_source" -> ContentType.ANIME
            id.contains("anime") || id.contains("otaku") || id.contains("samehadaku") ||
                    id.contains("anichin") || id.contains("kura") || id.contains("kaze") ||
                    id.contains("anoboy") || id.contains("nime") -> ContentType.ANIME
            id.contains("drama") || id.contains("drakor") || id.contains("viki") ||
                    id.contains("licil") || id.contains("film") || id.contains("jucelebrity") -> ContentType.DRAMA
            else -> ContentType.COMIC
        }
    }

    /**
     * Mencari kandidat migrasi alternatif yang aktif dan sehat dari berbagai sumber sejenis.
     */
    suspend fun findCandidates(
        app: OtakuHubApp,
        currentSourceId: String,
        currentTitle: String,
        targetType: ContentType? = null
    ): List<MigrationCandidate> = withContext(Dispatchers.IO) {
        val repository = app.repository
        val type = targetType ?: getContentType(currentSourceId)
        val availableSources = repository.sources.filter {
            getContentType(it.sourceId) == type && it.sourceId != currentSourceId
        }
        if (availableSources.isEmpty()) return@withContext emptyList()

        val cleanedTitle = normalizeTitle(currentTitle).ifBlank { currentTitle }
        val concurrencyLimiter = Semaphore(3)

        val searchDeferreds = coroutineScope {
            availableSources.map { candidateSource ->
                async {
                    concurrencyLimiter.withPermit {
                        try {
                            withTimeoutOrNull(10_000L) {
                                val results = candidateSource.search(cleanedTitle, 1).firstOrNull() ?: emptyList()
                                results.map { item -> item to candidateSource }
                            } ?: emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }
            }
        }

        val allSearchResults = searchDeferreds.awaitAll().flatten()
        if (allSearchResults.isEmpty()) return@withContext emptyList()

        val scoredCandidates = allSearchResults.map { (item, src) ->
            val score = calculateFuzzyScore(currentTitle, item.title)
            Triple(item, src, score)
        }.filter { it.third >= MATCH_SCORE_THRESHOLD }
            .sortedByDescending { it.third }

        val candidates = mutableListOf<MigrationCandidate>()
        for ((candidateItem, candidateSource, score) in scoredCandidates.take(6)) {
            try {
                val chapters = withTimeoutOrNull(8_000L) {
                    candidateSource.getChapters(candidateItem.id).firstOrNull()
                } ?: emptyList()
                if (chapters.isNotEmpty()) {
                    candidates.add(
                        MigrationCandidate(
                            sourceId = candidateSource.sourceId,
                            sourceName = candidateSource.sourceName,
                            mangaId = candidateItem.id,
                            title = candidateItem.title,
                            imageUrl = candidateItem.imageUrl,
                            similarityScore = score,
                            chaptersCount = chapters.size,
                            latestChapterName = chapters.firstOrNull()?.title
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        candidates
    }

    /**
     * Mengeksekusi migrasi satu kandidat terpilih secara instan (One-Tap Migration).
     * Memindahkan riwayat baca/tonton, bookmark, dan memetakan bab/episode lama ke baru.
     */
    suspend fun executeMigrationCandidate(
        app: OtakuHubApp,
        oldSourceId: String,
        oldMangaId: String,
        oldTitle: String,
        candidate: MigrationCandidate,
        profileId: Long = com.example.data.ProfileManager.currentProfileIdValue
    ): AutoMigrationResult = withContext(Dispatchers.IO) {
        val db = app.database
        val repository = app.repository
        val matchedSource = repository.sources.find { it.sourceId == candidate.sourceId }
        if (matchedSource == null) {
            return@withContext AutoMigrationResult(
                oldTitle = oldTitle,
                newTitle = candidate.title,
                fromSourceId = oldSourceId,
                toSourceId = candidate.sourceId,
                toSourceName = candidate.sourceName,
                oldMangaId = oldMangaId,
                newMangaId = candidate.mangaId,
                transferredChaptersCount = 0,
                isSuccess = false,
                errorMessage = "Sumber baru [${candidate.sourceId}] tidak ditemukan."
            )
        }

        val newChaptersList = try {
            withTimeoutOrNull(12_000L) {
                matchedSource.getChapters(candidate.mangaId).firstOrNull()
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (newChaptersList.isEmpty()) {
            return@withContext AutoMigrationResult(
                oldTitle = oldTitle,
                newTitle = candidate.title,
                fromSourceId = oldSourceId,
                toSourceId = candidate.sourceId,
                toSourceName = candidate.sourceName,
                oldMangaId = oldMangaId,
                newMangaId = candidate.mangaId,
                transferredChaptersCount = 0,
                isSuccess = false,
                errorMessage = "Gagal mengambil daftar bab/episode dari sumber baru."
            )
        }

        val oldChapters = db.chapterDao().getChaptersList(profileId, oldSourceId, oldMangaId)
        val existingLibraryEntry = db.libraryDao().getLibraryEntry(profileId, oldSourceId, oldMangaId)

        val newChapterEntities = newChaptersList.mapIndexed { index, newChap ->
            val numStr = newChap.title.replace(Regex("[^0-9.]"), "")
            val newNum = numStr.toFloatOrNull() ?: (newChaptersList.size - index).toFloat()

            val oldChap = oldChapters.find {
                it.number == newNum || it.name.equals(newChap.title, ignoreCase = true)
            }

            ChapterEntry(
                sourceId = candidate.sourceId,
                mangaId = candidate.mangaId,
                chapterId = newChap.id,
                name = newChap.title,
                url = newChap.url,
                dateUpload = null,
                number = newNum,
                read = oldChap?.read ?: false,
                bookmark = oldChap?.bookmark ?: false,
                lastPageRead = oldChap?.lastPageRead ?: 0,
                lastReadAt = oldChap?.lastReadAt,
                downloaded = false,
                profileId = profileId
            )
        }

        try {
            if (existingLibraryEntry != null) {
                val unreadCount = newChapterEntities.count { !it.read }
                val updatedLibraryEntry = LibraryEntry(
                    sourceId = candidate.sourceId,
                    mangaId = candidate.mangaId,
                    title = candidate.title.ifBlank { existingLibraryEntry.title },
                    thumbnailUrl = candidate.imageUrl.ifBlank { existingLibraryEntry.thumbnailUrl },
                    addedAt = existingLibraryEntry.addedAt,
                    lastUpdatedAt = System.currentTimeMillis(),
                    favorite = true,
                    categoryId = existingLibraryEntry.categoryId,
                    status = existingLibraryEntry.status,
                    unreadCount = unreadCount,
                    profileId = profileId
                )
                db.libraryDao().deleteLibraryEntry(profileId, oldSourceId, oldMangaId)
                db.libraryDao().insertLibraryEntry(updatedLibraryEntry)
            }

            db.chapterDao().insertChapters(newChapterEntities)
            if (oldChapters.isNotEmpty()) {
                db.chapterDao().deleteChapters(oldChapters)
            }

            val histories = db.historyDao().getAllHistorySync(profileId)
            val oldHistory = histories.find { it.id == oldMangaId && it.sourceId == oldSourceId }
            if (oldHistory != null) {
                val newHistory = oldHistory.copy(
                    id = candidate.mangaId,
                    sourceId = candidate.sourceId,
                    source = candidate.sourceName,
                    title = candidate.title.ifBlank { oldHistory.title },
                    imageUrl = candidate.imageUrl.ifBlank { oldHistory.imageUrl },
                    lastUpdated = System.currentTimeMillis()
                )
                db.historyDao().deleteHistoryById(profileId, oldHistory.id)
                db.historyDao().insertHistory(newHistory)
            }

            // Migrate Tracks in TrackDao if any
            val tracks = db.trackDao().getTracksForMangaSync(profileId, oldMangaId)
            if (tracks.isNotEmpty()) {
                tracks.forEach { track ->
                    db.trackDao().deleteTrack(track.id)
                    db.trackDao().insertTrack(track.copy(mangaId = candidate.mangaId))
                }
            }

            LinkRotTracker.getInstance(app).clearEntry(oldSourceId, oldMangaId)
            LinkRotTracker.getInstance(app).clearEntry(candidate.sourceId, candidate.mangaId)

            Log.i(TAG, "Berhasil One-Tap Migration '${oldTitle}' ke '${candidate.title}' [${candidate.sourceId}]")

            AutoMigrationResult(
                oldTitle = oldTitle,
                newTitle = candidate.title,
                fromSourceId = oldSourceId,
                toSourceId = candidate.sourceId,
                toSourceName = candidate.sourceName,
                oldMangaId = oldMangaId,
                newMangaId = candidate.mangaId,
                transferredChaptersCount = newChapterEntities.size,
                isSuccess = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memproses migrasi: ${e.message}", e)
            AutoMigrationResult(
                oldTitle = oldTitle,
                newTitle = candidate.title,
                fromSourceId = oldSourceId,
                toSourceId = candidate.sourceId,
                toSourceName = candidate.sourceName,
                oldMangaId = oldMangaId,
                newMangaId = candidate.mangaId,
                transferredChaptersCount = 0,
                isSuccess = false,
                errorMessage = "Gagal memproses migrasi: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Mencari bab/episode pada daftar baru yang paling cocok dengan bab lama berdasarkan nomor atau judul.
     */
    fun findMatchingChapter(
        newChapters: List<ChapterEntry>,
        targetNumber: Float?,
        targetName: String? = null
    ): ChapterEntry? {
        if (newChapters.isEmpty()) return null
        if (targetNumber != null) {
            val exactNum = newChapters.find { it.number == targetNumber }
            if (exactNum != null) return exactNum
        }
        if (!targetName.isNullOrBlank()) {
            val exactName = newChapters.find { it.name.equals(targetName, ignoreCase = true) }
            if (exactName != null) return exactName
            val targetDigits = targetName.filter { it.isDigit() }.toIntOrNull()
            if (targetDigits != null) {
                val digitMatch = newChapters.find { it.name.filter { c -> c.isDigit() }.toIntOrNull() == targetDigits }
                if (digitMatch != null) return digitMatch
            }
        }
        return newChapters.firstOrNull()
    }

    /**
     * Melakukan proses migrasi otomatis untuk satu LibraryEntry yang mengalami Link Rot.
     */
    suspend fun resolveAndMigrate(
        app: OtakuHubApp,
        entry: LibraryEntry,
        profileId: Long = com.example.data.ProfileManager.currentProfileIdValue
    ): AutoMigrationResult = withContext(Dispatchers.IO) {
        val db = app.database
        val repository = app.repository
        val targetType = getContentType(entry.sourceId)

        // Cari kandidat sumber aktif yang memiliki tipe konten serupa dan bukan sumber asal
        val availableSources = repository.sources.filter {
            getContentType(it.sourceId) == targetType && it.sourceId != entry.sourceId
        }

        if (availableSources.isEmpty()) {
            return@withContext AutoMigrationResult(
                oldTitle = entry.title,
                newTitle = entry.title,
                fromSourceId = entry.sourceId,
                toSourceId = "",
                toSourceName = "",
                oldMangaId = entry.mangaId,
                newMangaId = "",
                transferredChaptersCount = 0,
                isSuccess = false,
                errorMessage = "Tidak ada sumber cadangan aktif untuk tipe $targetType"
            )
        }

        val cleanedTitle = normalizeTitle(entry.title).ifBlank { entry.title }
        val concurrencyLimiter = Semaphore(3)

        // Lakukan pencarian secara paralel di kandidat sumber dengan timeout per sumber
        val searchDeferreds = coroutineScope {
            availableSources.map { candidateSource ->
                async {
                    concurrencyLimiter.withPermit {
                        try {
                            withTimeoutOrNull(12_000L) {
                                val results = candidateSource.search(cleanedTitle, 1).firstOrNull() ?: emptyList()
                                results.map { item -> item to candidateSource }
                            } ?: emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }
            }
        }

        val allSearchResults = searchDeferreds.awaitAll().flatten()
        if (allSearchResults.isEmpty()) {
            return@withContext AutoMigrationResult(
                oldTitle = entry.title,
                newTitle = entry.title,
                fromSourceId = entry.sourceId,
                toSourceId = "",
                toSourceName = "",
                oldMangaId = entry.mangaId,
                newMangaId = "",
                transferredChaptersCount = 0,
                isSuccess = false,
                errorMessage = "Tidak ditemukan kecocokan judul di sumber cadangan"
            )
        }

        // Urutkan kandidat berdasarkan skor kemiripan tertinggi
        val scoredCandidates = allSearchResults.map { (item, src) ->
            val score = calculateFuzzyScore(entry.title, item.title)
            Triple(item, src, score)
        }.filter { it.third >= MATCH_SCORE_THRESHOLD }
            .sortedByDescending { it.third }

        if (scoredCandidates.isEmpty()) {
            return@withContext AutoMigrationResult(
                oldTitle = entry.title,
                newTitle = entry.title,
                fromSourceId = entry.sourceId,
                toSourceId = "",
                toSourceName = "",
                oldMangaId = entry.mangaId,
                newMangaId = "",
                transferredChaptersCount = 0,
                isSuccess = false,
                errorMessage = "Skor kemiripan judul di bawah ambang batas ($MATCH_SCORE_THRESHOLD)"
            )
        }

        // Coba kandidat teratas dan pastikan chapters/episodenya dapat diambil
        var matchedCandidate: ContentItem? = null
        var matchedSource: ContentSource? = null
        var newChaptersList: List<com.example.data.models.Chapter> = emptyList()

        for ((candidateItem, candidateSource, _) in scoredCandidates) {
            try {
                val chapters = withTimeoutOrNull(12_000L) {
                    candidateSource.getChapters(candidateItem.id).firstOrNull()
                }
                if (!chapters.isNullOrEmpty()) {
                    matchedCandidate = candidateItem
                    matchedSource = candidateSource
                    newChaptersList = chapters
                    break
                }
            } catch (_: Exception) {
                // Lanjut ke kandidat berikutnya jika sumber ini gagal fetch chapter
            }
        }

        if (matchedCandidate == null || matchedSource == null || newChaptersList.isEmpty()) {
            return@withContext AutoMigrationResult(
                oldTitle = entry.title,
                newTitle = entry.title,
                fromSourceId = entry.sourceId,
                toSourceId = "",
                toSourceName = "",
                oldMangaId = entry.mangaId,
                newMangaId = "",
                transferredChaptersCount = 0,
                isSuccess = false,
                errorMessage = "Gagal mengambil daftar chapter/episode dari kandidat sumber cadangan"
            )
        }

        // Ambil data chapter lama untuk memetakan progres baca & bookmark
        val oldChapters = db.chapterDao().getChaptersList(profileId, entry.sourceId, entry.mangaId)

        val newChapterEntities = newChaptersList.mapIndexed { index, newChap ->
            val numStr = newChap.title.replace(Regex("[^0-9.]"), "")
            val newNum = numStr.toFloatOrNull() ?: (newChaptersList.size - index).toFloat()

            val oldChap = oldChapters.find {
                it.number == newNum || it.name.equals(newChap.title, ignoreCase = true)
            }

            ChapterEntry(
                sourceId = matchedSource.sourceId,
                mangaId = matchedCandidate.id,
                chapterId = newChap.id,
                name = newChap.title,
                url = newChap.url,
                dateUpload = null,
                number = newNum,
                read = oldChap?.read ?: false,
                bookmark = oldChap?.bookmark ?: false,
                lastPageRead = oldChap?.lastPageRead ?: 0,
                lastReadAt = oldChap?.lastReadAt,
                downloaded = false,
                profileId = profileId
            )
        }

        val unreadCount = newChapterEntities.count { !it.read }
        val updatedLibraryEntry = LibraryEntry(
            sourceId = matchedSource.sourceId,
            mangaId = matchedCandidate.id,
            title = matchedCandidate.title.ifBlank { entry.title },
            thumbnailUrl = matchedCandidate.imageUrl.ifBlank { entry.thumbnailUrl },
            addedAt = entry.addedAt,
            lastUpdatedAt = System.currentTimeMillis(),
            favorite = true,
            categoryId = entry.categoryId,
            status = entry.status,
            unreadCount = unreadCount,
            profileId = profileId
        )

        try {
            // 1. Masukkan data baru ke database Room
            db.libraryDao().insertLibraryEntry(updatedLibraryEntry)
            db.chapterDao().insertChapters(newChapterEntities)

            // 2. Transfer riwayat di HistoryDao
            val histories = db.historyDao().getAllHistorySync(profileId)
            val oldHistory = histories.find { it.id == entry.mangaId && it.sourceId == entry.sourceId }
            if (oldHistory != null) {
                val newHistory = oldHistory.copy(
                    id = matchedCandidate.id,
                    sourceId = matchedSource.sourceId,
                    source = matchedSource.sourceName,
                    title = matchedCandidate.title.ifBlank { oldHistory.title },
                    imageUrl = matchedCandidate.imageUrl.ifBlank { oldHistory.imageUrl },
                    lastUpdated = System.currentTimeMillis()
                )
                db.historyDao().deleteHistoryById(profileId, oldHistory.id)
                db.historyDao().insertHistory(newHistory)
            }

            // 3. Hapus data lama yang mengalami Link Rot
            db.libraryDao().deleteLibraryEntry(profileId, entry.sourceId, entry.mangaId)
            db.chapterDao().deleteChapters(oldChapters)

            // 4. Bersihkan catatan kegagalan di LinkRotTracker
            LinkRotTracker.getInstance(app).clearEntry(entry.sourceId, entry.mangaId)
            LinkRotTracker.getInstance(app).clearEntry(matchedSource.sourceId, matchedCandidate.id)

            Log.i(TAG, "Berhasil memigrasikan '${entry.title}' dari [${entry.sourceId}] ke [${matchedSource.sourceId}]")

            AutoMigrationResult(
                oldTitle = entry.title,
                newTitle = matchedCandidate.title,
                fromSourceId = entry.sourceId,
                toSourceId = matchedSource.sourceId,
                toSourceName = matchedSource.sourceName,
                oldMangaId = entry.mangaId,
                newMangaId = matchedCandidate.id,
                transferredChaptersCount = newChapterEntities.size,
                isSuccess = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menulis hasil migrasi ke database: ${e.message}", e)
            AutoMigrationResult(
                oldTitle = entry.title,
                newTitle = matchedCandidate.title,
                fromSourceId = entry.sourceId,
                toSourceId = matchedSource.sourceId,
                toSourceName = matchedSource.sourceName,
                oldMangaId = entry.mangaId,
                newMangaId = matchedCandidate.id,
                transferredChaptersCount = 0,
                isSuccess = false,
                errorMessage = "Database write error: ${e.localizedMessage}"
            )
        }
    }
}
