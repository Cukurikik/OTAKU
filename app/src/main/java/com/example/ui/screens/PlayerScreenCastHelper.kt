package com.example.ui.screens

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.cast.MediaItemConverter
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.media3.common.util.UnstableApi
import org.json.JSONObject

@UnstableApi
class CastManager(
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
                    
                    val drmConfig = mediaItem.localConfiguration?.drmConfiguration
                    val hasDrm = drmConfig != null && drmConfig.licenseUri != null
                    val activeHeaders = dynamicHeadersProvider?.invoke() ?: headers
                    if (originalMediaInfo != null && (activeHeaders.isNotEmpty() || hasDrm)) {
                        val customData = originalMediaInfo.customData ?: JSONObject()
                        
                        if (activeHeaders.isNotEmpty()) {
                            // Pass headers to Google Cast Receiver
                            val headersJson = JSONObject()
                            activeHeaders.forEach { (key, value) ->
                                headersJson.put(key, value)
                            }
                            customData.put("headers", headersJson)
                            customData.put("User-Agent", activeHeaders["User-Agent"])
                            customData.put("Referer", activeHeaders["Referer"])
                        }

                        if (hasDrm && drmConfig != null) {
                            customData.put("drmScheme", "widevine")
                            customData.put("drmLicenseUrl", drmConfig.licenseUri.toString())
                            if (drmConfig.licenseRequestHeaders.isNotEmpty()) {
                                val drmHeadersJson = JSONObject()
                                drmConfig.licenseRequestHeaders.forEach { (k, v) ->
                                    drmHeadersJson.put(k, v)
                                }
                                customData.put("drmHeaders", drmHeadersJson)
                            }
                        }
                        
                        val newMediaInfoBuilder = MediaInfo.Builder(originalMediaInfo.contentId)
                            .setStreamType(originalMediaInfo.streamType)
                            .setContentType(originalMediaInfo.contentType)
                            .setMetadata(originalMediaInfo.metadata)
                            .setMediaTracks(originalMediaInfo.mediaTracks)
                            .setCustomData(customData)
                            
                        val builder = MediaQueueItem.Builder(newMediaInfoBuilder.build())
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
                    return queueItem
                }
                
                override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
                    val defaultItem = defaultConverter.toMediaItem(mediaQueueItem)
                    val customData = mediaQueueItem.media?.customData
                    val drmLicenseUrl = customData?.optString("drmLicenseUrl")
                    if (!drmLicenseUrl.isNullOrEmpty()) {
                        val drmConfigBuilder = MediaItem.DrmConfiguration.Builder(androidx.media3.common.C.WIDEVINE_UUID)
                            .setLicenseUri(drmLicenseUrl)
                        val drmHeadersJson = customData.optJSONObject("drmHeaders")
                        if (drmHeadersJson != null) {
                            val headersMap = mutableMapOf<String, String>()
                            val keys = drmHeadersJson.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                headersMap[k] = drmHeadersJson.getString(k)
                            }
                            drmConfigBuilder.setLicenseRequestHeaders(headersMap)
                        }
                        return defaultItem.buildUpon().setDrmConfiguration(drmConfigBuilder.build()).build()
                    }
                    return defaultItem
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
