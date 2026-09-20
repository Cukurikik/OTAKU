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
 * Registry to hold active download request headers mapped by downloadId, stream URL, and host.
 * Guarantees that any Media3 background download (HLS manifest, AES-128 key, or TS segments)
 * receives the exact HTTP headers (Referer, Origin, Cookie, User-Agent) without HTTP 403 Forbidden.
 */
object DownloadHeaderRegistry {
    private val headersByDownloadId = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
    private val headersByHost = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
    private val headersByUrl = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
    private val aes128Keys = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    private val localKeyMappings = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun register(downloadId: String, headers: Map<String, String>, url: String? = null) {
        if (headers.isNotEmpty()) {
            headersByDownloadId[downloadId] = headers
            url?.let {
                headersByUrl[it] = headers
                try {
                    val uri = Uri.parse(it)
                    val host = uri.host?.lowercase()
                    if (!host.isNullOrBlank()) {
                        headersByHost[host] = headers
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    fun getHeaders(downloadId: String): Map<String, String>? = headersByDownloadId[downloadId]

    fun getHeadersForUri(uri: Uri): Map<String, String> {
        val uriStr = uri.toString()
        headersByUrl[uriStr]?.let { return it }
        val host = uri.host?.lowercase() ?: return emptyMap()
        return headersByHost[host] ?: emptyMap()
    }

    /**
     * Registers and caches an AES-128 decryption key in-memory by key URL or identifier.
     */
    fun registerAes128Key(keyUri: String, keyBytes: ByteArray) {
        if (keyBytes.isNotEmpty()) {
            aes128Keys[keyUri] = keyBytes
        }
    }

    /**
     * Retrieves a cached AES-128 decryption key bytes.
     */
    fun getAes128Key(keyUri: String): ByteArray? = aes128Keys[keyUri]

    /**
     * Registers a local filesystem mapping for a remote AES-128 key URI.
     */
    fun registerLocalKeyMapping(remoteKeyUri: String, localFilePath: String) {
        localKeyMappings[remoteKeyUri] = localFilePath
    }

    /**
     * Gets the local filesystem file path mapped to a remote AES-128 key URI.
     */
    fun getLocalKeyMapping(remoteKeyUri: String): String? = localKeyMappings[remoteKeyUri]

    fun unregister(downloadId: String) {
        headersByDownloadId.remove(downloadId)
    }

    fun clear() {
        headersByDownloadId.clear()
        headersByHost.clear()
        headersByUrl.clear()
        aes128Keys.clear()
        localKeyMappings.clear()
    }
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
            val registryHeaders = DownloadHeaderRegistry.getHeadersForUri(requestUri)
            val mergedCustomHeaders = if (registryHeaders.isNotEmpty()) {
                val combined = HashMap(registryHeaders)
                customHeaders.forEach { (k, v) -> if (v.isNotBlank()) combined[k] = v }
                combined
            } else {
                customHeaders
            }

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
                    hostOrigin = mergedCustomHeaders["Origin"] ?: mergedCustomHeaders["origin"] ?: baseHost
                    hostReferer = mergedCustomHeaders["Referer"] ?: mergedCustomHeaders["referer"] ?: "$baseHost/"
                }

                // 2. Mp4Upload and its subdomains (*.mp4upload.com)
                isMp4UploadHost(requestHost) -> {
                    hostOrigin = mergedCustomHeaders["Origin"] ?: mergedCustomHeaders["origin"] ?: "https://www.mp4upload.com"
                    hostReferer = mergedCustomHeaders["Referer"] ?: mergedCustomHeaders["referer"] ?: "https://www.mp4upload.com/"
                }

                // 3. FileLions, Vidhide, Streamwish and edge subdomains
                isFileLionsHost(requestHost) -> {
                    val baseDomain = when {
                        requestHost.contains("vidhide") -> "https://vidhidepro.com"
                        requestHost.contains("streamwish") -> "https://streamwish.to"
                        else -> "https://filelions.to"
                    }
                    hostOrigin = mergedCustomHeaders["Origin"] ?: mergedCustomHeaders["origin"] ?: baseDomain
                    hostReferer = mergedCustomHeaders["Referer"] ?: mergedCustomHeaders["referer"] ?: "$baseDomain/"
                }

                // 4. Streamtape and subdomains
                requestHost.contains("streamtape") -> {
                    hostOrigin = mergedCustomHeaders["Origin"] ?: mergedCustomHeaders["origin"] ?: "https://streamtape.com"
                    hostReferer = mergedCustomHeaders["Referer"] ?: mergedCustomHeaders["referer"] ?: "https://streamtape.com/"
                }

                // 5. Default / Other external hosts
                else -> {
                    val customReferer = mergedCustomHeaders["Referer"] ?: mergedCustomHeaders["referer"]
                    val customOrigin = mergedCustomHeaders["Origin"] ?: mergedCustomHeaders["origin"]

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
            val customCookie = mergedCustomHeaders["Cookie"] ?: mergedCustomHeaders["cookie"]
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
            val ua = mergedCustomHeaders["User-Agent"] ?: mergedCustomHeaders["user-agent"] ?: NetworkModule.userAgent
            resolvedHeaders["User-Agent"] = ua

            // Retain any remaining authentication or custom headers (e.g. X-Requested-With, Authorization, blob tokens)
            for ((key, value) in mergedCustomHeaders) {
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
