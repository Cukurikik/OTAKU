package com.example.data.sources.base

import android.content.Context
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.models.Page
import com.example.data.sources.ConfigurableSource
import com.example.data.sources.ContentSource
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.WebViewFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

abstract class BaseMangaSource(protected val context: Context) : ContentSource, ConfigurableSource {
    abstract val baseUrl: String
    override val defaultBaseUrl: String get() = baseUrl
    protected val fetcher by lazy { WebViewFetcher(context) }

    /** The URL to actually use for requests: user override (if set) or [baseUrl]. */
    protected fun currentBaseUrl(): String = effectiveBaseUrl(context)

    protected val healthProbe by lazy { com.example.data.sources.registry.SourceHealthProbe() }

    /**
     * Checks if the given URL belongs to an outdated mirror and remaps it to [currentBaseUrl].
     */
    protected fun adjustUrlToActiveDomain(url: String): String {
        val activeBase = currentBaseUrl()
        val allMirrors = com.example.data.sources.registry.SourceEndpointRegistry.getAllMirrors(sourceId)
        val urlHost = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: ""
        val activeHost = runCatching { java.net.URI(activeBase).host?.lowercase() }.getOrNull() ?: ""

        if (urlHost.isNotBlank() && activeHost.isNotBlank() && urlHost != activeHost) {
            val isKnownMirror = allMirrors.any { mirror ->
                val mHost = runCatching { java.net.URI(mirror).host?.lowercase() }.getOrNull() ?: ""
                mHost.isNotBlank() && (urlHost.contains(mHost) || mHost.contains(urlHost))
            }
            if (isKnownMirror) {
                return com.example.data.sources.registry.SourceHealthProbe.rewriteUrl(url, activeBase)
            }
        }
        return url
    }

    protected suspend fun fetchDocument(url: String): org.jsoup.nodes.Document {
        val targetUrl = adjustUrlToActiveDomain(url)
        return try {
            fetchDocumentInternal(targetUrl)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (shouldFailover(e)) {
                val backupDomain = healthProbe.probeAndFailover(context, sourceId, targetUrl)
                if (!backupDomain.isNullOrBlank()) {
                    val rewritten = com.example.data.sources.registry.SourceHealthProbe.rewriteUrl(targetUrl, backupDomain)
                    return fetchDocumentInternal(rewritten)
                }
            }
            throw e
        }
    }

    private fun shouldFailover(e: Exception): Boolean {
        if (e is java.net.UnknownHostException ||
            e is java.net.SocketTimeoutException ||
            e is java.net.ConnectException ||
            e is java.net.NoRouteToHostException ||
            e is javax.net.ssl.SSLException) {
            return true
        }
        val msg = e.message.orEmpty().lowercase()
        return msg.contains("failed to fetch") ||
               msg.contains("timeout") ||
               msg.contains("cloudflare") ||
               msg.contains("http 5") ||
               msg.contains("connect") ||
               msg.contains("resolve")
    }

    protected suspend fun fetchDocumentInternal(url: String): org.jsoup.nodes.Document {
        var doc: org.jsoup.nodes.Document? = null
        var isCfChallenge = false
        try {
            doc = fetcher.fetchHtml(url)
            val title = doc.title().lowercase()
            isCfChallenge = title.contains("just a moment") || 
                title.contains("attention required") || 
                title.contains("security check") || 
                title.contains("access denied") ||
                (doc.selectFirst("#challenge-running, #cf-challenge-running, #challenge-form, #turnstile-wrapper") != null)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("HTTP 403") || msg.contains("HTTP 503") || msg.contains("Cloudflare") || msg.contains("Just a moment")) {
                isCfChallenge = true
            } else {
                throw e
            }
        }

        if (isCfChallenge) {
            try {
                val host = java.net.URL(url).host
                com.example.data.NetworkModule.markAsBlocked(host)
                doc = fetcher.fetchHtml(url)
            } catch (e: Exception) {
                throw com.example.data.sources.CloudflareException(url)
            }
        }

        val finalDoc = doc ?: throw Exception("Failed to fetch $url")
        val finalTitle = finalDoc.title().lowercase()
        if (finalTitle.contains("just a moment") || 
            finalTitle.contains("attention required") || 
            finalTitle.contains("security check") || 
            finalTitle.contains("access denied") ||
            (finalDoc.selectFirst("#challenge-running, #cf-challenge-running, #challenge-form, #turnstile-wrapper") != null)) {
            throw com.example.data.sources.CloudflareException(url)
        }
        return finalDoc
    }

    override fun getPages(chapterId: String): Flow<List<Page>> = flow {
        try {
            val pages = getPagesList(chapterId).map { Page(it) }
            emit(pages)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }

    abstract suspend fun getPagesList(chapterId: String): List<String>
}
