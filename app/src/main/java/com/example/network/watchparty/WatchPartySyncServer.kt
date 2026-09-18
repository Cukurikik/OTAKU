package com.example.network.watchparty

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Embedded NanoWSD WebSocket Sync Server for Watch Party Host node.
 * Broadcasts play, pause, seek, heartbeat, and chat sync events
 * across all connected LAN peers with sub-50ms latency.
 */
class WatchPartySyncServer(
    port: Int = DEFAULT_PORT,
    private val onEventReceived: (WatchPartySyncEvent) -> Unit = {}
) : NanoWSD(port) {

    companion object {
        const val DEFAULT_PORT = 8088
        private const val TAG = "WatchPartySyncServer"
    }

    private val connectedSockets = CopyOnWriteArrayList<WatchPartyWebSocket>()

    var currentRoomState: WatchPartyRoom? = null

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        val socket = WatchPartyWebSocket(handshake)
        connectedSockets.add(socket)
        Log.d(TAG, "New peer connected: ${handshake.remoteIpAddress}. Total peers: ${connectedSockets.size}")
        updatePeerCount()
        return socket
    }

    private fun updatePeerCount() {
        currentRoomState = currentRoomState?.copy(connectedPeersCount = connectedSockets.size + 1)
    }

    fun broadcastEvent(event: WatchPartySyncEvent, excludeSocket: WatchPartyWebSocket? = null) {
        val payload = event.toJson().toString()
        for (socket in connectedSockets) {
            if (socket != excludeSocket && socket.isOpen) {
                try {
                    socket.send(payload)
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to send message to peer: ${e.message}")
                }
            }
        }
    }

    fun closeAllPeerSockets() {
        for (socket in connectedSockets) {
            try {
                if (socket.isOpen) {
                    socket.close(NanoWSD.WebSocketFrame.CloseCode.NormalClosure, "Host closed Watch Party", false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error closing peer socket: ${e.message}")
            }
        }
        connectedSockets.clear()
        updatePeerCount()
    }

    inner class WatchPartyWebSocket(handshakeRequest: IHTTPSession) : WebSocket(handshakeRequest) {

        override fun onOpen() {
            Log.d(TAG, "WebSocket connection opened")
            // Send current state to newly connected peer immediately
            currentRoomState?.let { room ->
                val initEvent = WatchPartySyncEvent(
                    type = SyncEventType.STATE_INIT,
                    senderName = room.hostName,
                    position = room.currentPosition,
                    isPlaying = room.isPlaying,
                    mediaTitle = room.mediaTitle,
                    episodeTitle = room.episodeTitle,
                    mediaUrl = room.mediaUrl,
                    textPayload = room.headersJson
                )
                try {
                    send(initEvent.toJson().toString())
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send state init: ${e.message}")
                }
            }
        }

        override fun onClose(
            code: NanoWSD.WebSocketFrame.CloseCode,
            reason: String,
            initiatedByRemote: Boolean
        ) {
            Log.d(TAG, "WebSocket closed: $reason, code: $code")
            connectedSockets.remove(this)
            updatePeerCount()
        }

        override fun onMessage(message: NanoWSD.WebSocketFrame) {
            try {
                val jsonStr = message.textPayload
                val json = JSONObject(jsonStr)
                val event = WatchPartySyncEvent.fromJson(json)

                // Dispatch event to local host handler
                onEventReceived(event)

                // Broadcast event to other connected peers
                broadcastEvent(event, excludeSocket = this)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing WebSocket message: ${e.message}")
            }
        }

        override fun onPong(pong: NanoWSD.WebSocketFrame) {
            // Heartbeat response
        }

        override fun onException(exception: IOException) {
            Log.e(TAG, "WebSocket exception: ${exception.message}")
            connectedSockets.remove(this)
            updatePeerCount()
        }
    }
}
