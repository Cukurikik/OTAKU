package com.example.data.sources.direct

enum class ScraperFailureKind {
    OK, DNS, TLS, TIMEOUT, HTTP_404, HTTP_403, HTTP_429, HTTP_5XX, REDIRECT_LOOP, EMPTY_HTML, SELECTOR_DRIFT, PLAYER_MISSING, UNKNOWN
}

data class ScraperHealthState(
    val sourceId: String,
    val checkedAt: Long,
    val reachable: Boolean,
    val failure: ScraperFailureKind = ScraperFailureKind.OK,
    val httpCode: Int? = null,
    val latencyMs: Long? = null,
    val parserHealthy: Boolean = false,
    val playerHealthy: Boolean = false,
    val detail: String = ""
)
