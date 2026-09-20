package com.example.data.sources.registry

import android.content.Context
import com.example.data.sources.SourceBaseUrlStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/** Network-only health probe. It never parses or downloads media. */
class SourceHealthProbe(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    suspend fun check(endpoint: SourceEndpoint): HealthResult {
        if (endpoint.status == EndpointStatus.DEAD || SourceEndpointRegistry.isRawDeadIp(endpoint.baseUrl)) {
            return HealthResult.ERROR("DeadEndpoint", "Endpoint ${endpoint.id} is marked as DEAD or raw dead IP")
        }
        return probeUrl(endpoint.baseUrl)
    }

    suspend fun probeUrl(url: String): HealthResult = withContext(Dispatchers.IO) {
        if (isKnownDeadUrl(url)) {
            return@withContext HealthResult.ERROR("DeadEndpoint", "Endpoint $url is known dead and bypassed (0s delay)")
        }
        val target = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        try {
            val request = Request.Builder()
                .url(target)
                .header("User-Agent", com.example.data.NetworkModule.userAgent)
                .head()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 403) HealthResult.UP
                else if (response.code == 405) {
                    // Method Not Allowed for HEAD, fallback to GET
                    probeGet(target)
                } else HealthResult.HTTP(response.code)
            }
        } catch (headError: Exception) {
            probeGet(target)
        }
    }

    private fun probeGet(url: String): HealthResult {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", com.example.data.NetworkModule.userAgent)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 403) HealthResult.UP
                else HealthResult.HTTP(response.code)
            }
        } catch (error: Exception) {
            HealthResult.ERROR(error::class.simpleName ?: "NetworkError", error.message.orEmpty())
        }
    }

    fun isHealthy(result: HealthResult): Boolean {
        return result is HealthResult.UP || (result is HealthResult.HTTP && result.code in 200..403)
    }

    /**
     * Probes all alternative mirrors for a given source and returns the first healthy one.
     */
    suspend fun findHealthyMirror(sourceId: String, failingUrlOrBase: String = ""): String? = withContext(Dispatchers.IO) {
        val allMirrors = SourceEndpointRegistry.getAllMirrors(sourceId)
        if (allMirrors.isEmpty()) return@withContext null

        val failingHost = runCatching { URI(failingUrlOrBase).host?.lowercase() }.getOrNull()
            ?: failingUrlOrBase.lowercase()

        // Test candidates whose host differs from the failing one first
        val candidateMirrors = allMirrors.filter { mirror ->
            val mHost = runCatching { URI(mirror).host?.lowercase() }.getOrNull() ?: ""
            mHost.isNotBlank() && !failingHost.contains(mHost) && !mHost.contains(failingHost)
        }

        for (candidate in candidateMirrors) {
            val result = probeUrl(candidate)
            if (isHealthy(result)) {
                return@withContext candidate
            }
        }

        // If no alternative candidate passed, test all remaining mirrors as fallback
        for (fallback in allMirrors) {
            if (candidateMirrors.contains(fallback)) continue
            val result = probeUrl(fallback)
            if (isHealthy(result)) {
                return@withContext fallback
            }
        }

        // Return next mirror from registry even if probe couldn't reach, as best effort
        SourceEndpointRegistry.getNextMirror(sourceId, failingUrlOrBase)
    }

    /**
     * Probes alternative mirrors, picks the first healthy one, persists it to
     * [SourceBaseUrlStore], and returns the new base URL.
     */
    suspend fun probeAndFailover(context: Context, sourceId: String, failingUrlOrBase: String = ""): String? {
        val backupDomain = findHealthyMirror(sourceId, failingUrlOrBase)
        if (!backupDomain.isNullOrBlank()) {
            SourceBaseUrlStore.save(context, sourceId, backupDomain)
            return backupDomain
        }
        return null
    }

    companion object {
        /**
         * Returns true if the URL or host corresponds to a known dead raw IP or dead endpoint.
         */
        fun isKnownDeadUrl(url: String): Boolean {
            if (SourceEndpointRegistry.isRawDeadIp(url)) return true
            val ep = SourceEndpointRegistry.findByHostOrUrl(url)
            return ep != null && ep.status == EndpointStatus.DEAD
        }

        /**
         * Returns true if the given endpoint URL is considered healthy/available without hitting known dead servers.
         */
        fun isHealthy(endpointUrl: String): Boolean {
            return !isKnownDeadUrl(endpointUrl)
        }

        /**
         * Replaces the domain (scheme + host + port) of [originalUrl] with the base domain of [newBaseUrl],
         * preserving the raw path, query, and fragment.
         */
        fun rewriteUrl(originalUrl: String, newBaseUrl: String): String {
            return try {
                val origUri = URI(originalUrl)
                val path = origUri.rawPath ?: ""
                val query = origUri.rawQuery?.let { "?$it" } ?: ""
                val fragment = origUri.rawFragment?.let { "#$it" } ?: ""
                val cleanBase = newBaseUrl.trimEnd('/')
                val cleanPath = if (path.startsWith("/")) path else "/$path"
                "$cleanBase$cleanPath$query$fragment"
            } catch (e: Exception) {
                newBaseUrl.trimEnd('/') + "/"
            }
        }
    }
}

sealed interface HealthResult {
    data object UP : HealthResult
    data class HTTP(val code: Int) : HealthResult
    data class ERROR(val type: String, val message: String) : HealthResult
}
