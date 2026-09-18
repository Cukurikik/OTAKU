package com.example.data.sources

sealed interface SourceError {
    data class Network(val message: String, val code: Int? = null) : SourceError
    data class Parsing(val message: String, val url: String) : SourceError
    data class RateLimit(val retryAfter: Long) : SourceError
    data class NotFound(val url: String) : SourceError
    data object Unknown : SourceError
}
