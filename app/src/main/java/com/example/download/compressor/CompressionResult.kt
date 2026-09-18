package com.example.download.compressor

data class CompressionResult(
    val success: Boolean,
    val message: String,
    val originalBytes: Long = 0L,
    val compressedBytes: Long = 0L,
    val bytesSaved: Long = 0L,
    val savingsPercentage: Double = 0.0,
    val outputPath: String? = null,
    val executionTimeMs: Long = 0L
) {
    companion object {
        fun failure(message: String): CompressionResult = CompressionResult(
            success = false,
            message = message
        )
    }
}
