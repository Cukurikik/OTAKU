package com.example.media

import android.net.Uri
import android.webkit.CookieManager
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.example.data.NetworkModule

/**
 * Extension function on Media3 DataSpec to persistently append or override HTTP headers
 * on each manifest, playlist, and chunked media request.
 */
@UnstableApi
fun DataSpec.withAdditionalHeaders(additionalHeaders: Map<String, String>): DataSpec {
    if (additionalHeaders.isEmpty()) return this
    val merged = HashMap(httpRequestHeaders)
    for ((key, value) in additionalHeaders) {
        if (value.isNotBlank()) {
            merged[key] = value
        }
    }
    return buildUpon().setHttpRequestHeaders(merged).build()
}

/**
 * Custom ResolvingDataSource.Resolver that guarantees strict network authentication
 * retention (Cookie, Origin, Referer, User-Agent) across chunked streaming requests
 * for protected external video hosts such as DoodStream, Mp4Upload, and FileLions.
 */
@UnstableApi
class PersistentHttpHeaderResolver(
    private val headersProvider: () -> Map<String, String> = { emptyMap() },
    private val streamUrlProvider: () -> String = { "" }
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val requestUri = dataSpec.uri
        val requestHost = requestUri.host.orEmpty().lowercase()
        val customHeaders = headersProvider()
        val activeStreamUrl = streamUrlProvider()

        val persistentHeaders = resolveHeadersForRequest(
            requestUri = requestUri,
            requestHost = requestHost,
            customHeaders = customHeaders,
            activeStreamUrl = activeStreamUrl
        )

        return dataSpec.withAdditionalHeaders(persistentHeaders)
    }

    companion object {
        /**
         * Resolves the persistent metadata (Cookie, Origin, Referer, User-Agent)
         * required for the given media request URI.
         */
        fun resolveHeadersForRequest(
            requestUri: Uri,
            requestHost: String = requestUri.host.orEmpty().lowercase(),
            customHeaders: Map<String, String> = emptyMap(),
            activeStreamUrl: String = ""
        ): Map<String, String> {
            val resolvedHeaders = mutableMapOf<String, String>()

            val hostOrigin: String
            val hostReferer: String

            when {
                // 1. DoodStream and its mirrors (*.dood.to, *.doodstream.com, *.ds2play.com, *.do0od.com)
                isDoodStreamHost(requestHost) -> {
                    val baseHost = when {
                        requestHost.contains("doodstream") -> "https://doodstream.com"
                        requestHost.contains("ds2play") -> "https://ds2play.com"
                        requestHost.contains("do0od") -> "https://do0od.com"
                        else -> "https://dood.to"
                    }
                    hostOrigin = customHeaders["Origin"] ?: customHeaders["origin"] ?: baseHost
                    hostReferer = customHeaders["Referer"] ?: customHeaders["referer"] ?: "$baseHost/"
                }

                // 2. Mp4Upload and its subdomains (*.mp4upload.com)
                isMp4UploadHost(requestHost) -> {
                    hostOrigin = customHeaders["Origin"] ?: customHeaders["origin"] ?: "https://www.mp4upload.com"
                    hostReferer = customHeaders["Referer"] ?: customHeaders["referer"] ?: "https://www.mp4upload.com/"
                }

                // 3. FileLions, Vidhide, Streamwish and edge subdomains
                isFileLionsHost(requestHost) -> {
                    val baseDomain = when {
                        requestHost.contains("vidhide") -> "https://vidhidepro.com"
                        requestHost.contains("streamwish") -> "https://streamwish.to"
                        else -> "https://filelions.to"
                    }
                    hostOrigin = customHeaders["Origin"] ?: customHeaders["origin"] ?: baseDomain
                    hostReferer = customHeaders["Referer"] ?: customHeaders["referer"] ?: "$baseDomain/"
                }

                // 4. Streamtape and subdomains
                requestHost.contains("streamtape") -> {
                    hostOrigin = customHeaders["Origin"] ?: customHeaders["origin"] ?: "https://streamtape.com"
                    hostReferer = customHeaders["Referer"] ?: customHeaders["referer"] ?: "https://streamtape.com/"
                }

                // 5. Default / Other external hosts
                else -> {
                    val customReferer = customHeaders["Referer"] ?: customHeaders["referer"]
                    val customOrigin = customHeaders["Origin"] ?: customHeaders["origin"]

                    if (!customReferer.isNullOrBlank()) {
                        hostReferer = customReferer
                        hostOrigin = if (!customOrigin.isNullOrBlank()) {
                            customOrigin
                        } else {
                            val refUri = Uri.parse(customReferer)
                            if (!refUri.scheme.isNullOrBlank() && !refUri.host.isNullOrBlank()) {
                                "${refUri.scheme}://${refUri.host}"
                            } else {
                                ""
                            }
                        }
                    } else if (activeStreamUrl.isNotBlank() && !activeStreamUrl.startsWith("file://")) {
                        hostReferer = activeStreamUrl
                        val stUri = Uri.parse(activeStreamUrl)
                        hostOrigin = if (!customOrigin.isNullOrBlank()) {
                            customOrigin
                        } else if (!stUri.scheme.isNullOrBlank() && !stUri.host.isNullOrBlank()) {
                            "${stUri.scheme}://${stUri.host}"
                        } else {
                            ""
                        }
                    } else {
                        hostReferer = if (!requestUri.scheme.isNullOrBlank() && !requestUri.host.isNullOrBlank()) {
                            "${requestUri.scheme}://${requestUri.host}/"
                        } else {
                            ""
                        }
                        hostOrigin = if (!customOrigin.isNullOrBlank()) {
                            customOrigin
                        } else if (!requestUri.scheme.isNullOrBlank() && !requestUri.host.isNullOrBlank()) {
                            "${requestUri.scheme}://${requestUri.host}"
                        } else {
                            ""
                        }
                    }
                }
            }

            if (hostReferer.isNotBlank()) {
                resolvedHeaders["Referer"] = hostReferer
            }
            if (hostOrigin.isNotBlank()) {
                resolvedHeaders["Origin"] = hostOrigin
            }

            // Session Cookie resolution (prioritize extracted headers, fallback to system Webkit CookieManager)
            val customCookie = customHeaders["Cookie"] ?: customHeaders["cookie"]
            val sessionCookie = if (!customCookie.isNullOrBlank()) {
                customCookie
            } else {
                try {
                    val cookieMgr = CookieManager.getInstance()
                    cookieMgr?.getCookie(requestUri.toString())
                        ?: if (hostReferer.isNotBlank()) cookieMgr?.getCookie(hostReferer) else null
                } catch (_: Throwable) {
                    null
                }
            }
            if (!sessionCookie.isNullOrBlank()) {
                resolvedHeaders["Cookie"] = sessionCookie
            }

            // User-Agent consistency
            val ua = customHeaders["User-Agent"] ?: customHeaders["user-agent"] ?: NetworkModule.userAgent
            resolvedHeaders["User-Agent"] = ua

            // Retain any remaining authentication or custom headers (e.g. X-Requested-With, Authorization, blob tokens)
            for ((key, value) in customHeaders) {
                if (!resolvedHeaders.containsKey(key) &&
                    !key.equals("Referer", ignoreCase = true) &&
                    !key.equals("Origin", ignoreCase = true) &&
                    !key.equals("Cookie", ignoreCase = true) &&
                    !key.equals("User-Agent", ignoreCase = true) &&
                    value.isNotBlank()
                ) {
                    resolvedHeaders[key] = value
                }
            }

            return resolvedHeaders
        }

        fun isDoodStreamHost(host: String): Boolean {
            val lower = host.lowercase()
            return lower.contains("dood") || lower.contains("ds2play") || lower.contains("do0od")
        }

        fun isMp4UploadHost(host: String): Boolean {
            return host.lowercase().contains("mp4upload")
        }

        fun isFileLionsHost(host: String): Boolean {
            val lower = host.lowercase()
            return lower.contains("filelions") || lower.contains("vidhide") || lower.contains("streamwish")
        }
    }
}

