package com.example.data

import com.example.data.sources.RateLimitInterceptor
import com.example.network.interceptor.CloudflareBypassInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

object NetworkModule {
    var userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" 
    
    // Cloudflare proxy blocklist registry
    private val blockedDomains = ConcurrentHashMap<String, Boolean>()

    const val WORKER_BASE_URL = "https://anime-aggregator.njirlah.workers.dev"
    const val WORKER_AUTH_TOKEN = "cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4"

    fun markAsBlocked(host: String) {
        blockedDomains[host] = true
    }

    fun isHostBlocked(host: String): Boolean = blockedDomains[host] == true

    fun isWafChallenge(titleOrContent: String): Boolean {
        val lower = titleOrContent.lowercase()
        return lower.contains("just a moment") ||
               lower.contains("attention required") ||
               lower.contains("security check") ||
               lower.contains("cloudflare") ||
               lower.contains("turnstile") ||
               lower.contains("ddos-guard") ||
               lower.contains("ray id:") ||
               lower.contains("cf-challenge") ||
               lower.contains("challenge-form") ||
               lower.contains("challenge-running")
    }

    fun fetchViaCloudflareWorker(targetUrl: String, extraHeaders: Map<String, String> = emptyMap()): String? {
        return try {
            val encoded = java.net.URLEncoder.encode(targetUrl, "UTF-8")
            val workerUrl = "$WORKER_BASE_URL/?url=$encoded"
            val appCheckToken = com.example.data.sync.CloudSyncManager.getAppCheckTokenSync()
            val builder = Request.Builder()
                .url(workerUrl)
                .header("Authorization", "Bearer $WORKER_AUTH_TOKEN")
                .header("User-Agent", userAgent)
            if (!appCheckToken.isNullOrBlank()) {
                builder.header("X-Firebase-AppCheck", appCheckToken)
            }
            extraHeaders.forEach { (k, v) -> builder.header(k, v) }
            val resp = okHttpClient.newCall(builder.build()).execute()
            if (resp.isSuccessful) {
                resp.body?.string()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(com.example.network.AppCheckInterceptor())
            .addInterceptor(com.example.network.interceptor.CloudflareInterceptor())
            .addInterceptor(CloudflareBypassInterceptor(WORKER_BASE_URL, WORKER_AUTH_TOKEN))
            .addInterceptor(RateLimitInterceptor(minIntervalMs = 700L))
            .cookieJar(com.example.data.sources.WebkitCookieJar())
            .build()
    }
}
