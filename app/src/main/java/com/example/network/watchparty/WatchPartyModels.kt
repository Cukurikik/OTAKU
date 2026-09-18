package com.example.network.watchparty

import org.json.JSONObject

enum class WatchPartyRole {
    HOST,
    PEER,
    NONE
}

data class WatchPartyRoom(
    val roomId: String,
    val roomName: String,
    val hostName: String,
    val hostIp: String,
    val wsPort: Int,
    val mediaTitle: String,
    val episodeTitle: String = "",
    val mediaUrl: String = "",
    val mimeType: String = "video/mp4",
    val headersJson: String = "{}",
    val currentPosition: Long = 0L,
    val isPlaying: Boolean = false,
    val connectedPeersCount: Int = 1
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("roomId", roomId)
            put("roomName", roomName)
            put("hostName", hostName)
            put("hostIp", hostIp)
            put("wsPort", wsPort)
            put("mediaTitle", mediaTitle)
            put("episodeTitle", episodeTitle)
            put("mediaUrl", mediaUrl)
            put("mimeType", mimeType)
            put("headersJson", headersJson)
            put("currentPosition", currentPosition)
            put("isPlaying", isPlaying)
            put("connectedPeersCount", connectedPeersCount)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): WatchPartyRoom {
            return WatchPartyRoom(
                roomId = json.optString("roomId", ""),
                roomName = json.optString("roomName", "Watch Party"),
                hostName = json.optString("hostName", "Host"),
                hostIp = json.optString("hostIp", "127.0.0.1"),
                wsPort = json.optInt("wsPort", 8088),
                mediaTitle = json.optString("mediaTitle", ""),
                episodeTitle = json.optString("episodeTitle", ""),
                mediaUrl = json.optString("mediaUrl", ""),
                mimeType = json.optString("mimeType", "video/mp4"),
                headersJson = json.optString("headersJson", "{}"),
                currentPosition = json.optLong("currentPosition", 0L),
                isPlaying = json.optBoolean("isPlaying", false),
                connectedPeersCount = json.optInt("connectedPeersCount", 1)
            )
        }
    }
}

enum class SyncEventType {
    JOIN,
    STATE_INIT,
    PLAY,
    PAUSE,
    SEEK,
    HEARTBEAT,
    CHAT,
    EMOJI,
    ROOM_CLOSED
}

data class WatchPartySyncEvent(
    val type: SyncEventType,
    val senderName: String,
    val position: Long = 0L,
    val isPlaying: Boolean = false,
    val mediaTitle: String = "",
    val episodeTitle: String = "",
    val mediaUrl: String = "",
    val textPayload: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("type", type.name)
            put("senderName", senderName)
            put("position", position)
            put("isPlaying", isPlaying)
            put("mediaTitle", mediaTitle)
            put("episodeTitle", episodeTitle)
            put("mediaUrl", mediaUrl)
            put("textPayload", textPayload)
            put("timestamp", timestamp)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): WatchPartySyncEvent {
            return WatchPartySyncEvent(
                type = try {
                    SyncEventType.valueOf(json.optString("type", "HEARTBEAT"))
                } catch (e: Exception) {
                    SyncEventType.HEARTBEAT
                },
                senderName = json.optString("senderName", "Anonymous"),
                position = json.optLong("position", 0L),
                isPlaying = json.optBoolean("isPlaying", false),
                mediaTitle = json.optString("mediaTitle", ""),
                episodeTitle = json.optString("episodeTitle", ""),
                mediaUrl = json.optString("mediaUrl", ""),
                textPayload = json.optString("textPayload", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val senderName: String,
    val message: String,
    val isEmojiOnly: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
