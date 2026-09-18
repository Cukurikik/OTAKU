package com.example.casting

import android.content.Context
import android.util.Log
import com.example.network.server.LocalMediaServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Universal CastHelper:
 * Mengoordinasikan penemuan perangkat TV (DLNA / UPnP SSDP), penyajian stream lokal
 * melalui LocalMediaServer (NanoHTTPD), dan kontrol pemutaran jarak jauh melalui DlnaCastManager.
 */
class CastHelper(
    private val context: Context
) {
    companion object {
        private const val TAG = "UniversalCastHelper"

        @Volatile
        private var instance: CastHelper? = null

        fun getInstance(context: Context): CastHelper {
            return instance ?: synchronized(this) {
                instance ?: CastHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val discoveryManager = SsdpDiscoveryManager(context)
    val mediaServer = LocalMediaServer.getInstance()

    private val _activeDlnaDevice = MutableStateFlow<DlnaDevice?>(null)
    val activeDlnaDevice: StateFlow<DlnaDevice?> = _activeDlnaDevice.asStateFlow()

    private val _playbackState = MutableStateFlow(DlnaPlaybackState())
    val playbackState: StateFlow<DlnaPlaybackState> = _playbackState.asStateFlow()

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private var pollingJob: Job? = null
    private var currentMediaId: String? = null

    /**
     * Memulai pemindaian perangkat Smart TV pada jaringan lokal via SSDP.
     */
    fun startDiscovery(timeoutSeconds: Int = 6) {
        discoveryManager.startScan(timeoutSeconds)
    }

    /**
     * Menghentikan pemindaian SSDP.
     */
    fun stopDiscovery() {
        discoveryManager.stopScan()
    }

    /**
     * Memutar video lokal (.mp4, .mkv, .m3u8) ke Smart TV target.
     */
    fun castLocalFile(
        device: DlnaDevice,
        file: File,
        title: String,
        mimeType: String = "video/mp4",
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        scope.launch {
            try {
                ensureServerRunning()
                val mediaId = mediaServer.registerLocalFile(file, mimeType)
                currentMediaId = mediaId
                val localStreamUrl = mediaServer.getMediaUrl(context, mediaId)

                Log.d(TAG, "Menyiapkan stream lokal: $localStreamUrl untuk TV: ${device.name}")
                val result = DlnaCastManager.playMediaOnTv(
                    controlUrl = device.controlUrl,
                    mediaUrl = localStreamUrl,
                    title = title,
                    mimeType = mimeType
                )

                if (result.isSuccess) {
                    _activeDlnaDevice.value = device
                    _isCasting.value = true
                    startPlaybackPolling(device.controlUrl)
                    onSuccess?.invoke()
                } else {
                    onError?.invoke(result.exceptionOrNull() ?: Exception("Gagal memutar di TV"))
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Exception castLocalFile: ${e.message}", e)
                onError?.invoke(e)
            }
        }
    }

    /**
     * Memutar remote stream (.m3u8 atau .mp4 online dengan anti-hotlinking headers) ke Smart TV target.
     */
    fun castRemoteStream(
        device: DlnaDevice,
        remoteUrl: String,
        title: String,
        mimeType: String = "video/mp4",
        headers: Map<String, String> = emptyMap(),
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        scope.launch {
            try {
                ensureServerRunning()
                val mediaId = mediaServer.registerRemoteRelay(remoteUrl, mimeType, headers)
                currentMediaId = mediaId
                val localRelayUrl = mediaServer.getMediaUrl(context, mediaId)

                Log.d(TAG, "Menyiapkan relay stream: $localRelayUrl untuk TV: ${device.name}")
                val result = DlnaCastManager.playMediaOnTv(
                    controlUrl = device.controlUrl,
                    mediaUrl = localRelayUrl,
                    title = title,
                    mimeType = mimeType
                )

                if (result.isSuccess) {
                    _activeDlnaDevice.value = device
                    _isCasting.value = true
                    startPlaybackPolling(device.controlUrl)
                    onSuccess?.invoke()
                } else {
                    onError?.invoke(result.exceptionOrNull() ?: Exception("Gagal memutar di TV"))
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Exception castRemoteStream: ${e.message}", e)
                onError?.invoke(e)
            }
        }
    }

    /**
     * Memutar halaman komik .cbz ke Smart TV target.
     */
    fun castComicPage(
        device: DlnaDevice,
        cbzFile: File,
        pageIndex: Int,
        title: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        scope.launch {
            try {
                ensureServerRunning()
                val mediaId = mediaServer.registerComicPage(cbzFile, pageIndex)
                currentMediaId = mediaId
                val pageUrl = mediaServer.getMediaUrl(context, mediaId)

                val result = DlnaCastManager.playMediaOnTv(
                    controlUrl = device.controlUrl,
                    mediaUrl = pageUrl,
                    title = "$title - Hal ${pageIndex + 1}",
                    mimeType = "image/jpeg"
                )

                if (result.isSuccess) {
                    _activeDlnaDevice.value = device
                    _isCasting.value = true
                    onSuccess?.invoke()
                } else {
                    onError?.invoke(result.exceptionOrNull() ?: Exception("Gagal menampilkan komik di TV"))
                }
            } catch (e: Throwable) {
                onError?.invoke(e)
            }
        }
    }

    /**
     * Pause media di TV.
     */
    fun pause() {
        val device = _activeDlnaDevice.value ?: return
        scope.launch {
            DlnaCastManager.pause(device.controlUrl)
        }
    }

    /**
     * Resume media di TV.
     */
    fun resume() {
        val device = _activeDlnaDevice.value ?: return
        scope.launch {
            DlnaCastManager.resume(device.controlUrl)
        }
    }

    /**
     * Stop media di TV dan lepaskan registrasi server lokal.
     */
    fun stop() {
        val device = _activeDlnaDevice.value
        scope.launch {
            if (device != null) {
                try {
                    DlnaCastManager.stop(device.controlUrl)
                } catch (_: Exception) {}
            }
            disconnect()
        }
    }

    /**
     * Seeking posisi di TV (milidetik).
     */
    fun seekTo(positionMs: Long) {
        val device = _activeDlnaDevice.value ?: return
        scope.launch {
            DlnaCastManager.seek(device.controlUrl, positionMs)
        }
    }

    /**
     * Mengatur volume pada TV (0 - 100).
     */
    fun setVolume(volumePercent: Int) {
        val device = _activeDlnaDevice.value ?: return
        val renderingUrl = device.renderingControlUrl ?: return
        scope.launch {
            DlnaCastManager.setVolume(renderingUrl, volumePercent)
        }
    }

    /**
     * Memutuskan koneksi casting dan menghentikan polling.
     */
    fun disconnect() {
        pollingJob?.cancel()
        pollingJob = null
        currentMediaId?.let { mediaServer.unregisterMedia(it) }
        currentMediaId = null
        _activeDlnaDevice.value = null
        _isCasting.value = false
        _playbackState.value = DlnaPlaybackState()
    }

    private fun ensureServerRunning() {
        if (!mediaServer.isAlive) {
            try {
                mediaServer.start()
                Log.i(TAG, "LocalMediaServer berhasil dijalankan pada port ${mediaServer.listeningPort}")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menjalankan LocalMediaServer: ${e.message}")
            }
        }
    }

    private fun startPlaybackPolling(controlUrl: String) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive && _isCasting.value) {
                delay(1500)
                val infoResult = DlnaCastManager.getPositionInfo(controlUrl)
                if (infoResult.isSuccess) {
                    val state = infoResult.getOrNull()
                    if (state != null) {
                        _playbackState.value = state
                    }
                }
            }
        }
    }
}
