package com.example.cast

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.cast.MediaItemConverter
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.media3.common.util.UnstableApi
import org.json.JSONObject

@UnstableApi
class CastHelper(
    private val context: Context,
    private val localPlayer: Player,
    private val headers: Map<String, String> = emptyMap(),
    private val dynamicHeadersProvider: (() -> Map<String, String>)? = null
) {
    private var castPlayer: CastPlayer? = null
    
    private val _isCastAvailable = MutableStateFlow(false)
    val isCastAvailable: StateFlow<Boolean> = _isCastAvailable.asStateFlow()
    
    private val _currentPlayer = MutableStateFlow<Player>(localPlayer)
    val currentPlayer: StateFlow<Player> = _currentPlayer.asStateFlow()

    init {
        try {
            val castContext = CastContext.getSharedInstance(context)
            
            val mediaItemConverter = object : MediaItemConverter {
                val defaultConverter = DefaultMediaItemConverter()
                
                override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
                    val queueItem = defaultConverter.toMediaQueueItem(mediaItem)
                    val originalMediaInfo = queueItem.media
                    
                    val activeHeaders = dynamicHeadersProvider?.invoke() ?: headers
                    val mediaInfoBuilder = if (originalMediaInfo != null) {
                        MediaInfo.Builder(originalMediaInfo.contentId)
                            .setStreamType(originalMediaInfo.streamType)
                            .setContentType(originalMediaInfo.contentType)
                            .setMetadata(originalMediaInfo.metadata ?: CastMediaMetadata(CastMediaMetadata.MEDIA_TYPE_MOVIE).apply {
                                mediaItem.mediaMetadata.title?.let {
                                    putString(CastMediaMetadata.KEY_TITLE, it.toString())
                                }
                                mediaItem.mediaMetadata.subtitle?.let {
                                    putString(CastMediaMetadata.KEY_SUBTITLE, it.toString())
                                }
                            })
                            .setMediaTracks(originalMediaInfo.mediaTracks)
                    } else {
                        val uriStr = mediaItem.localConfiguration?.uri?.toString() ?: ""
                        val contentType = if (uriStr.contains(".m3u8", ignoreCase = true)) {
                            "application/x-mpegURL"
                        } else {
                            "video/mp4"
                        }
                        val metadata = CastMediaMetadata(CastMediaMetadata.MEDIA_TYPE_MOVIE).apply {
                            mediaItem.mediaMetadata.title?.let {
                                putString(CastMediaMetadata.KEY_TITLE, it.toString())
                            }
                            mediaItem.mediaMetadata.subtitle?.let {
                                putString(CastMediaMetadata.KEY_SUBTITLE, it.toString())
                            }
                        }
                        MediaInfo.Builder(uriStr)
                            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                            .setContentType(contentType)
                            .setMetadata(metadata)
                    }

                    val customData = originalMediaInfo?.customData ?: JSONObject()
                    if (activeHeaders.isNotEmpty()) {
                        val headersJson = JSONObject()
                        activeHeaders.forEach { (key, value) ->
                            headersJson.put(key, value)
                        }
                        customData.put("headers", headersJson)
                        activeHeaders["User-Agent"]?.let { customData.put("User-Agent", it) }
                        activeHeaders["Referer"]?.let { customData.put("Referer", it) }
                    }
                    mediaInfoBuilder.setCustomData(customData)
                    
                    val builder = MediaQueueItem.Builder(mediaInfoBuilder.build())
                    builder.setAutoplay(queueItem.autoplay)
                    builder.setPreloadTime(queueItem.preloadTime)
                    builder.setStartTime(queueItem.startTime)
                    builder.setPlaybackDuration(queueItem.playbackDuration)
                    if (queueItem.activeTrackIds != null) {
                        builder.setActiveTrackIds(queueItem.activeTrackIds!!)
                    }
                    if (queueItem.customData != null) {
                        builder.setCustomData(queueItem.customData!!)
                    }
                    
                    return builder.build()
                }
                
                override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
                    return defaultConverter.toMediaItem(mediaQueueItem)
                }
            }
            
            castPlayer = CastPlayer(castContext, mediaItemConverter)
            
            castPlayer?.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() {
                    _isCastAvailable.value = true
                    transferState(localPlayer, castPlayer!!)
                    _currentPlayer.value = castPlayer!!
                }

                override fun onCastSessionUnavailable() {
                    _isCastAvailable.value = false
                    transferState(castPlayer!!, localPlayer)
                    _currentPlayer.value = localPlayer
                }
            })
            
            if (castPlayer?.isCastSessionAvailable == true) {
                _isCastAvailable.value = true
                transferState(localPlayer, castPlayer!!)
                _currentPlayer.value = castPlayer!!
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun transferState(from: Player, to: Player) {
        val playWhenReady = from.playWhenReady
        val playbackPosition = Math.max(0L, from.currentPosition)
        val mediaItem = from.currentMediaItem
        
        from.stop()
        from.clearMediaItems()
        
        if (mediaItem != null) {
            to.setMediaItem(mediaItem, playbackPosition)
            to.playWhenReady = playWhenReady
            to.prepare()
        }
    }

    fun release() {
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
    }
}
