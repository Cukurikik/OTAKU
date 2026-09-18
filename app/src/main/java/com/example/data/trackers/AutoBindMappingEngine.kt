package com.example.data.trackers

import android.content.Context
import android.util.Log
import com.example.OtakuHubApp
import com.example.data.SettingsManager
import com.example.data.models.ContentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

object AutoBindMappingEngine {
    private const val TAG = "AutoBindMappingEngine"
    private const val MATCH_THRESHOLD = 0.85

    fun calculateLevenshteinDistance(a: String, b: String): Int {
        val lhs = a.lowercase()
        val rhs = b.lowercase()
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

    suspend fun tryAutoBind(context: Context, entry: ContentItem) = withContext(Dispatchers.IO) {
        val settings = SettingsManager(context)
        val trackerManager = TrackerManager(context, settings)

        val loggedInTrackers = trackerManager.trackers.filter { it.isLogged() }
        if (loggedInTrackers.isEmpty()) {
            Log.d(TAG, "No logged in trackers. Skipping Auto-Bind.")
            return@withContext
        }

        Log.i(TAG, "Starting Auto-Bind scan for '${entry.title}'")

        val app = context.applicationContext as? OtakuHubApp ?: return@withContext
        val trackDao = app.database.trackDao()
        val profileId = com.example.data.ProfileManager.currentProfileIdValue

        val existingTracks = try {
            trackDao.getTracksForMangaSync(profileId, entry.id)
        } catch (e: Exception) {
            emptyList()
        }
        val boundTrackerIds = existingTracks.map { it.trackerId }

        for (tracker in loggedInTrackers) {
            if (boundTrackerIds.contains(tracker.id)) continue

            try {
                // Remove trailing tags (e.g. " (Subtitle Indonesia)" or " [Sub Indo]") that might ruin matching
                var searchTitle = entry.title
                    .replace(Regex("\\(.*?\\)"), "")
                    .replace(Regex("\\[.*?\\]"), "")
                    .trim()

                if (searchTitle.isBlank()) searchTitle = entry.title

                val results = tracker.search(searchTitle)
                var bestMatch: com.example.data.models.TrackItem? = null
                var bestScore = 0.0

                for (item in results) {
                    val titleScore = calculateSimilarity(searchTitle, item.title)
                    if (titleScore > bestScore) {
                        bestScore = titleScore
                        bestMatch = item
                    }
                }

                if (bestMatch != null && bestScore >= MATCH_THRESHOLD) {
                    Log.i(TAG, "Auto-Bind Success: '${entry.title}' -> '${bestMatch.title}' on ${tracker.name} (Score: ${String.format("%.1f", bestScore * 100)}%)")
                    val trackEntity = tracker.bind(bestMatch, entry)
                    val newEntity = trackEntity.copy(profileId = profileId)
                    trackDao.insertTrack(newEntity)
                } else {
                    Log.w(TAG, "Auto-Bind Failed: '${entry.title}' on ${tracker.name} (Best match: '${bestMatch?.title}', Score: ${String.format("%.1f", bestScore * 100)}%)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Auto-Bind for ${tracker.name}: ${e.message}", e)
            }
        }
    }
}
