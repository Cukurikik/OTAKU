package com.example.data.torrent

import android.content.Context
import android.util.Log
import com.github.se_bastiaan.torrentstream.StreamStatus
import com.github.se_bastiaan.torrentstream.Torrent
import com.github.se_bastiaan.torrentstream.TorrentOptions
import com.github.se_bastiaan.torrentstream.TorrentStream
import com.github.se_bastiaan.torrentstream.listeners.TorrentListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.lang.Exception

object TorrentStreamManager : TorrentListener {
    private var torrentStream: TorrentStream? = null
    private var currentTorrent: Torrent? = null

    private val _streamUrl = MutableStateFlow<String?>(null)
    val streamUrl: StateFlow<String?> = _streamUrl.asStateFlow()

    private val _progress = MutableStateFlow<Float>(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _downloadSpeed = MutableStateFlow<Float>(0f)
    val downloadSpeed: StateFlow<Float> = _downloadSpeed.asStateFlow()

    private val _seeds = MutableStateFlow<Int>(0)
    val seeds: StateFlow<Int> = _seeds.asStateFlow()

    fun init(context: Context) {
        if (torrentStream != null) return
        
        val saveLocation = File(context.getExternalFilesDir(null), "TorrentStreams")
        if (!saveLocation.exists()) {
            saveLocation.mkdirs()
        }

        val torrentOptions = TorrentOptions.Builder()
            .saveLocation(saveLocation)
            .removeFilesAfterStop(true)
            .autoDownload(true)
            .build()

        torrentStream = TorrentStream.init(torrentOptions)
        torrentStream?.addListener(this)
    }

    fun startStream(magnetUrl: String) {
        _streamUrl.value = null
        _progress.value = 0f
        _downloadSpeed.value = 0f
        _seeds.value = 0
        torrentStream?.startStream(magnetUrl)
    }

    fun stopStream() {
        if (torrentStream?.isStreaming == true) {
            torrentStream?.stopStream()
        }
        currentTorrent = null
        _streamUrl.value = null
    }

    fun getCurrentTorrent(): Torrent? = currentTorrent

    override fun onStreamPrepared(torrent: Torrent) {
        Log.d("TorrentStreamManager", "Stream prepared: ${torrent.videoFile?.name}")
        currentTorrent = torrent
        torrent.startDownload()
    }

    override fun onStreamStarted(torrent: Torrent) {
        Log.d("TorrentStreamManager", "Stream started")
    }

    override fun onStreamError(torrent: Torrent, e: Exception) {
        Log.e("TorrentStreamManager", "Stream error", e)
    }

    override fun onStreamReady(torrent: Torrent) {
        Log.d("TorrentStreamManager", "Stream ready")
        _streamUrl.value = "torrent://stream"
    }

    override fun onStreamProgress(torrent: Torrent, status: StreamStatus) {
        _progress.value = status.progress
        _downloadSpeed.value = status.downloadSpeed.toFloat()
        _seeds.value = status.seeds
    }

    override fun onStreamStopped() {
        Log.d("TorrentStreamManager", "Stream stopped")
    }
}
