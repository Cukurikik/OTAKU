package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.ChapterDao
import com.example.data.db.HistoryDao
import com.example.data.db.LibraryDao
import com.example.data.models.ChapterEntry
import com.example.data.models.ContentType
import com.example.data.models.HistoryItem
import com.example.data.models.LibraryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

enum class StatTimeRange(val label: String) {
    LAST_7_DAYS("7 Hari"),
    LAST_30_DAYS("30 Hari"),
    ALL_TIME("Semua Waktu")
}

enum class StatMediaTab(val label: String) {
    ALL("Semua"),
    COMIC("Komik"),
    ANIME("Anime"),
    DRAMA("Drama")
}

data class DailyActivityPoint(
    val dayLabel: String,
    val dateString: String,
    val count: Int,
    val timestamp: Long
)

data class SourceUsageStat(
    val sourceId: String,
    val displayName: String,
    val mediaType: String,
    val count: Int,
    val percentage: Float
)

data class HourlyActivityStat(
    val hourLabel: String,
    val count: Int
)

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val selectedTimeRange: StatTimeRange = StatTimeRange.LAST_7_DAYS,
    val selectedMediaTab: StatMediaTab = StatMediaTab.ALL,

    // Overview Totals
    val totalWatchTimeMinutes: Int = 0,
    val totalChaptersRead: Int = 0,
    val totalAnimeEpisodesWatched: Int = 0,
    val totalDramaEpisodesWatched: Int = 0,
    val totalCompletedSeries: Int = 0,
    val totalInProgressSeries: Int = 0,
    val totalLibraryCount: Int = 0,
    val currentStreakDays: Int = 0,
    val totalHistoryEntries: Int = 0,

    // Media Distribution (Percentages 0.0 - 1.0)
    val comicDistributionRatio: Float = 0f,
    val animeDistributionRatio: Float = 0f,
    val dramaDistributionRatio: Float = 0f,
    val comicTotalEntries: Int = 0,
    val animeTotalEntries: Int = 0,
    val dramaTotalEntries: Int = 0,

    // Charts Data
    val activityPoints: List<DailyActivityPoint> = emptyList(),
    val topSources: List<SourceUsageStat> = emptyList(),
    val hourlyDistribution: List<HourlyActivityStat> = emptyList(),

    // Per Media Specifics
    val comicCompleted: Int = 0,
    val comicInProgress: Int = 0,
    val animeCompleted: Int = 0,
    val animeInProgress: Int = 0,
    val dramaCompleted: Int = 0,
    val dramaInProgress: Int = 0
)

