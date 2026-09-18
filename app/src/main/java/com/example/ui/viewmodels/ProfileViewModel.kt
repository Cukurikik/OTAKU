package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SettingsManager
import com.example.data.db.ChapterDao
import com.example.data.db.HistoryDao
import com.example.data.db.LibraryDao
import com.example.data.models.HistoryItem
import com.example.data.models.LibraryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class ProfileStats(
    val totalWatchTimeMinutes: Int = 0,
    val totalChaptersRead: Int = 0,
    val favoriteComicsCount: Int = 0,
    val favoriteAnimesCount: Int = 0,
    val favoriteDramasCount: Int = 0,
    val weeklyActivity: List<Float> = List(7) { 0f }, // Mon-Sun
    val streakDays: Int = 0,
    val mediaDistribution: List<Float> = listOf(0f, 0f, 0f) // Comic, Anime, Drama
)

class ProfileViewModel(
    private val historyDao: HistoryDao,
    private val libraryDao: LibraryDao,
    private val chapterDao: ChapterDao,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _kitsuToken = MutableStateFlow(settingsManager.kitsuToken)
    val kitsuToken: StateFlow<String> = _kitsuToken
    
    private val _kitsuUserId = MutableStateFlow(settingsManager.kitsuUserId)
    val kitsuUserId: StateFlow<String> = _kitsuUserId

    private val _malToken = MutableStateFlow(settingsManager.malToken)
    val malToken: StateFlow<String> = _malToken

    private val _anilistToken = MutableStateFlow(settingsManager.anilistToken)
    val anilistToken: StateFlow<String> = _anilistToken

    val stats: StateFlow<ProfileStats> = combine(
        historyDao.getAllHistory(com.example.data.ProfileManager.currentProfileIdValue),
        libraryDao.getAllLibrary(com.example.data.ProfileManager.currentProfileIdValue),
        chapterDao.getReadChaptersSourceIds(com.example.data.ProfileManager.currentProfileIdValue)
    ) { history, library, readSourceIds ->
        calculateStats(history, library, readSourceIds)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileStats())

    fun saveKitsuToken(token: String, userId: String) {
        settingsManager.kitsuToken = token
        settingsManager.kitsuUserId = userId
        _kitsuToken.value = token
        _kitsuUserId.value = userId
    }

    fun saveMalToken(accessToken: String, refreshToken: String = "", expiresIn: Long = 0L) {
        val expiresAt = if (expiresIn > 0) System.currentTimeMillis() + (expiresIn * 1000L) else 0L
        settingsManager.malToken = accessToken
        settingsManager.malRefreshToken = refreshToken
        settingsManager.malExpiresAt = expiresAt
        _malToken.value = accessToken
    }
    
    fun saveAnilistToken(token: String) {
        settingsManager.anilistToken = token
        _anilistToken.value = token
    }
    
    fun logoutKitsu() {
        settingsManager.kitsuToken = ""
        settingsManager.kitsuUserId = ""
        _kitsuToken.value = ""
        _kitsuUserId.value = ""
    }
    
    fun logoutMal() {
        settingsManager.malToken = ""
        settingsManager.malRefreshToken = ""
        settingsManager.malExpiresAt = 0L
        _malToken.value = ""
    }

    fun logoutAnilist() {
        settingsManager.anilistToken = ""
        _anilistToken.value = ""
    }

    private fun calculateStats(history: List<HistoryItem>, library: List<LibraryEntry>, readSourceIds: List<String>): ProfileStats {
        var watchTime = 0
        var chaptersRead = 0
        var favoriteComics = 0
        var favoriteAnimes = 0
        var favoriteDramas = 0
        var historyComics = 0f
        var historyAnimes = 0f
        var historyDramas = 0f

        // Parse read chapters/episodes
        for (sourceId in readSourceIds) {
            val mediaType = getMediaType(sourceId)
            if (mediaType == "ANIME") {
                watchTime += 24
            } else if (mediaType == "DRAMA") {
                watchTime += 60
            } else {
                chaptersRead += 1
            }
        }

        // Parse history for activity and streak
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        // For weekly activity: mapping 1=Sun, 2=Mon, etc to a value
        val dayActivityMap = mutableMapOf<Int, Float>()
        val uniqueDaysActive = mutableSetOf<Long>()

        for (item in history) {
            val mediaType = getMediaType(item.sourceId)
            when (mediaType) {
                "ANIME" -> historyAnimes++
                "DRAMA" -> historyDramas++
                else -> historyComics++
            }
            
            val calendar = Calendar.getInstance().apply { timeInMillis = item.timestamp }
            val dayStart = Calendar.getInstance().apply {
                timeInMillis = item.timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            uniqueDaysActive.add(dayStart)

            // Weekly activity: limit to last 7 days including today
            val diffMs = today.timeInMillis - dayStart
            val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)

            // if diffDays is negative, it's in the future? (shouldn't happen)
            // if diffDays is 0..6, it's within the last 7 days
            if (diffDays in 0..6) {
                val dow = calendar.get(Calendar.DAY_OF_WEEK)
                dayActivityMap[dow] = (dayActivityMap[dow] ?: 0f) + 1f
            }
        }

        // Parse library
        for (item in library) {
            val mediaType = getMediaType(item.sourceId)
            when (mediaType) {
                "ANIME" -> favoriteAnimes++
                "DRAMA" -> favoriteDramas++
                "COMIC" -> favoriteComics++
            }
        }

        // Build weekly array (Mon=0, Sun=6)
        val weeklyActivity = FloatArray(7) { 0f }
        weeklyActivity[0] = dayActivityMap[Calendar.MONDAY] ?: 0f
        weeklyActivity[1] = dayActivityMap[Calendar.TUESDAY] ?: 0f
        weeklyActivity[2] = dayActivityMap[Calendar.WEDNESDAY] ?: 0f
        weeklyActivity[3] = dayActivityMap[Calendar.THURSDAY] ?: 0f
        weeklyActivity[4] = dayActivityMap[Calendar.FRIDAY] ?: 0f
        weeklyActivity[5] = dayActivityMap[Calendar.SATURDAY] ?: 0f
        weeklyActivity[6] = dayActivityMap[Calendar.SUNDAY] ?: 0f

        // Calculate streak
        var streak = 0
        var checkDay = today.timeInMillis
        while (uniqueDaysActive.contains(checkDay)) {
            streak++
            checkDay -= TimeUnit.DAYS.toMillis(1)
        }
        if (streak == 0 && uniqueDaysActive.contains(today.timeInMillis - TimeUnit.DAYS.toMillis(1))) {
            // Started from yesterday
            checkDay = today.timeInMillis - TimeUnit.DAYS.toMillis(1)
            while (uniqueDaysActive.contains(checkDay)) {
                streak++
                checkDay -= TimeUnit.DAYS.toMillis(1)
            }
        }

        // Calculate distribution
        val totalHistory = historyComics + historyAnimes + historyDramas
        val distribution = if (totalHistory > 0) {
            listOf(historyComics / totalHistory, historyAnimes / totalHistory, historyDramas / totalHistory)
        } else {
            listOf(0f, 0f, 0f)
        }

        return ProfileStats(
            totalWatchTimeMinutes = watchTime,
            totalChaptersRead = chaptersRead,
            favoriteComicsCount = favoriteComics,
            favoriteAnimesCount = favoriteAnimes,
            favoriteDramasCount = favoriteDramas,
            weeklyActivity = weeklyActivity.toList(),
            streakDays = streak,
            mediaDistribution = distribution
        )
    }

    private fun getMediaType(sourceId: String): String {
        val id = sourceId.lowercase()
        return when {
            id.contains("anime") || id.contains("otaku") || id.contains("samehadaku") || id.contains("anichin") || id.contains("kura") || id.contains("kaze") || id.contains("anoboy") || id.contains("nime") -> "ANIME"
            id.contains("drama") || id.contains("drakor") || id.contains("viki") || id.contains("licil") || id.contains("film") || id.contains("jucelebrity") -> "DRAMA"
            else -> "COMIC"
        }
    }
}
