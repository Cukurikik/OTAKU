package com.example.data.sources.torrent

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoSource
import com.example.data.sources.VideoStreamInfo
import com.github.se_bastiaan.torrentstream.StreamStatus
import com.github.se_bastiaan.torrentstream.Torrent
import com.github.se_bastiaan.torrentstream.TorrentOptions
import com.github.se_bastiaan.torrentstream.TorrentStream
import com.github.se_bastiaan.torrentstream.listeners.TorrentListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.lang.Exception

class TorrentManager private constructor(context: Context) {
    private val saveLocation: File = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "TorrentStreams")
    
    val torrentStream: TorrentStream

    init {
        if (!saveLocation.exists()) {
            saveLocation.mkdirs()
        }
        val torrentOptions = TorrentOptions.Builder()
            .saveLocation(saveLocation)
            .removeFilesAfterStop(true)
            .autoDownload(true)
            .anonymousMode(true)
            .build()
            
        torrentStream = TorrentStream.init(torrentOptions)
    }

    companion object {
        @Volatile
        private var instance: TorrentManager? = null

        fun getInstance(context: Context): TorrentManager {
            return instance ?: synchronized(this) {
                instance ?: TorrentManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
