package com.example.data.torrent

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

@androidx.media3.common.util.UnstableApi
class TorrentDataSource(private val torrentStreamManager: TorrentStreamManager) : BaseDataSource(/* isNetwork = */ false) {

    private var inputStream: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        try {
            uri = dataSpec.uri
            transferInitializing(dataSpec)

            val torrent = torrentStreamManager.getCurrentTorrent()
                ?: throw IOException("No active torrent")
                
            val file = torrent.videoFile ?: throw IOException("No video file")
            val totalLength = file.length()
            
            val stream = torrent.videoStream
            inputStream = stream

            // Skip to the requested position
            var skipped = 0L
            while (skipped < dataSpec.position) {
                val toSkip = dataSpec.position - skipped
                val skippedThisTime = stream.skip(toSkip)
                if (skippedThisTime <= 0L) {
                    if (stream.read() == -1) {
                        throw EOFException()
                    }
                    skipped++
                } else {
                    skipped += skippedThisTime
                }
            }

            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                totalLength - dataSpec.position
            }

            opened = true
            transferStarted(dataSpec)

            return bytesRemaining
        } catch (e: IOException) {
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length else min(length.toLong(), bytesRemaining).toInt()
        
        return try {
            val bytesRead = inputStream!!.read(buffer, offset, bytesToRead)
            if (bytesRead == -1) {
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                    throw EOFException()
                }
                C.RESULT_END_OF_INPUT
            } else {
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                    bytesRemaining -= bytesRead
                }
                bytesTransferred(bytesRead)
                bytesRead
            }
        } catch (e: IOException) {
            throw e
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            inputStream?.close()
        } catch (e: IOException) {
            // ignore
        } finally {
            inputStream = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}
