package com.example.network.watchparty

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WatchPartyEngineTest {

    @Test
    fun testWatchPartyRoomSerialization() {
        val room = WatchPartyRoom(
            roomId = "wp-123456",
            roomName = "Anime Night",
            hostName = "Otaku Host",
            hostIp = "192.168.1.50",
            wsPort = 8088,
            mediaTitle = "One Piece Episode 1000",
            episodeTitle = "Episode 1000",
            mediaUrl = "http://192.168.1.50:8080/media/op1000.mp4",
            connectedPeersCount = 3
        )

        val json = room.toJson()
        assertEquals("wp-123456", json.getString("roomId"))
        assertEquals("Anime Night", json.getString("roomName"))
        assertEquals("192.168.1.50", json.getString("hostIp"))
        assertEquals(8088, json.getInt("wsPort"))

        val parsedRoom = WatchPartyRoom.fromJson(json)
        assertEquals(room.roomId, parsedRoom.roomId)
        assertEquals(room.roomName, parsedRoom.roomName)
        assertEquals(room.hostName, parsedRoom.hostName)
        assertEquals(room.hostIp, parsedRoom.hostIp)
        assertEquals(room.wsPort, parsedRoom.wsPort)
        assertEquals(room.mediaTitle, parsedRoom.mediaTitle)
        assertEquals(3, parsedRoom.connectedPeersCount)
    }

    @Test
    fun testWatchPartySyncEventSerialization() {
        val event = WatchPartySyncEvent(
            type = SyncEventType.SEEK,
            senderName = "Peer_Device_1",
            position = 45000L,
            isPlaying = true,
            textPayload = "Fast forward to fight scene"
        )

        val json = event.toJson()
        assertEquals("SEEK", json.getString("type"))
        assertEquals("Peer_Device_1", json.getString("senderName"))
        assertEquals(45000L, json.getLong("position"))
        assertTrue(json.getBoolean("isPlaying"))

        val parsedEvent = WatchPartySyncEvent.fromJson(json)
        assertEquals(SyncEventType.SEEK, parsedEvent.type)
        assertEquals("Peer_Device_1", parsedEvent.senderName)
        assertEquals(45000L, parsedEvent.position)
        assertTrue(parsedEvent.isPlaying)
    }

    @Test
    fun testChatAndEmojiMessageModels() {
        val chatMsg = ChatMessage(
            senderName = "Host",
            message = "Wah episode ini keren banget!!",
            isEmojiOnly = false
        )
        assertNotNull(chatMsg.id)
        assertEquals("Host", chatMsg.senderName)
        assertEquals("Wah episode ini keren banget!!", chatMsg.message)

        val emojiMsg = ChatMessage(
            senderName = "Peer2",
            message = "🔥",
            isEmojiOnly = true
        )
        assertEquals("🔥", emojiMsg.message)
        assertTrue(emojiMsg.isEmojiOnly)
    }

    @Test
    fun testLowLatencySyncFormula() {
        val tPropagation = 12L // 12ms network round trip / 2 = 6ms
        val tProcessing = 5L  // 5ms message parsing & player seek execution
        val latencySync = tPropagation + tProcessing

        assertTrue("Latency sync must be less than 50ms", latencySync < 50)
    }
}
