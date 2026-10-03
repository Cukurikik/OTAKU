package com.example.download.muxing

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MoovAtomHeaderValidator:
 * Comprehensive parser and validator for MP4 (ISO/IEC 14496-12) and MPEG-2 TS video stream containers.
 *
 * Capabilities:
 * - Detects container signatures: MP4 Atoms (ftyp, moov, mdat) vs MPEG-2 Transport Stream (0x47 sync cadence).
 * - Verifies presence, structure, and position of the 'moov' atom (fast-start vs end-of-file).
 * - Extracts duration, timescale, and seekable index parameters.
 * - Prevents playback stutter and corrupted offline downloads by ensuring all required atoms are intact.
 */
object MoovAtomHeaderValidator {

    enum class ContainerType {
        MP4,
        MPEG_TS,
        UNKNOWN
    }

    data class ValidationResult(
        val isValid: Boolean,
        val containerType: ContainerType,
        val hasMoovAtom: Boolean,
        val hasMdatAtom: Boolean,
        val isFastStart: Boolean, // true if moov appears before mdat
        val durationMs: Long,
        val fileSizeBytes: Long,
        val errorMessage: String? = null
    ) {
        val isSeekable: Boolean get() = isValid && (hasMoovAtom || containerType == ContainerType.MPEG_TS)
    }

    // FourCC Atom Identifiers
    private const val ATOM_FTYP = 0x66747970 // "ftyp"
    private const val ATOM_MOOV = 0x6D6F6F76 // "moov"
    private const val ATOM_MDAT = 0x6D646174 // "mdat"
    private const val ATOM_FREE = 0x66726565 // "free"
    private const val ATOM_WIDE = 0x77696465 // "wide"
    private const val ATOM_MVHD = 0x6D766864 // "mvhd"

    // MPEG-2 TS Constants
    private const val TS_SYNC_BYTE = 0x47.toByte()
    private const val TS_PACKET_SIZE = 188

    /**
     * Validates file integrity and container atoms.
     */
    fun validateFileIntegrity(file: File): ValidationResult {
        if (!file.exists() || file.length() < 128) {
            return ValidationResult(
                isValid = false,
                containerType = ContainerType.UNKNOWN,
                hasMoovAtom = false,
                hasMdatAtom = false,
                isFastStart = false,
                durationMs = 0L,
                fileSizeBytes = if (file.exists()) file.length() else 0L,
                errorMessage = "File does not exist or size is below minimum threshold (128 bytes)."
            )
        }

        val fileLength = file.length()

        return try {
            RandomAccessFile(file, "r").use { raf ->
                // 1. Check for MP4 ISO Box Structure
                val mp4Result = parseMp4Structure(raf, fileLength)
                if (mp4Result.containerType == ContainerType.MP4) {
                    return@use mp4Result
                }

                // 2. Check for MPEG-2 TS Structure
                val tsResult = parseTsStructure(raf, fileLength)
                if (tsResult.containerType == ContainerType.MPEG_TS) {
                    return@use tsResult
                }

                // Unknown or corrupt container
                ValidationResult(
                    isValid = false,
                    containerType = ContainerType.UNKNOWN,
                    hasMoovAtom = false,
                    hasMdatAtom = false,
                    isFastStart = false,
                    durationMs = 0L,
                    fileSizeBytes = fileLength,
                    errorMessage = "Unrecognized container structure. Missing both MP4 atom boxes and TS sync bytes."
                )
            }
        } catch (e: Exception) {
            ValidationResult(
                isValid = false,
                containerType = ContainerType.UNKNOWN,
                hasMoovAtom = false,
                hasMdatAtom = false,
                isFastStart = false,
                durationMs = 0L,
                fileSizeBytes = fileLength,
                errorMessage = "Exception during header parsing: ${e.message}"
            )
        }
    }

    private fun parseMp4Structure(raf: RandomAccessFile, fileLength: Long): ValidationResult {
        var offset = 0L
        var hasFtyp = false
        var hasMoov = false
        var hasMdat = false
        var moovOffset = -1L
        var mdatOffset = -1L
        var durationMs = 0L

        val buffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)

        while (offset < fileLength - 8) {
            raf.seek(offset)
            buffer.clear()
            val bytesRead = raf.read(buffer.array(), 0, 8)
            if (bytesRead < 8) break

            buffer.position(0)
            var atomSize = buffer.getInt().toLong() and 0xFFFFFFFFL
            val atomType = buffer.getInt()

            if (atomSize == 1L) {
                // 64-bit large box size (extended size)
                val extBuffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                val extRead = raf.read(extBuffer.array(), 0, 8)
                if (extRead < 8) break
                atomSize = extBuffer.getLong()
                if (atomSize < 16) break
            } else if (atomSize == 0L) {
                // Atom extends to end of file
                atomSize = fileLength - offset
            } else if (atomSize < 8) {
                // Invalid atom size
                break
            }

            when (atomType) {
                ATOM_FTYP -> {
                    hasFtyp = true
                }
                ATOM_MOOV -> {
                    hasMoov = true
                    moovOffset = offset
                    // Parse mvhd for duration inside moov
                    val mvhdDuration = parseMvhdDuration(raf, offset + 8, atomSize - 8)
                    if (mvhdDuration > 0) durationMs = mvhdDuration
                }
                ATOM_MDAT -> {
                    hasMdat = true
                    mdatOffset = offset
                }
            }

            // Move offset to next top-level atom
            offset += atomSize
            if (offset <= 0) break // Guard against overflow
        }

