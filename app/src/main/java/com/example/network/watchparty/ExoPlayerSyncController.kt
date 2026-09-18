package com.example.network.watchparty

import android.content.Context
import android.util.Log
import androidx.media3.common.Player
import com.example.network.server.LocalMediaServer
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

class ExoPlayerSyncController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ExoPlayerSyncController"
        private const val MAX_ALLOWED_DRIFT_MS = 300L
    }

    private var player: Player? = null
    var role: WatchPartyRole = WatchPartyRole.NONE
        private set

    var currentRoom: WatchPartyRoom? = null
        private set

    private var hostServer: WatchPartySyncServer? = null
    private var client: WatchPartyClient? = null
    private var discovery: WatchPartyDiscovery = WatchPartyDiscovery(context)

    val activeRooms: StateFlow<List<WatchPartyRoom>> = discovery.activeRooms

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _pingMs = MutableStateFlow(0L)
    val pingMs: StateFlow<Long> = _pingMs.asStateFlow()

    private val _connectedPeersCount = MutableStateFlow(1)
    val connectedPeersCount: StateFlow<Int> = _connectedPeersCount.asStateFlow()

    private var isExecutingRemoteCommand = false
    private var heartbeatJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isExecutingRemoteCommand) return

            val currentPos = player?.currentPosition ?: 0L
            if (role == WatchPartyRole.HOST) {
                val eventType = if (isPlaying) SyncEventType.PLAY else SyncEventType.PAUSE
                val event = WatchPartySyncEvent(
                    type = eventType,
                    senderName = currentRoom?.hostName ?: "Host",
                    position = currentPos,
                    isPlaying = isPlaying
                )
                hostServer?.broadcastEvent(event)
            } else if (role == WatchPartyRole.PEER) {
                val eventType = if (isPlaying) SyncEventType.PLAY else SyncEventType.PAUSE
                val event = WatchPartySyncEvent(
                    type = eventType,
                    senderName = "Peer",
                    position = currentPos,
                    isPlaying = isPlaying
                )
                client?.sendSyncEvent(event)
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (isExecutingRemoteCommand) return
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                val targetPos = newPosition.positionMs
                if (role == WatchPartyRole.HOST) {
                    val event = WatchPartySyncEvent(
                        type = SyncEventType.SEEK,
                        senderName = currentRoom?.hostName ?: "Host",
                        position = targetPos,
                        isPlaying = player?.isPlaying ?: false
                    )
                    hostServer?.broadcastEvent(event)
                } else if (role == WatchPartyRole.PEER) {
                    val event = WatchPartySyncEvent(
                        type = SyncEventType.SEEK,
                        senderName = "Peer",
                        position = targetPos,
                        isPlaying = player?.isPlaying ?: false
                    )
                    client?.sendSyncEvent(event)
                }
            }
        }
    }

    fun attachPlayer(exoPlayer: Player) {
        detachPlayer()
        this.player = exoPlayer
        exoPlayer.addListener(playerListener)
    }

    fun detachPlayer() {
        player?.removeListener(playerListener)
        player = null
    }

    fun startHosting(
        roomName: String,
        hostName: String,
        mediaTitle: String,
        episodeTitle: String,
        mediaUrl: String,
        headersJson: String = "{}"
    ) {
        leaveRoom()
        role = WatchPartyRole.HOST

        val localIp = LocalMediaServer.getLocalIpAddress(context)
        val roomId = java.util.UUID.randomUUID().toString().take(8)
        val room = WatchPartyRoom(
            roomId = roomId,
            roomName = roomName,
            hostName = hostName,
            hostIp = localIp,
            wsPort = WatchPartySyncServer.DEFAULT_PORT,
            mediaTitle = mediaTitle,
            episodeTitle = episodeTitle,
            mediaUrl = mediaUrl,
            headersJson = headersJson,
            currentPosition = player?.currentPosition ?: 0L,
            isPlaying = player?.isPlaying ?: false
        )
        currentRoom = room

        try {
            hostServer = WatchPartySyncServer(WatchPartySyncServer.DEFAULT_PORT) { incomingEvent ->
                handleIncomingEvent(incomingEvent)
            }.apply {
                currentRoomState = room
                start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            }
            _isConnected.value = true
            _pingMs.value = 1L
            discovery.startHostingRoom(room)

            startHeartbeatLoop()
            Log.d(TAG, "Watch Party Host server started on port ${WatchPartySyncServer.DEFAULT_PORT}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Watch Party Host server: ${e.message}")
        }
    }

    fun joinRoom(room: WatchPartyRoom, peerName: String) {
        leaveRoom()
        role = WatchPartyRole.PEER
        currentRoom = room

        client = WatchPartyClient(
            hostIp = room.hostIp,
            wsPort = room.wsPort,
            peerName = peerName
        ) { incomingEvent ->
            handleIncomingEvent(incomingEvent)
        }.apply {
            connect()
        }

        scope.launch {
            client?.isConnected?.collect { connected ->
                _isConnected.value = connected
            }
        }
        scope.launch {
            client?.pingMs?.collect { ping ->
                _pingMs.value = ping
            }
        }

        startHeartbeatLoop()
    }

    fun startDiscovering() {
        discovery.startDiscoveringRooms()
    }

    fun stopDiscovering() {
        discovery.stopDiscoveringRooms()
    }

    fun sendChatMessage(message: String, senderName: String) {
        val chatMsg = ChatMessage(senderName = senderName, message = message, isEmojiOnly = false)
        val event = WatchPartySyncEvent(
            type = SyncEventType.CHAT,
            senderName = senderName,
            textPayload = message
        )
        addChatMessageLocally(chatMsg)
        broadcastOrSendEvent(event)
    }

    fun sendEmojiReaction(emoji: String, senderName: String) {
        val chatMsg = ChatMessage(senderName = senderName, message = emoji, isEmojiOnly = true)
        val event = WatchPartySyncEvent(
            type = SyncEventType.EMOJI,
            senderName = senderName,
            textPayload = emoji
        )
        addChatMessageLocally(chatMsg)
        broadcastOrSendEvent(event)
    }

    private fun broadcastOrSendEvent(event: WatchPartySyncEvent) {
        if (role == WatchPartyRole.HOST) {
            hostServer?.broadcastEvent(event)
        } else if (role == WatchPartyRole.PEER) {
            client?.sendSyncEvent(event)
        }
    }

    private fun addChatMessageLocally(msg: ChatMessage) {
        val list = _chatMessages.value.toMutableList()
        list.add(msg)
        if (list.size > 50) list.removeAt(0)
        _chatMessages.value = list
    }

    private fun handleIncomingEvent(event: WatchPartySyncEvent) {
        scope.launch(Dispatchers.Main) {
            when (event.type) {
                SyncEventType.PLAY -> {
                    isExecutingRemoteCommand = true
                    val currentPos = player?.currentPosition ?: 0L
                    val drift = Math.abs(currentPos - event.position)
                    if (drift > MAX_ALLOWED_DRIFT_MS) {
                        player?.seekTo(event.position)
                    }
                    player?.play()
                    isExecutingRemoteCommand = false
                }
                SyncEventType.PAUSE -> {
                    isExecutingRemoteCommand = true
                    player?.pause()
                    player?.seekTo(event.position)
                    isExecutingRemoteCommand = false
                }
                SyncEventType.SEEK -> {
                    isExecutingRemoteCommand = true
                    player?.seekTo(event.position)
                    if (event.isPlaying) {
                        player?.play()
                    } else {
                        player?.pause()
                    }
                    isExecutingRemoteCommand = false
                }
                SyncEventType.STATE_INIT -> {
                    isExecutingRemoteCommand = true
                    if (event.position > 0) {
                        player?.seekTo(event.position)
                    }
                    if (event.isPlaying) {
                        player?.play()
                    } else {
                        player?.pause()
                    }
                    isExecutingRemoteCommand = false
                }
                SyncEventType.CHAT -> {
                    addChatMessageLocally(
                        ChatMessage(senderName = event.senderName, message = event.textPayload, isEmojiOnly = false)
                    )
                }
                SyncEventType.EMOJI -> {
                    addChatMessageLocally(
                        ChatMessage(senderName = event.senderName, message = event.textPayload, isEmojiOnly = true)
                    )
                }
                SyncEventType.HEARTBEAT -> {
                    // Check drift for host monitoring
                    if (role == WatchPartyRole.HOST) {
                        val hostPos = player?.currentPosition ?: 0L
                        val drift = Math.abs(hostPos - event.position)
                        if (drift > MAX_ALLOWED_DRIFT_MS && player?.isPlaying == true) {
                            val correctiveEvent = WatchPartySyncEvent(
                                type = SyncEventType.SEEK,
                                senderName = currentRoom?.hostName ?: "Host",
                                position = hostPos,
                                isPlaying = true
                            )
                            hostServer?.broadcastEvent(correctiveEvent)
                        }
                    }
                }
                SyncEventType.JOIN -> {
                    Log.d(TAG, "Peer joined: ${event.senderName}")
                }
                SyncEventType.ROOM_CLOSED -> {
                    leaveRoom()
                }
            }
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.Default) {
            while (role != WatchPartyRole.NONE) {
                delay(3000)
                val currentPos = player?.currentPosition ?: 0L
                val isPlaying = player?.isPlaying ?: false
                val heartbeat = WatchPartySyncEvent(
                    type = SyncEventType.HEARTBEAT,
                    senderName = if (role == WatchPartyRole.HOST) "Host" else "Peer",
                    position = currentPos,
                    isPlaying = isPlaying
                )
                broadcastOrSendEvent(heartbeat)
            }
        }
    }

    fun leaveRoom() {
        heartbeatJob?.cancel()
        heartbeatJob = null

        if (role == WatchPartyRole.HOST) {
            val closeEvent = WatchPartySyncEvent(
                type = SyncEventType.ROOM_CLOSED,
                senderName = currentRoom?.hostName ?: "Host"
            )
            hostServer?.broadcastEvent(closeEvent)
            hostServer?.closeAllPeerSockets()
            hostServer?.stop()
            hostServer = null
            discovery.stopHostingRoom()
        } else if (role == WatchPartyRole.PEER) {
            client?.disconnect()
            client = null
        }

        role = WatchPartyRole.NONE
        currentRoom = null
        _isConnected.value = false
        _pingMs.value = 0L
    }
}
