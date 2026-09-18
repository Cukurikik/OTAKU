package com.example.data.sync

import androidx.annotation.Keep
import org.json.JSONObject

/**
 * Delta State data model for cross-device micro-state playback & reading synchronization.
 */
@Keep
data class CrossDeviceResumeState(
    val deviceId: String = "",
    val deviceName: String = "",
    val itemId: String = "",
    val sourceId: String = "",
    val contentTitle: String = "",
    val episodeOrChapterId: String = "",
    val episodeOrChapterTitle: String = "",
    val contentType: String = "ANIME", // "ANIME", "DRAMA", "COMIC"
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val pageIndex: Int = 0,
    val totalPages: Int = 0,
    val coverImageUrl: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Formats position into readable string:
     * e.g., "12:05" or "01:15:30" for video, or "Halaman 15" for comic.
     */
    fun formattedTimestamp(): String {
        return if (contentType.equals("COMIC", ignoreCase = true)) {
            if (totalPages > 0) {
                "Halaman ${pageIndex + 1} / $totalPages"
            } else {
                "Halaman ${pageIndex + 1}"
            }
        } else {
            val totalSeconds = (positionMs / 1000).coerceAtLeast(0)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "deviceId" to deviceId,
            "deviceName" to deviceName,
            "itemId" to itemId,
            "sourceId" to sourceId,
            "contentTitle" to contentTitle,
            "episodeOrChapterId" to episodeOrChapterId,
            "episodeOrChapterTitle" to episodeOrChapterTitle,
            "contentType" to contentType,
            "positionMs" to positionMs,
            "durationMs" to durationMs,
            "pageIndex" to pageIndex,
            "totalPages" to totalPages,
            "coverImageUrl" to coverImageUrl,
            "updatedAt" to updatedAt
        )
    }

    fun toJson(): String {
        val json = JSONObject()
        json.put("deviceId", deviceId)
        json.put("deviceName", deviceName)
        json.put("itemId", itemId)
        json.put("sourceId", sourceId)
        json.put("contentTitle", contentTitle)
        json.put("episodeOrChapterId", episodeOrChapterId)
        json.put("episodeOrChapterTitle", episodeOrChapterTitle)
        json.put("contentType", contentType)
        json.put("positionMs", positionMs)
        json.put("durationMs", durationMs)
        json.put("pageIndex", pageIndex)
        json.put("totalPages", totalPages)
        json.put("coverImageUrl", coverImageUrl)
        json.put("updatedAt", updatedAt)
        return json.toString()
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): CrossDeviceResumeState? {
            return try {
                CrossDeviceResumeState(
                    deviceId = map["deviceId"] as? String ?: "",
                    deviceName = map["deviceName"] as? String ?: "",
                    itemId = map["itemId"] as? String ?: "",
                    sourceId = map["sourceId"] as? String ?: "",
                    contentTitle = map["contentTitle"] as? String ?: "",
                    episodeOrChapterId = map["episodeOrChapterId"] as? String ?: "",
                    episodeOrChapterTitle = map["episodeOrChapterTitle"] as? String ?: "",
                    contentType = map["contentType"] as? String ?: "ANIME",
                    positionMs = (map["positionMs"] as? Number)?.toLong() ?: 0L,
                    durationMs = (map["durationMs"] as? Number)?.toLong() ?: 0L,
                    pageIndex = (map["pageIndex"] as? Number)?.toInt() ?: 0,
                    totalPages = (map["totalPages"] as? Number)?.toInt() ?: 0,
                    coverImageUrl = map["coverImageUrl"] as? String ?: "",
                    updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L
                )
            } catch (e: Exception) {
                null
            }
        }

        fun fromJson(jsonStr: String): CrossDeviceResumeState? {
            return try {
                val json = JSONObject(jsonStr)
                CrossDeviceResumeState(
                    deviceId = json.optString("deviceId", ""),
                    deviceName = json.optString("deviceName", ""),
                    itemId = json.optString("itemId", ""),
                    sourceId = json.optString("sourceId", ""),
                    contentTitle = json.optString("contentTitle", ""),
                    episodeOrChapterId = json.optString("episodeOrChapterId", ""),
                    episodeOrChapterTitle = json.optString("episodeOrChapterTitle", ""),
                    contentType = json.optString("contentType", "ANIME"),
                    positionMs = json.optLong("positionMs", 0L),
                    durationMs = json.optLong("durationMs", 0L),
                    pageIndex = json.optInt("pageIndex", 0),
                    totalPages = json.optInt("totalPages", 0),
                    coverImageUrl = json.optString("coverImageUrl", ""),
                    updatedAt = json.optLong("updatedAt", 0L)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
