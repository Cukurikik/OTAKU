package com.example.network.watchparty

import android.util.Log
import com.example.data.NetworkModule
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WatchPartyClient(
    private val hostIp: String,
    private val wsPort: Int,
    private val peerName: String,
    private val onEventReceived: (WatchPartySyncEvent) -> Unit
) {
    companion object {
        private const val TAG = "WatchPartyClient"
    }

    private var webSocket: WebSocket? = null
    private val client: OkHttpClient = NetworkModule.okHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _pingMs = MutableStateFlow(0L)
    val pingMs: StateFlow<Long> = _pingMs.asStateFlow()

    private var lastPingSendTime = 0L

    fun connect() {
        disconnect()
        val wsUrl = "ws://$hostIp:$wsPort/watchparty/ws"
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to Watch Party host at $wsUrl")
                _isConnected.value = true
                
                // Send JOIN event
                val joinEvent = WatchPartySyncEvent(
                    type = SyncEventType.JOIN,
                    senderName = peerName,
                    timestamp = System.currentTimeMillis()
                )
                webSocket.send(joinEvent.toJson().toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val event = WatchPartySyncEvent.fromJson(json)

                    // Measure round-trip ping latency if heartbeat
                    if (event.type == SyncEventType.HEARTBEAT && event.senderName == peerName) {
                        val roundTrip = System.currentTimeMillis() - lastPingSendTime
                        _pingMs.value = (roundTrip / 2).coerceAtLeast(1L)
                    }

                    onEventReceived(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse incoming WS message: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $reason ($code)")
                _isConnected.value = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason ($code)")
                _isConnected.value = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                _isConnected.value = false
            }
        })
    }

    fun sendSyncEvent(event: WatchPartySyncEvent) {
        val ws = webSocket
        if (ws != null && _isConnected.value) {
            if (event.type == SyncEventType.HEARTBEAT) {
                lastPingSendTime = System.currentTimeMillis()
            }
            ws.send(event.toJson().toString())
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _isConnected.value = false
    }
}
