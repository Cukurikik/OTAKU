package com.example.data.sources.base

import android.content.Context
import com.example.data.NetworkModule
import com.example.data.sources.ConfigurableSource
import com.example.data.sources.registry.SourceEndpointRegistry
import com.example.network.domain.DynamicDomainResolverEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.URI

/**
 * BaseScraperSource:
 * Foundational abstract source class providing Dynamic Domain Auto-Healing,
 * DoH validation, mirror fallback rotation, and URL rewriting for all content scrapers.
 */
abstract class BaseScraperSource : ConfigurableSource {

    abstract override val sourceId: String
    abstract val baseUrl: String
    open val mirrorDomains: List<String> get() = SourceEndpointRegistry.getAllMirrors(sourceId)

    override val defaultBaseUrl: String get() = baseUrl

    protected val domainResolver: DynamicDomainResolverEngine by lazy {
        DynamicDomainResolverEngine.getOrCreate(
            sourceId = sourceId,
            mirrors = mirrorDomains,
            defaultBaseUrl = baseUrl
        )
    }

    /**
     * Resolves the active base URL with auto-healing and DoH validation.
     */
    suspend fun getValidBaseUrl(
        context: Context? = null,
        client: OkHttpClient = NetworkModule.okHttpClient
    ): String {
        return domainResolver.getValidBaseUrl(context, client)
    }

    /**
     * Rewrites outdated mirror URLs to the currently active domain.
     */
    fun rewriteUrlToActiveDomain(url: String, activeBaseUrl: String): String {
        if (url.isBlank() || activeBaseUrl.isBlank()) return url
        val targetBase = activeBaseUrl.trimEnd('/')

        return try {
            val uri = URI(url)
            val path = uri.rawPath ?: ""
            val query = if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
            val fragment = if (uri.rawFragment != null) "#${uri.rawFragment}" else ""

            "$targetBase$path$query$fragment"
        } catch (_: Exception) {
            url
        }
    }

    /**
     * Executes a suspending block with automatic domain failover if an endpoint failure occurs.
     */
    suspend fun <T> executeWithAutoHealing(
        context: Context? = null,
        initialUrl: String,
        client: OkHttpClient = NetworkModule.okHttpClient,
        maxRetries: Int = 3,
        block: suspend (effectiveUrl: String) -> T
    ): T = withContext(Dispatchers.IO) {
        var currentUrl = initialUrl
        var attempts = 0
        var lastException: Exception? = null

        while (attempts < maxRetries) {
            try {
                return@withContext block(currentUrl)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastException = e
                attempts++

                // Trigger domain auto-healing to next healthy mirror
                val healedBase = domainResolver.autoHeal(context, currentUrl, client)
                if (!healedBase.isNullOrBlank()) {
                    currentUrl = rewriteUrlToActiveDomain(currentUrl, healedBase)
                } else {
                    break
                }
            }
        }

        throw lastException ?: java.io.IOException("Failed to execute request after $attempts auto-healing attempts on '$initialUrl'")
    }
}
