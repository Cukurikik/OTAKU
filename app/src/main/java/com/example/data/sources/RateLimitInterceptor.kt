package com.example.data.sources

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * Per-host request throttle, modeled after the `rateLimit()` OkHttp DSL
 * helper used across Keiyoushi/Tachiyomi extensions
 * (https://github.com/tachiyomiorg/extensions-lib). Scraping sources hit
 * shared-hosting WordPress sites that rate-limit or Cloudflare-challenge
 * aggressively; sending requests back-to-back gets the app's IP
 * soft-banned. This keeps requests to any single host spaced out by
 * [minIntervalMs] regardless of how many sources/coroutines are firing
 * concurrently.
 *
 * Not a token bucket / sliding window — just a "don't hit the same host
 * more than once every N ms" gate, which is what actually matters for
 * these low-QPS scraping workloads.
 */
class RateLimitInterceptor(private val minIntervalMs: Long = 700L) : Interceptor {
    private val lastRequestAtByHost = ConcurrentHashMap<String, Long>()
    private val lock = ReentrantLock()

    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        val waitMs = lock.lock().let {
            try {
                val now = System.currentTimeMillis()
                val last = lastRequestAtByHost[host] ?: 0L
                val elapsed = now - last
                val wait = (minIntervalMs - elapsed).coerceAtLeast(0L)
                lastRequestAtByHost[host] = now + wait
                wait
            } finally {
                lock.unlock()
            }
        }
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        return chain.proceed(chain.request())
    }
}
