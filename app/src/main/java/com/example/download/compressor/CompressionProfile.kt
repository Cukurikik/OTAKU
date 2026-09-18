package com.example.download.compressor

/**
 * Profil kompresi offline video:
 * - ORIGINAL: H.264 / AAC (100% ukuran, tanpa re-encoding)
 * - SPACE_SAVER: HEVC / H.265 CRF 28 + Opus 96k (~30% ukuran, ~70% hemat ruang, Visually Lossless)
 * - ULTRA_COMPACT: AV1 / HEVC CRF 32 + Opus 64k (~20% ukuran, ~80% hemat ruang, Kompresi Maksimal)
 */
enum class CompressionProfile(
    val id: String,
    val displayName: String,
    val codecBadge: String,
    val estimatedRatio: Float,
    val qualityLabel: String,
    val videoCodec: String,
    val crf: Int,
    val audioCodec: String,
    val audioBitrate: String,
    val preset: String
) {
    ORIGINAL(
        id = "original",
        displayName = "Asli (Original)",
        codecBadge = "H.264 / AAC",
        estimatedRatio = 1.0f,
        qualityLabel = "Source Original (100%)",
        videoCodec = "copy",
        crf = 0,
        audioCodec = "copy",
        audioBitrate = "",
        preset = ""
    ),
    SPACE_SAVER(
        id = "space_saver",
        displayName = "Hemat Ruang",
        codecBadge = "HEVC (CRF 28) / Opus",
        estimatedRatio = 0.30f,
        qualityLabel = "Visually Lossless (~70% Hemat)",
        videoCodec = "libx265",
        crf = 28,
        audioCodec = "libopus",
        audioBitrate = "96k",
        preset = "medium"
    ),
    ULTRA_COMPACT(
        id = "ultra_compact",
        displayName = "Sangat Padat",
        codecBadge = "AV1 / HEVC (CRF 32) / Opus",
        estimatedRatio = 0.20f,
        qualityLabel = "Efficient Compression (~80% Hemat)",
        videoCodec = "libx265",
        crf = 32,
        audioCodec = "libopus",
        audioBitrate = "64k",
        preset = "fast"
    );

    /**
     * Menghitung estimasi ruang yang dihemat berdasarkan ukuran file asal.
     * Formulasi: (1 - Size_HEVC / Size_H264) * 100%
     */
    fun calculateEstimatedSavedBytes(originalBytes: Long): Long {
        if (this == ORIGINAL) return 0L
        val compressedEstimate = (originalBytes * estimatedRatio).toLong()
        return (originalBytes - compressedEstimate).coerceAtLeast(0L)
    }

    /**
     * Persentase penghematan teoretis
     */
    val savingsPercentageText: String
        get() = when (this) {
            ORIGINAL -> "0%"
            SPACE_SAVER -> "~70%"
            ULTRA_COMPACT -> "~80%"
        }
}
