package com.example.network.interceptor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ChallengeRequest(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val host: String,
    val userAgent: String,
    val timeoutSeconds: Int = 60,
    val deferredResult: CompletableDeferred<String>
)

/**
 * ChallengeStateManager mengoordinasikan interaksi antara background OkHttp Interceptor
 * dan antarmuka Jetpack Compose (ChallengeBottomSheet).
 * 
 * Mendukung deduplikasi challenge pada host yang sama sehingga tidak memunculkan
 * popup ganda saat terdapat request konkruen.
 */
object ChallengeStateManager {
    private val _activeChallenge = MutableStateFlow<ChallengeRequest?>(null)
    val activeChallenge = _activeChallenge.asStateFlow()

    private val ongoingHostChallenges = ConcurrentHashMap<String, CompletableDeferred<String>>()

    fun requestInteractiveSolver(
        url: String,
        userAgent: String,
        timeoutSeconds: Int = 60
    ): CompletableDeferred<String> {
        val host = try {
            URI(url).host ?: url
        } catch (_: Exception) {
            url
        }

        // Deduplikasi: Jika host ini sedang dalam antrean verifikasi interaktif, gabungkan dengan deferred yang ada
        ongoingHostChallenges[host]?.let { existingDeferred ->
            if (!existingDeferred.isCompleted) {
                return existingDeferred
            }
        }

        val deferred = CompletableDeferred<String>()
        ongoingHostChallenges[host] = deferred

        val request = ChallengeRequest(
            url = url,
            host = host,
            userAgent = userAgent,
            timeoutSeconds = timeoutSeconds,
            deferredResult = deferred
        )

        _activeChallenge.value = request
        return deferred
    }

    fun onChallengeSolved(cookies: String) {
        val current = _activeChallenge.value
        _activeChallenge.value = null
        if (current != null) {
            ongoingHostChallenges.remove(current.host)
            current.deferredResult.complete(cookies)
        }
    }

    fun onChallengeCancelled() {
        val current = _activeChallenge.value
        _activeChallenge.value = null
        if (current != null) {
            ongoingHostChallenges.remove(current.host)
            current.deferredResult.completeExceptionally(
                java.io.IOException("Verifikasi Cloudflare Turnstile dibatalkan oleh pengguna.")
            )
        }
    }
}
