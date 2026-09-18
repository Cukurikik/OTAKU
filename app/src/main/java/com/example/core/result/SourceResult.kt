package com.example.core.result

sealed interface SourceResult<out T> {
    data class Success<T>(val value: T) : SourceResult<T>
    data class Failure(val error: SourceError) : SourceResult<Nothing>
}

sealed class SourceError(open val message: String, open val throwable: Throwable? = null) {
    data class Network(override val message: String = "Network error", override val throwable: Throwable? = null) : SourceError(message, throwable)
    data class Parse(override val message: String = "Parse error", override val throwable: Throwable? = null) : SourceError(message, throwable)
    data class RateLimited(override val message: String = "Rate limited", override val throwable: Throwable? = null) : SourceError(message, throwable)
    data class NotFound(override val message: String = "Not found", override val throwable: Throwable? = null) : SourceError(message, throwable)
    data class Cancelled(override val message: String = "Operation cancelled", override val throwable: Throwable? = null) : SourceError(message, throwable)
    data class Unknown(override val message: String = "Unknown error", override val throwable: Throwable? = null) : SourceError(message, throwable)
}