/**
 * Factory helper for building Persistent ResolvingDataSource.Factory instances.
 */
@UnstableApi
object PersistentResolvingDataSourceFactory {

    /**
     * Creates a ResolvingDataSource.Factory using dynamic headers and stream url providers.
     */
    fun create(
        upstreamFactory: DataSource.Factory,
        headersProvider: () -> Map<String, String> = { emptyMap() },
        streamUrlProvider: () -> String = { "" }
    ): ResolvingDataSource.Factory {
        val resolver = PersistentHttpHeaderResolver(headersProvider, streamUrlProvider)
        return ResolvingDataSource.Factory(upstreamFactory, resolver)
    }

    /**
     * Creates a ResolvingDataSource.Factory with fixed headers.
     */
    fun create(
        upstreamFactory: DataSource.Factory,
        headers: Map<String, String>,
        streamUrl: String = ""
    ): ResolvingDataSource.Factory {
        return create(
            upstreamFactory = upstreamFactory,
            headersProvider = { headers },
            streamUrlProvider = { streamUrl }
        )
    }

    /**
     * Creates a DefaultHttpDataSource.Factory configured with User-Agent, Referer, Cookie, and request properties.
     */
    fun createDefaultHttpDataSourceFactory(
        headers: Map<String, String> = emptyMap(),
        userAgent: String = NetworkModule.userAgent
    ): androidx.media3.datasource.DefaultHttpDataSource.Factory {
        val ua = headers["User-Agent"] ?: headers["user-agent"] ?: userAgent
        val defaultProperties = headers.filter { it.value.isNotBlank() }
        return androidx.media3.datasource.DefaultHttpDataSource.Factory().apply {
            setUserAgent(ua)
            if (defaultProperties.isNotEmpty()) {
                setDefaultRequestProperties(defaultProperties)
            }
        }
    }

    /**
     * Creates an HlsMediaSource.Factory with headers injected via DefaultHttpDataSource.Factory.
     */
    fun createHlsMediaSourceFactory(
        headers: Map<String, String> = emptyMap(),
        userAgent: String = NetworkModule.userAgent
    ): androidx.media3.exoplayer.hls.HlsMediaSource.Factory {
        val httpFactory = createDefaultHttpDataSourceFactory(headers, userAgent)
        return androidx.media3.exoplayer.hls.HlsMediaSource.Factory(httpFactory)
    }

    /**
     * Creates a ResolvingDataSource.Factory with direct Resolver closure (exact schema from requirements).
     */
    fun createWithResolver(
        upstreamFactory: DataSource.Factory,
        resolver: ResolvingDataSource.Resolver
    ): ResolvingDataSource.Factory {
        return ResolvingDataSource.Factory(upstreamFactory, resolver)
    }
}
