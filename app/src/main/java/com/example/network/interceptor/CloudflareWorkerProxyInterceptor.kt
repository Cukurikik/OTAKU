package com.example.network.interceptor

import com.example.data.NetworkModule
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.net.URLEncoder

/**
 * CloudflareWorkerProxyInterceptor:
 * Mencegat request yang terkena proteksi Cloudflare Turnstile / WAF (HTTP 403/503 atau HTML challenge)
 * dan mengalihkan request secara otomatis melalui Cloudflare Worker Proxy yang telah terotentikasi.
 */
class CloudflareWorkerProxyInterceptor(
    private val workerProxyUrl: String = NetworkModule.WORKER_BASE_URL,
    private val workerAuthToken: String = NetworkModule.WORKER_AUTH_TOKEN
) : Interceptor {

    companion object {
        const val HEADER_BYPASS_DEPTH = "X-Bypass-Depth"
        const val MAX_RETRY_DEPTH = 3
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val currentDepth = originalRequest.header(HEADER_BYPASS_DEPTH)?.toIntOrNull() ?: 0

        // 0. Loop termination
        if (currentDepth >= MAX_RETRY_DEPTH) {
            throw IOException("Cloudflare proxy bypass failed: maximum retry limit exceeded")
        }

        val originalUrl = originalRequest.url.toString()

        // 1. Prevent recursion if request is already targeting the worker
        if (originalUrl.startsWith(workerProxyUrl)) {
            val response = chain.proceed(originalRequest)
            if (response.code == 403 || response.code == 503) {
                response.close()
                throw IOException("Cloudflare worker proxy returned error ${response.code}")
            }
            return response
        }

        val host = originalRequest.url.host
        val isPreBlocked = NetworkModule.isHostBlocked(host)

        // 2. Direct routing if host is known to be blocked
        if (isPreBlocked) {
            val proxied = buildProxiedRequest(originalRequest, currentDepth)
            return chain.proceed(proxied)
        }

        // 3. Proceed with original request
        val (bufferedRequest, rawBodyBytes) = cloneRequestWithBufferedBytes(originalRequest)
        val response: Response = try {
            chain.proceed(bufferedRequest)
        } catch (e: IOException) {
            if (currentDepth + 1 >= MAX_RETRY_DEPTH) throw e
            NetworkModule.markAsBlocked(host)
            val proxied = buildProxiedRequest(originalRequest, currentDepth, rawBodyBytes)
            return chain.proceed(proxied)
        }

        // 4. Detect Cloudflare / WAF Turnstile challenges
        if (response.code == 403 || response.code == 503) {
            response.close()
            NetworkModule.markAsBlocked(host)
            val proxied = buildProxiedRequest(originalRequest, currentDepth, rawBodyBytes)
            return chain.proceed(proxied)
        }

        return response
    }

    private fun buildProxiedRequest(
        originalRequest: Request,
        currentDepth: Int,
        bufferedBody: ByteArray? = null
    ): Request {
        val originalUrl = originalRequest.url.toString()
        val encodedTarget = URLEncoder.encode(originalUrl, "UTF-8")
        val proxyRequestUrl = "$workerProxyUrl?url=$encodedTarget&target=$encodedTarget"

        val builder = originalRequest.newBuilder()
            .url(proxyRequestUrl)
            .header("Authorization", "Bearer $workerAuthToken")
            .header("X-Bypass-Engine", "HeadlessWebView")
            .header(HEADER_BYPASS_DEPTH, (currentDepth + 1).toString())

        if (originalRequest.header("User-Agent").isNullOrBlank()) {
            builder.header("User-Agent", NetworkModule.userAgent)
        }

        val appCheckToken = com.example.data.sync.CloudSyncManager.getAppCheckTokenSync()
        if (!appCheckToken.isNullOrBlank()) {
            builder.header("X-Firebase-AppCheck", appCheckToken)
        }

        if (originalRequest.body != null && bufferedBody != null) {
            val mediaType = originalRequest.body?.contentType()
            builder.method(originalRequest.method, bufferedBody.toRequestBody(mediaType))
        }

        return builder.build()
    }

    private fun cloneRequestWithBufferedBytes(request: Request): Pair<Request, ByteArray?> {
        val body = request.body ?: return Pair(request, null)
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            val bytes = buffer.readByteArray()
            val mediaType = body.contentType()
            val clonedReq = request.newBuilder()
                .method(request.method, bytes.toRequestBody(mediaType))
                .build()
            Pair(clonedReq, bytes)
        } catch (_: Exception) {
            Pair(request, null)
        }
    }
}
