package com.example.data.sources

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages custom HTTP headers (User-Agent, Cookie, Referer) configured per-source.
 * Allows overriding global network headers for specific manga, anime, or drama sources
 * to satisfy strict anti-DDoS, Cloudflare Turnstile, or IP/session bindings.
 */
object SourceHeaderStore {
    private const val PREFS_NAME = "source_header_overrides"
    private const val KEY_PREFIX_UA = "ua_"
    private const val KEY_PREFIX_COOKIE = "cookie_"
    private const val KEY_PREFIX_REFERER = "referer_"

    // In-memory cache for fast, synchronized interceptor access without repeated Disk I/O
    private val uaCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val cookieCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val refererCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var isInitialized = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        val prefs = getPrefs(context)
        prefs.all.forEach { (key, value) ->
            val strVal = value as? String ?: return@forEach
            when {
                key.startsWith(KEY_PREFIX_UA) -> {
                    val sourceId = key.removePrefix(KEY_PREFIX_UA)
                    if (strVal.isNotBlank()) uaCache[sourceId] = strVal
                }
                key.startsWith(KEY_PREFIX_COOKIE) -> {
                    val sourceId = key.removePrefix(KEY_PREFIX_COOKIE)
                    if (strVal.isNotBlank()) cookieCache[sourceId] = strVal
                }
                key.startsWith(KEY_PREFIX_REFERER) -> {
                    val sourceId = key.removePrefix(KEY_PREFIX_REFERER)
                    if (strVal.isNotBlank()) refererCache[sourceId] = strVal
                }
            }
        }
        isInitialized = true
    }

    fun getUserAgent(sourceId: String): String? {
        return uaCache[sourceId]
    }

    fun getUserAgent(context: Context, sourceId: String): String? {
        init(context)
        return uaCache[sourceId]
    }

    fun setUserAgent(context: Context, sourceId: String, userAgent: String?) {
        init(context)
        val clean = userAgent?.trim().orEmpty()
        if (clean.isBlank()) {
            uaCache.remove(sourceId)
            getPrefs(context).edit().remove(KEY_PREFIX_UA + sourceId).apply()
        } else {
            uaCache[sourceId] = clean
            getPrefs(context).edit().putString(KEY_PREFIX_UA + sourceId, clean).apply()
        }
    }

    fun getCookie(sourceId: String): String? {
        return cookieCache[sourceId]
    }

    fun getCookie(context: Context, sourceId: String): String? {
        init(context)
        return cookieCache[sourceId]
    }

    fun setCookie(context: Context, sourceId: String, cookie: String?) {
        init(context)
        val clean = cookie?.trim().orEmpty()
        if (clean.isBlank()) {
            cookieCache.remove(sourceId)
            getPrefs(context).edit().remove(KEY_PREFIX_COOKIE + sourceId).apply()
        } else {
            cookieCache[sourceId] = clean
            getPrefs(context).edit().putString(KEY_PREFIX_COOKIE + sourceId, clean).apply()
        }
    }

    fun getReferer(sourceId: String): String? {
        return refererCache[sourceId]
    }

    fun getReferer(context: Context, sourceId: String): String? {
        init(context)
        return refererCache[sourceId]
    }

    fun setReferer(context: Context, sourceId: String, referer: String?) {
        init(context)
        val clean = referer?.trim().orEmpty()
        if (clean.isBlank()) {
            refererCache.remove(sourceId)
            getPrefs(context).edit().remove(KEY_PREFIX_REFERER + sourceId).apply()
        } else {
            refererCache[sourceId] = clean
            getPrefs(context).edit().putString(KEY_PREFIX_REFERER + sourceId, clean).apply()
        }
    }

    /**
     * Clear all custom headers for a given source
     */
    fun clear(context: Context, sourceId: String) {
        init(context)
        uaCache.remove(sourceId)
        cookieCache.remove(sourceId)
        refererCache.remove(sourceId)
        getPrefs(context).edit()
            .remove(KEY_PREFIX_UA + sourceId)
            .remove(KEY_PREFIX_COOKIE + sourceId)
            .remove(KEY_PREFIX_REFERER + sourceId)
            .apply()
    }
}

data class SourceHeaders(
    val userAgent: String? = null,
    val cookie: String? = null,
    val referer: String? = null
) {
    fun isEmpty(): Boolean = userAgent.isNullOrBlank() && cookie.isNullOrBlank() && referer.isNullOrBlank()
}

