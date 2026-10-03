package com.example.download.muxing

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * CustomNioFileAppender:
 * High-performance, low-level NIO File Channel byte appender for direct video stream muxing.
 *
 * Implements:
 * - O(1) Sequential Direct-to-Disk write overhead without temporary file intermediaries.
 * - Thread-safe channel appending with zero-copy ByteBuffer streaming.
 * - Random-access write capabilities for in-place Moov Atom and header reconstruction.
 */
class CustomNioFileAppender(
    val targetFile: File,
    val bufferCapacityBytes: Int = 128 * 1024 // 128 KB chunk buffer
) : AutoCloseable {

    private val lock = ReentrantLock()
    private var randomAccessFile: RandomAccessFile? = null
    private var fileChannel: FileChannel? = null
    private var totalBytesWritten: Long = 0L
    private var isClosed = false

    init {
        targetFile.parentFile?.mkdirs()
        randomAccessFile = RandomAccessFile(targetFile, "rw")
        fileChannel = randomAccessFile?.channel
        // Seek to end if appending to existing partial file
        fileChannel?.position(targetFile.length())
        totalBytesWritten = targetFile.length()
    }

    /**
     * Appends a byte array directly to the underlying FileChannel.
     */
    fun appendBytes(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Long = lock.withLock {
        ensureOpen()
        val channel = fileChannel ?: throw IllegalStateException("FileChannel is not initialized")
        val byteBuffer = ByteBuffer.wrap(bytes, offset, length)
        var written = 0
        while (byteBuffer.hasRemaining()) {
            written += channel.write(byteBuffer)
        }
        totalBytesWritten += written
        return written.toLong()
    }

    /**
     * Appends a ByteBuffer directly into the channel.
     */
    fun appendBuffer(buffer: ByteBuffer): Long = lock.withLock {
        ensureOpen()
        val channel = fileChannel ?: throw IllegalStateException("FileChannel is not initialized")
        var written = 0
        while (buffer.hasRemaining()) {
            written += channel.write(buffer)
        }
        totalBytesWritten += written
        return written.toLong()
    }

    /**
     * Writes bytes at a specific absolute position in the file without moving the append cursor.
     * Useful for updating header offsets, container lengths, and Moov Atom pointers.
     */
    fun writeAtPosition(position: Long, bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Long = lock.withLock {
        ensureOpen()
        val channel = fileChannel ?: throw IllegalStateException("FileChannel is not initialized")
        val currentPos = channel.position()
        channel.position(position)
        val byteBuffer = ByteBuffer.wrap(bytes, offset, length)
        var written = 0
        while (byteBuffer.hasRemaining()) {
            written += channel.write(byteBuffer)
        }
        // Restore cursor position
        channel.position(currentPos)
        return written.toLong()
    }

    /**
     * Flushes channel buffers to disk storage.
     */
    fun flush(forceMetadataSync: Boolean = false) = lock.withLock {
        if (!isClosed && fileChannel?.isOpen == true) {
            fileChannel?.force(forceMetadataSync)
        }
    }

    fun size(): Long = lock.withLock {
        return if (!isClosed && fileChannel?.isOpen == true) {
            fileChannel?.size() ?: totalBytesWritten
        } else {
            targetFile.length()
        }
    }

    fun getBytesWritten(): Long = totalBytesWritten

    private fun ensureOpen() {
        if (isClosed || fileChannel == null || !fileChannel!!.isOpen) {
            throw IllegalStateException("CustomNioFileAppender for '${targetFile.name}' is already closed.")
        }
    }

    override fun close() = lock.withLock {
        if (!isClosed) {
            try {
                flush(true)
                fileChannel?.close()
                randomAccessFile?.close()
            } catch (_: Exception) {
            } finally {
                fileChannel = null
                randomAccessFile = null
                isClosed = true
            }
        }
    }
}
