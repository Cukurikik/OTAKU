package com.example.network.domain

import android.content.Context
import com.example.data.NetworkModule
import com.example.data.sources.SourceBaseUrlStore
import com.example.network.dns.CloudflareDoHResolver
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * DynamicDomainResolverEngine:
 * Intelligent domain resolution & auto-healing system for anime, manga, and drama sources.
 *
 * Implements Formulations:
 * 1. Domain_active = arg min_{d in Mirrors} { Latency(d) | HTTP_Status(d) = 200 AND DoH_Valid(d) }
 * 2. Trout_max = sum_{k=1}^N Timeout_k <= 4500 ms
 */
class DynamicDomainResolverEngine(
    val sourceId: String,
    val mirrorDomains: List<String>,
    val defaultBaseUrl: String = mirrorDomains.firstOrNull() ?: "",
    val workerMetadataUrl: String = "https://anime-aggregator.njirlah.workers.dev/domains",
    private val dohResolver: CloudflareDoHResolver = CloudflareDoHResolver.instance
) {

    private val cachedActiveDomain = ConcurrentHashMap<String, CachedDomain>()

    data class CachedDomain(
        val domain: String,
        val latencyMs: Long,
        val timestamp: Long
    )

    data class ProbeResult(
        val domain: String,
        val isSuccessful: Boolean,
        val statusCode: Int,
        val isDoHValid: Boolean,
        val latencyMs: Long
    )

    companion object {
        const val MAX_TOTAL_RESOLUTION_TIMEOUT_MS = 4500L // Trout_max <= 4500 ms
        const val CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes TTL

        private val engineRegistry = ConcurrentHashMap<String, DynamicDomainResolverEngine>()

        fun getOrCreate(
            sourceId: String,
            mirrors: List<String>,
            defaultBaseUrl: String = mirrors.firstOrNull() ?: ""
        ): DynamicDomainResolverEngine {
            return engineRegistry.computeIfAbsent(sourceId) {
                DynamicDomainResolverEngine(sourceId, mirrors, defaultBaseUrl)
            }
        }
    }

    /**
     * Retrieves the best valid active domain.
     * Evaluates cache, worker metadata, and runs latency/health benchmark.
     */
    suspend fun getValidBaseUrl(
        context: Context? = null,
        client: OkHttpClient = NetworkModule.okHttpClient
    ): String = withContext(Dispatchers.IO) {
        // 1. Check in-memory cache
        val cached = cachedActiveDomain[sourceId]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS) {
            return@withContext cached.domain
        }

        // 2. Check persistent store if context is available
        if (context != null) {
            val stored = SourceBaseUrlStore.get(context, sourceId, "")
            if (stored.isNotBlank() && isHostHealthyQuick(client, stored)) {
                cacheDomain(stored, 50L)
                return@withContext stored
            }
        }

        // 3. Resolve best domain from mirrors + worker metadata
        val allCandidateDomains = collectCandidateDomains(client)
        val bestDomain = resolveBestActiveDomain(client, allCandidateDomains)

        if (bestDomain != null) {
            cacheDomain(bestDomain.domain, bestDomain.latencyMs)
            if (context != null) {
                SourceBaseUrlStore.save(context, sourceId, bestDomain.domain)
            }
            return@withContext bestDomain.domain
        }

        // Fallback to default base URL if provided
        if (defaultBaseUrl.isNotBlank()) {
            return@withContext defaultBaseUrl.trimEnd('/')
        }

        throw IOException("All mirror domains for source '$sourceId' are unreachable within ${MAX_TOTAL_RESOLUTION_TIMEOUT_MS}ms.")
    }

    /**
     * Resolves the domain with the lowest latency satisfying HTTP 200 and DoH resolution.
     * Formulasi: Domain_active = arg min_{d in Mirrors} { Latency(d) | HTTP_Status(d) = 200 AND DoH_Valid(d) }
     * Trout_max <= 4500ms
     */
    suspend fun resolveBestActiveDomain(
        client: OkHttpClient,
        candidates: List<String>
    ): ProbeResult? = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext null

        val perCandidateTimeoutMs = (MAX_TOTAL_RESOLUTION_TIMEOUT_MS / candidates.size.coerceAtLeast(1))
            .coerceIn(800L, 2000L)

        // Run parallel probes with timeout constraint
        val probeDeferreds = candidates.map { domain ->
            async {
                probeDomain(client, domain, perCandidateTimeoutMs)
            }
        }

        val results = withTimeoutOrNull(MAX_TOTAL_RESOLUTION_TIMEOUT_MS) {
            probeDeferreds.awaitAll()
        } ?: probeDeferreds.mapNotNull { if (it.isCompleted) it.getCompleted() else null }

        // Filter valid candidates: HTTP 200 (or acceptable 403 for Cloudflare protected) and DoH Valid
        val validResults = results.filter { it.isSuccessful && it.isDoHValid }

        // arg min Latency(d)
        validResults.minByOrNull { it.latencyMs }
            ?: results.firstOrNull { it.isSuccessful } // Fallback to first reachable
            ?: results.firstOrNull { it.statusCode in 200..403 }
    }

    /**
     * Probes an individual candidate domain.
     */
    private suspend fun probeDomain(
        client: OkHttpClient,
        domain: String,
        timeoutMs: Long
    ): ProbeResult = withContext(Dispatchers.IO) {
        val sanitizedUrl = if (domain.startsWith("http://") || domain.startsWith("https://")) domain else "https://$domain"
        val host = runCatching { URI(sanitizedUrl).host }.getOrNull() ?: ""

        val isDoH = if (host.isNotBlank()) dohResolver.isDoHValid(host) else true
        var statusCode = 0
        var isSuccess = false

        val latencyMs = measureTimeMillis {
            try {
                val probeClient = client.newBuilder()
                    .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .build()

                val request = Request.Builder()
                    .url(sanitizedUrl)
                    .header("User-Agent", NetworkModule.userAgent)
                    .head()
                    .build()

                probeClient.newCall(request).execute().use { response ->
                    statusCode = response.code
                    isSuccess = response.isSuccessful || response.code == 403 // 403 indicates host is up and responding (WAF/Cloudflare)
                }
            } catch (_: Exception) {
                // Try lightweight GET probe if HEAD method is not allowed
                try {
                    val getRequest = Request.Builder()
                        .url(sanitizedUrl)
                        .header("User-Agent", NetworkModule.userAgent)
                        .get()
                        .build()
                    client.newCall(getRequest).execute().use { resp ->
                        statusCode = resp.code
                        isSuccess = resp.isSuccessful || resp.code == 403
                    }
                } catch (_: Exception) {
                    isSuccess = false
                }
            }
        }

        ProbeResult(
            domain = sanitizedUrl.trimEnd('/'),
            isSuccessful = isSuccess,
            statusCode = statusCode,
            isDoHValid = isDoH,
            latencyMs = if (isSuccess) latencyMs else Long.MAX_VALUE
        )
    }

    /**
     * Collects candidate mirrors combining local list and Cloudflare Worker metadata endpoint.
     */
    suspend fun collectCandidateDomains(client: OkHttpClient): List<String> = withContext(Dispatchers.IO) {
        val candidates = LinkedHashSet<String>()
        mirrorDomains.forEach { candidates.add(it.trimEnd('/')) }
        if (defaultBaseUrl.isNotBlank()) {
            candidates.add(defaultBaseUrl.trimEnd('/'))
        }

        // Fetch dynamic metadata from Cloudflare Worker
        val workerDomains = fetchDomainsFromWorker(client)
        workerDomains?.forEach { candidates.add(it.trimEnd('/')) }

        candidates.toList()
    }

    /**
     * Fetches fresh domains from the Cloudflare Worker metadata proxy.
     */
    private fun fetchDomainsFromWorker(client: OkHttpClient): List<String>? {
        return try {
            val request = Request.Builder()
                .url(workerMetadataUrl)
                .header("Authorization", "Bearer ${NetworkModule.WORKER_AUTH_TOKEN}")
                .header("User-Agent", NetworkModule.userAgent)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                parseDomainsFromJson(body, sourceId)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDomainsFromJson(jsonStr: String, sourceId: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val root = JSONObject(jsonStr)
            // 1. Check if there's a specific source map e.g. {"kuramanime": ["https://..."]}
            if (root.has(sourceId)) {
                val array = root.optJSONArray(sourceId)
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val d = array.optString(i, "").trim()
                        if (d.isNotBlank()) list.add(d)
                    }
                    return list
                }
            }

            // 2. Check general "domains" array
            val genericArray = root.optJSONArray("domains")
            if (genericArray != null) {
                for (i in 0 until genericArray.length()) {
                    val d = genericArray.optString(i, "").trim()
                    if (d.isNotBlank()) list.add(d)
                }
            }
        } catch (_: Exception) {
            // Check if root is directly a JSON Array
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val d = array.optString(i, "").trim()
                    if (d.isNotBlank()) list.add(d)
                }
            } catch (_: Exception) {}
        }
        return list
    }

    /**
     * Automatically heals and switches domain when a failing request is detected.
     */
    suspend fun autoHeal(
        context: Context? = null,
        failingUrl: String,
        client: OkHttpClient = NetworkModule.okHttpClient
    ): String? = withContext(Dispatchers.IO) {
        val failingHost = runCatching { URI(failingUrl).host?.lowercase() }.getOrNull() ?: ""

        val allCandidates = collectCandidateDomains(client).filter { candidate ->
            val candidateHost = runCatching { URI(candidate).host?.lowercase() }.getOrNull() ?: ""
            candidateHost.isNotBlank() && candidateHost != failingHost
        }

        val best = resolveBestActiveDomain(client, allCandidates)
        if (best != null) {
            cacheDomain(best.domain, best.latencyMs)
            if (context != null) {
                SourceBaseUrlStore.save(context, sourceId, best.domain)
            }
            return@withContext best.domain
        }
        null
    }

    private fun isHostHealthyQuick(client: OkHttpClient, domain: String): Boolean {
        return try {
            val req = Request.Builder().url(domain).head().build()
            client.newCall(req).execute().use { it.isSuccessful || it.code == 403 }
        } catch (_: Exception) {
            false
        }
    }

    private fun cacheDomain(domain: String, latencyMs: Long) {
        cachedActiveDomain[sourceId] = CachedDomain(
            domain = domain.trimEnd('/'),
            latencyMs = latencyMs,
            timestamp = System.currentTimeMillis()
        )
    }

    fun clearCache() {
        cachedActiveDomain.clear()
    }
}