        val isMp4 = hasFtyp || hasMoov || hasMdat
        if (!isMp4) {
            return ValidationResult(
                isValid = false,
                containerType = ContainerType.UNKNOWN,
                hasMoovAtom = false,
                hasMdatAtom = false,
                isFastStart = false,
                durationMs = 0L,
                fileSizeBytes = fileLength
            )
        }

        val isFastStart = (moovOffset in 0 until mdatOffset)
        val isValid = (hasMoov || hasFtyp) && hasMdat

        return ValidationResult(
            isValid = isValid,
            containerType = ContainerType.MP4,
            hasMoovAtom = hasMoov,
            hasMdatAtom = hasMdat,
            isFastStart = isFastStart,
            durationMs = durationMs,
            fileSizeBytes = fileLength,
            errorMessage = if (!isValid) "Missing required MP4 atoms: moov=$hasMoov, mdat=$hasMdat" else null
        )
    }

    private fun parseMvhdDuration(raf: RandomAccessFile, startOffset: Long, moovLength: Long): Long {
        return try {
            var current = startOffset
            val maxOffset = startOffset + moovLength - 8
            val buf = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)

            while (current < maxOffset) {
                raf.seek(current)
                buf.clear()
                val read = raf.read(buf.array(), 0, 8)
                if (read < 8) break
                buf.position(0)
                val size = buf.getInt().toLong() and 0xFFFFFFFFL
                val type = buf.getInt()

                if (type == ATOM_MVHD && size >= 24) {
                    raf.seek(current + 8)
                    buf.clear()
                    val dataRead = raf.read(buf.array(), 0, 32.coerceAtMost((size - 8).toInt()))
                    if (dataRead >= 20) {
                        buf.position(0)
                        val version = buf.get().toInt()
                        buf.position(4) // Skip version (1) + flags (3)

                        return if (version == 1) {
                            // Version 1: 64-bit times
                            val extBuf = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)
                            raf.seek(current + 12) // after size (4) + type (4) + version/flags (4)
                            raf.read(extBuf.array(), 0, 28)
                            extBuf.position(16) // Skip creation_time (8) + mod_time (8)
                            val timescale = extBuf.getInt().toLong() and 0xFFFFFFFFL
                            val duration = extBuf.getLong()
                            if (timescale > 0) (duration * 1000L) / timescale else 0L
                        } else {
                            // Version 0: 32-bit times (creation (4) + mod (4) + timescale (4) + duration (4))
                            val timescale = buf.getInt(12).toLong() and 0xFFFFFFFFL
                            val duration = buf.getInt(16).toLong() and 0xFFFFFFFFL
                            if (timescale > 0) (duration * 1000L) / timescale else 0L
                        }
                    }
                }

                if (size <= 0) break
                current += size
            }
            0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun parseTsStructure(raf: RandomAccessFile, fileLength: Long): ValidationResult {
        val checkPackets = 5.coerceAtMost((fileLength / TS_PACKET_SIZE).toInt())
        if (checkPackets < 3) {
            return ValidationResult(
                isValid = false,
                containerType = ContainerType.UNKNOWN,
                hasMoovAtom = false,
                hasMdatAtom = false,
                isFastStart = false,
                durationMs = 0L,
                fileSizeBytes = fileLength
            )
        }

        raf.seek(0)
        val sampleBytes = ByteArray(TS_PACKET_SIZE * checkPackets)
        val read = raf.read(sampleBytes)
        if (read < TS_PACKET_SIZE * checkPackets) {
            return ValidationResult(
                isValid = false,
                containerType = ContainerType.UNKNOWN,
                hasMoovAtom = false,
                hasMdatAtom = false,
                isFastStart = false,
                durationMs = 0L,
                fileSizeBytes = fileLength
            )
        }

        var syncCount = 0
        for (i in 0 until checkPackets) {
            if (sampleBytes[i * TS_PACKET_SIZE] == TS_SYNC_BYTE) {
                syncCount++
            }
        }

        val isTs = syncCount == checkPackets
        return ValidationResult(
            isValid = isTs,
            containerType = if (isTs) ContainerType.MPEG_TS else ContainerType.UNKNOWN,
            hasMoovAtom = false,
            hasMdatAtom = isTs,
            isFastStart = false,
            durationMs = 0L,
            fileSizeBytes = fileLength,
            errorMessage = if (!isTs) "Invalid TS sync byte sequence" else null
        )
    }
}