class StatisticsViewModel(
    private val historyDao: HistoryDao,
    private val libraryDao: LibraryDao,
    private val chapterDao: ChapterDao
) : ViewModel() {

    private val _timeRange = MutableStateFlow(StatTimeRange.LAST_7_DAYS)
    val timeRange: StateFlow<StatTimeRange> = _timeRange.asStateFlow()

    private val _mediaTab = MutableStateFlow(StatMediaTab.ALL)
    val mediaTab: StateFlow<StatMediaTab> = _mediaTab.asStateFlow()

    val uiState: StateFlow<StatisticsUiState> = combine(
        historyDao.getAllHistory(com.example.data.ProfileManager.currentProfileIdValue),
        libraryDao.getAllLibrary(com.example.data.ProfileManager.currentProfileIdValue),
        chapterDao.getAllChaptersFlow(com.example.data.ProfileManager.currentProfileIdValue),
        _timeRange,
        _mediaTab
    ) { historyList, libraryList, allChapters, timeRange, mediaTab ->
        calculateStatistics(historyList, libraryList, allChapters, timeRange, mediaTab)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatisticsUiState())

    fun setTimeRange(range: StatTimeRange) {
        _timeRange.value = range
    }

    fun setMediaTab(tab: StatMediaTab) {
        _mediaTab.value = tab
    }

    private fun calculateStatistics(
        history: List<HistoryItem>,
        library: List<LibraryEntry>,
        chapters: List<ChapterEntry>,
        timeRange: StatTimeRange,
        mediaTab: StatMediaTab
    ): StatisticsUiState {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        val readChapters = chapters.filter { it.read }

        // Filter history by time range
        val filteredHistory = when (timeRange) {
            StatTimeRange.LAST_7_DAYS -> {
                val sevenDaysAgo = now - TimeUnit.DAYS.toMillis(7)
                history.filter { it.timestamp >= sevenDaysAgo }
            }
            StatTimeRange.LAST_30_DAYS -> {
                val thirtyDaysAgo = now - TimeUnit.DAYS.toMillis(30)
                history.filter { it.timestamp >= thirtyDaysAgo }
            }
            StatTimeRange.ALL_TIME -> history
        }

        // Aggregate Watch Time & Chapters Read
        var totalAnimeEpisodes = 0
        var totalDramaEpisodes = 0
        var totalChaptersRead = 0

        readChapters.forEach { ch ->
            val type = getMediaType(ch.sourceId)
            when (type) {
                "ANIME" -> totalAnimeEpisodes++
                "DRAMA" -> totalDramaEpisodes++
                else -> totalChaptersRead++
            }
        }

        val totalWatchTime = (totalAnimeEpisodes * 24) + (totalDramaEpisodes * 50)

        // Library Series Status Calculation
        var totalCompleted = 0
        var totalInProgress = 0
        var comicCompleted = 0
        var comicInProgress = 0
        var animeCompleted = 0
        var animeInProgress = 0
        var dramaCompleted = 0
        var dramaInProgress = 0

        val chaptersByManga = chapters.groupBy { "${it.sourceId}_${it.mangaId}" }

        library.forEach { lib ->
            val key = "${lib.sourceId}_${lib.mangaId}"
            val seriesChapters = chaptersByManga[key] ?: emptyList()
            val type = getMediaType(lib.sourceId)

            if (seriesChapters.isNotEmpty()) {
                val readCount = seriesChapters.count { it.read }
                val isCompleted = readCount == seriesChapters.size && seriesChapters.isNotEmpty()
                val isInProgress = readCount in 1 until seriesChapters.size

                if (isCompleted) {
                    totalCompleted++
                    when (type) {
                        "ANIME" -> animeCompleted++
                        "DRAMA" -> dramaCompleted++
                        else -> comicCompleted++
                    }
                } else if (isInProgress) {
                    totalInProgress++
                    when (type) {
                        "ANIME" -> animeInProgress++
                        "DRAMA" -> dramaInProgress++
                        else -> comicInProgress++
                    }
                }
            }
        }

        // Streak Calculation
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val activeDaysSet = history.map { item ->
            Calendar.getInstance().apply {
                timeInMillis = item.timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.toSet()

        var streak = 0
        var checkDay = todayStart
        while (activeDaysSet.contains(checkDay)) {
            streak++
            checkDay -= TimeUnit.DAYS.toMillis(1)
        }
        if (streak == 0 && activeDaysSet.contains(todayStart - TimeUnit.DAYS.toMillis(1))) {
            checkDay = todayStart - TimeUnit.DAYS.toMillis(1)
            while (activeDaysSet.contains(checkDay)) {
                streak++
                checkDay -= TimeUnit.DAYS.toMillis(1)
            }
        }

        // Media Distribution from History
        var comicHistoryCount = 0
        var animeHistoryCount = 0
        var dramaHistoryCount = 0

        filteredHistory.forEach { item ->
            when (getMediaType(item.sourceId)) {
                "ANIME" -> animeHistoryCount++
                "DRAMA" -> dramaHistoryCount++
                else -> comicHistoryCount++
            }
        }

        val totalHistCount = comicHistoryCount + animeHistoryCount + dramaHistoryCount
        val comicRatio = if (totalHistCount > 0) comicHistoryCount.toFloat() / totalHistCount else 0f
        val animeRatio = if (totalHistCount > 0) animeHistoryCount.toFloat() / totalHistCount else 0f
        val dramaRatio = if (totalHistCount > 0) dramaHistoryCount.toFloat() / totalHistCount else 0f

        // Daily Activity Points for Chart
        val numDays = when (timeRange) {
            StatTimeRange.LAST_7_DAYS -> 7
            StatTimeRange.LAST_30_DAYS -> 14 // 14 representative days or steps for cleaner display
            StatTimeRange.ALL_TIME -> 10
        }

        val activityPoints = mutableListOf<DailyActivityPoint>()
        val dayFormat = SimpleDateFormat("EEE", Locale("id", "ID"))
        val dateFormat = SimpleDateFormat("dd MMM", Locale("id", "ID"))

        for (i in (numDays - 1) downTo 0) {
            val dayCal = Calendar.getInstance().apply {
                timeInMillis = now - TimeUnit.DAYS.toMillis(i.toLong())
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = dayCal.timeInMillis
            val endMs = startMs + TimeUnit.DAYS.toMillis(1)

            val countInDay = filteredHistory.count { item ->
                val typeMatch = when (mediaTab) {
                    StatMediaTab.ALL -> true
                    StatMediaTab.COMIC -> getMediaType(item.sourceId) == "COMIC"
                    StatMediaTab.ANIME -> getMediaType(item.sourceId) == "ANIME"
                    StatMediaTab.DRAMA -> getMediaType(item.sourceId) == "DRAMA"
                }
                typeMatch && item.timestamp in startMs until endMs
            }

            activityPoints.add(
                DailyActivityPoint(
                    dayLabel = dayFormat.format(dayCal.time),
                    dateString = dateFormat.format(dayCal.time),
                    count = countInDay,
                    timestamp = startMs
                )
            )
        }

        // Hourly Distribution (Time of Day: Morning 06-12, Afternoon 12-18, Evening 18-24, Night 00-06)
        val hourlyBuckets = mutableMapOf(
            "Pagi (06-12)" to 0,
            "Siang (12-18)" to 0,
            "Malam (18-24)" to 0,
            "Dini Hari (00-06)" to 0
        )

        filteredHistory.forEach { item ->
            val itemCal = Calendar.getInstance().apply { timeInMillis = item.timestamp }
            val hour = itemCal.get(Calendar.HOUR_OF_DAY)
            when (hour) {
                in 6..11 -> hourlyBuckets["Pagi (06-12)"] = (hourlyBuckets["Pagi (06-12)"] ?: 0) + 1
                in 12..17 -> hourlyBuckets["Siang (12-18)"] = (hourlyBuckets["Siang (12-18)"] ?: 0) + 1
                in 18..23 -> hourlyBuckets["Malam (18-24)"] = (hourlyBuckets["Malam (18-24)"] ?: 0) + 1
                else -> hourlyBuckets["Dini Hari (00-06)"] = (hourlyBuckets["Dini Hari (00-06)"] ?: 0) + 1
            }
        }

        val hourlyList = hourlyBuckets.map { HourlyActivityStat(it.key, it.value) }

        // Top Sources Breakdown
        val sourceCounts = mutableMapOf<String, Int>()
        filteredHistory.forEach { item ->
            val typeMatch = when (mediaTab) {
                StatMediaTab.ALL -> true
                StatMediaTab.COMIC -> getMediaType(item.sourceId) == "COMIC"
                StatMediaTab.ANIME -> getMediaType(item.sourceId) == "ANIME"
                StatMediaTab.DRAMA -> getMediaType(item.sourceId) == "DRAMA"
            }
            if (typeMatch && item.sourceId.isNotBlank()) {
                sourceCounts[item.sourceId] = (sourceCounts[item.sourceId] ?: 0) + 1
            }
        }

        val totalSourceItems = sourceCounts.values.sum().coerceAtLeast(1)
        val topSources = sourceCounts.entries
            .sortedByDescending { it.value }
            .take(6)
            .map { entry ->
                val mediaType = getMediaType(entry.key)
                SourceUsageStat(
                    sourceId = entry.key,
                    displayName = formatSourceDisplayName(entry.key),
                    mediaType = mediaType,
                    count = entry.value,
                    percentage = entry.value.toFloat() / totalSourceItems
                )
            }

        return StatisticsUiState(
            isLoading = false,
            selectedTimeRange = timeRange,
            selectedMediaTab = mediaTab,
            totalWatchTimeMinutes = totalWatchTime,
            totalChaptersRead = totalChaptersRead,
            totalAnimeEpisodesWatched = totalAnimeEpisodes,
            totalDramaEpisodesWatched = totalDramaEpisodes,
            totalCompletedSeries = totalCompleted,
            totalInProgressSeries = totalInProgress,
            totalLibraryCount = library.size,
            currentStreakDays = streak,
            totalHistoryEntries = history.size,
            comicDistributionRatio = comicRatio,
            animeDistributionRatio = animeRatio,
            dramaDistributionRatio = dramaRatio,
            comicTotalEntries = comicHistoryCount,
            animeTotalEntries = animeHistoryCount,
            dramaTotalEntries = dramaHistoryCount,
            activityPoints = activityPoints,
            topSources = topSources,
            hourlyDistribution = hourlyList,
            comicCompleted = comicCompleted,
            comicInProgress = comicInProgress,
            animeCompleted = animeCompleted,
            animeInProgress = animeInProgress,
            dramaCompleted = dramaCompleted,
            dramaInProgress = dramaInProgress
        )
    }

    private fun getMediaType(sourceId: String): String {
        val id = sourceId.lowercase()
        return when {
            id.contains("anime") || id.contains("otaku") || id.contains("samehadaku") ||
                    id.contains("anichin") || id.contains("kura") || id.contains("kaze") ||
                    id.contains("anoboy") || id.contains("nime") || id.contains("meow") -> "ANIME"
            id.contains("drama") || id.contains("drakor") || id.contains("viki") ||
                    id.contains("licil") || id.contains("film") || id.contains("jucelebrity") -> "DRAMA"
            else -> "COMIC"
        }
    }

    private fun formatSourceDisplayName(sourceId: String): String {
        return sourceId.replace("_", " ")
            .split(" ")
            .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } }
    }
}
