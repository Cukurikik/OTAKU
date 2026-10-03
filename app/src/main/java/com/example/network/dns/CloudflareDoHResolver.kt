package com.example.network.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * CloudflareDoHResolver:
 * High-performance DNS-over-HTTPS (DoH) engine independent of local ISP DNS interception.
 * Uses Cloudflare (1.1.1.1 / cloudflare-dns.com) and Google (8.8.8.8 / dns.google) DoH JSON APIs.
 *
 * Implements [okhttp3.Dns] for seamless injection into OkHttpClient.
 */
class CloudflareDoHResolver(
    private val bootstrapClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()
) : Dns {

    private val cache = ConcurrentHashMap<String, CachedDnsEntry>()

    data class CachedDnsEntry(
        val addresses: List<InetAddress>,
        val expiresAtMs: Long
    )

    companion object {
        const val CLOUDFLARE_DOH_URL = "https://cloudflare-dns.com/dns-query"
        const val GOOGLE_DOH_URL = "https://dns.google/resolve"
        private const val DEFAULT_TTL_MS = 5 * 60 * 1000L // 5 Minutes TTL

        val instance by lazy { CloudflareDoHResolver() }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.isBlank()) throw UnknownHostException("Hostname is blank")

        // 1. Return IP literal directly if already an IP
        if (isIpAddress(hostname)) {
            return listOf(InetAddress.getByName(hostname))
        }

        // 2. Check local memory cache
        val cached = cache[hostname]
        if (cached != null && System.currentTimeMillis() < cached.expiresAtMs) {
            return cached.addresses
        }

        // 3. Resolve via Cloudflare DoH, fallback to Google DoH, fallback to system DNS
        val resolved = resolveViaDoH(hostname)
        if (resolved.isNotEmpty()) {
            cache[hostname] = CachedDnsEntry(resolved, System.currentTimeMillis() + DEFAULT_TTL_MS)
            return resolved
        }

        // 4. Fallback to System DNS if DoH fails
        return try {
            Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            throw UnknownHostException("Failed to resolve host '$hostname' via DoH and System DNS: ${e.message}")
        }
    }

    /**
     * Resolves hostname via DoH JSON queries (Cloudflare -> Google).
     */
    fun resolveViaDoH(hostname: String): List<InetAddress> {
        val cloudflareResult = queryDoHEndpoint(CLOUDFLARE_DOH_URL, hostname)
        if (cloudflareResult.isNotEmpty()) return cloudflareResult

        val googleResult = queryDoHEndpoint(GOOGLE_DOH_URL, hostname)
        if (googleResult.isNotEmpty()) return googleResult

        return emptyList()
    }

    /**
     * Suspending version of DoH host resolution.
     */
    suspend fun resolveHostAsync(hostname: String): List<InetAddress> = withContext(Dispatchers.IO) {
        try {
            lookup(hostname)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Validates if a hostname can be resolved to a valid non-empty IP address via DoH.
     */
    fun isDoHValid(hostname: String): Boolean {
        return try {
            val ips = lookup(hostname)
            ips.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun queryDoHEndpoint(dohBaseUrl: String, hostname: String): List<InetAddress> {
        return try {
            val url = dohBaseUrl.toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("name", hostname)
                ?.addQueryParameter("type", "A")
                ?.build() ?: return emptyList()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) DoHClient/1.0")
                .build()

            bootstrapClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyStr = response.body?.string() ?: return emptyList()
                parseDnsJsonResponse(bodyStr)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseDnsJsonResponse(jsonStr: String): List<InetAddress> {
        val result = mutableListOf<InetAddress>()
        try {
            val json = JSONObject(jsonStr)
            val answers = json.optJSONArray("Answer") ?: return emptyList()
            for (i in 0 until answers.length()) {
                val item = answers.optJSONObject(i) ?: continue
                val type = item.optInt("type", 0)
                // Type 1 is DNS Type A (IPv4)
                if (type == 1) {
                    val data = item.optString("data", "").trim()
                    if (isIpAddress(data)) {
                        result.add(InetAddress.getByName(data))
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun isIpAddress(input: String): Boolean {
        return input.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$"""))
    }

    fun clearCache() {
        cache.clear()
    }
}
