package com.example.data.sources

/**
 * Legacy VideoExtractor wrapper.
 * All core extraction logic has been unified into [VideoExtractorEngine].
 * This object forwards calls to [VideoExtractorEngine] for backward compatibility.
 */
object VideoExtractor {

    private val engine = VideoExtractorEngine()

    fun createHeaders(url: String, referer: String = ""): Map<String, String> {
        return VideoExtractorEngine.createHeaders(url, referer)
    }

    suspend fun extract(embedUrl: String, referer: String = ""): VideoStreamInfo? {
        return engine.extractVideo(embedUrl, referer)
    }
}
