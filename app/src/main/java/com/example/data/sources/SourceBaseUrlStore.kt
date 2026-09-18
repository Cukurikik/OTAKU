package com.example.data.sources

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists a user-overridable base URL per source, mirroring the
 * `overridableBaseUrl` preference pattern used by Keiyoushi/Tachiyomi
 * extensions for volatile domains (piracy/streaming sites that rotate
 * domains frequently or get blocked by ISPs).
 *
 * Sources that extend [ConfigurableSource] read their effective base URL
 * through [get] instead of hardcoding it, so a dead/blocked domain can be
 * fixed by the user from Settings without needing an app update.
 */
object SourceBaseUrlStore {
    private const val PREFS_NAME = "source_base_url_overrides"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(context: Context, sourceId: String, default: String): String {
        val stored = prefs(context).getString(key(sourceId), null)
        return stored?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: default.trimEnd('/')
    }

    fun getBaseUrl(sourceId: String, default: String = ""): String? {
        val app = runCatching { com.example.OtakuHubApp.instance }.getOrNull()
        return if (app != null) {
            val result = get(app, sourceId, default)
            result.ifBlank { default.ifBlank { null } }
        } else {
            default.ifBlank { null }
        }
    }

    fun set(context: Context, sourceId: String, newBaseUrl: String) {
        val cleaned = newBaseUrl.trim().trimEnd('/')
        prefs(context).edit().putString(key(sourceId), cleaned).apply()
    }

    fun save(context: Context, sourceId: String, newBaseUrl: String) {
        set(context, sourceId, newBaseUrl)
    }

    fun save(sourceId: String, newBaseUrl: String) {
        val app = runCatching { com.example.OtakuHubApp.instance }.getOrNull()
        if (app != null) {
            set(app, sourceId, newBaseUrl)
        }
    }

    fun clear(context: Context, sourceId: String) {
        prefs(context).edit().remove(key(sourceId)).apply()
    }

    fun isOverridden(context: Context, sourceId: String): Boolean =
        prefs(context).contains(key(sourceId))

    private fun key(sourceId: String) = "base_url_$sourceId"
}

/**
 * Implemented by sources whose base URL is known to be volatile (frequently
 * blocked/changed domains, common among Indonesian scraping/streaming
 * sources). Allows the UI (see [SourceStatusBus]) to offer a "change domain"
 * action instead of leaving the user with a silent empty list.
 */
interface ConfigurableSource {
    val sourceId: String

    /** The last known-good URL shipped with the app. May no longer be reachable. */
    val defaultBaseUrl: String

    /** True when [defaultBaseUrl] is confirmed dead/unreachable at build time (see /docs/SOURCES_STATUS.md). */
    val isDomainKnownDead: Boolean get() = false

    fun effectiveBaseUrl(context: Context): String =
        SourceBaseUrlStore.get(context, sourceId, defaultBaseUrl)
}
