package com.example.network.interceptor

import android.webkit.CookieManager
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
 * CloudflareBypassInterceptor:
 * Mencegat response yang terkena proteksi WAF/Cloudflare (HTTP 403/503) atau domain yang telah terdaftar
 * dalam registry blockedDomains, dan merekonstruksi request secara penuh
 * (termasuk Headers dinamis, Cookie sesi, Origin, Referer, dan Request Body POST/PUT)
 * untuk diteruskan (re-played) melalui Cloudflare Worker Proxy tanpa memicu silent stream drops.
 */
class CloudflareBypassInterceptor(
    private val workerBaseUrl: String = NetworkModule.WORKER_BASE_URL,
    private val workerAuthToken: String = NetworkModule.WORKER_AUTH_TOKEN
) : Interceptor {

    companion object {
        const val HEADER_BYPASS_DEPTH = "X-CineManga-Bypass-Depth"
        const val MAX_RETRY_DEPTH = 3
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val currentDepth = originalRequest.header(HEADER_BYPASS_DEPTH)?.toIntOrNull() ?: 0

        // 0. Terminasi loop jika kedalaman rekursi telah mencapai atau melampaui batas maksimum
        if (currentDepth >= MAX_RETRY_DEPTH) {
            throw IOException("Cloudflare bypass failed: maximum retry limit exceeded")
        }

        val originalUrl = originalRequest.url.toString()

        // 1. Cegah rekursi tak terbatas jika request sudah mengarah ke Worker Proxy
        if (originalUrl.startsWith(workerBaseUrl)) {
            val response = chain.proceed(originalRequest)
            // Jika Worker Proxy sendiri mengalami gangguan (503) atau diblokir (403), terminasi dan lempar exception
            if (response.code == 403 || response.code == 503) {
                response.close()
                throw IOException("Cloudflare bypass failed: maximum retry limit exceeded")
            }
            return response
        }

        val host = originalRequest.url.host
        val isPreBlocked = NetworkModule.isHostBlocked(host)

        // 2. Jika domain sudah diketahui terblokir WAF, langsung alihkan request dengan rekonstruksi payload penuh
        if (isPreBlocked) {
            val reconstructed = reconstructRequest(originalRequest)
            return chain.proceed(reconstructed)
        }

        // 3. Gandakan/Buffer RequestBody terlebih dahulu untuk antisipasi bila request gagal di stream
        val (bufferedRequest, rawBodyBytes) = cloneRequestWithBufferedBytes(originalRequest)

        val response: Response = try {
            chain.proceed(bufferedRequest)
        } catch (e: IOException) {
            if (currentDepth + 1 >= MAX_RETRY_DEPTH) {
                throw IOException("Cloudflare bypass failed: maximum retry limit exceeded", e)
            }
            // Kegagalan koneksi awal (misal reset TCP / handshake dropped oleh anti-bot)
            NetworkModule.markAsBlocked(host)
            val reconstructed = reconstructRequest(originalRequest, rawBodyBytes)
            return chain.proceed(reconstructed)
        }

        // 4. Deteksi respon WAF Challenge / Turnstile Block (HTTP 403 Forbidden atau HTTP 503 Service Unavailable)
        if (response.code == 403 || response.code == 503) {
            response.close()

            if (currentDepth + 1 >= MAX_RETRY_DEPTH) {
                throw IOException("Cloudflare bypass failed: maximum retry limit exceeded")
            }

            // Daftarkan host agar panggilan berikutnya langsung dialihkan (menghemat round-trip)
            NetworkModule.markAsBlocked(host)

            val reconstructedRequest = reconstructRequest(originalRequest, rawBodyBytes)
            return chain.proceed(reconstructedRequest)
        }

        return response
    }

    /**
     * Membaca dan menggandakan RequestBody agar dapat dieksekusi ulang bila request pertama ditolak.
     */
    private fun cloneRequestWithBufferedBytes(request: Request): Pair<Request, ByteArray?> {
        val body = request.body ?: return Pair(request, null)
        val buffer = Buffer()
        body.writeTo(buffer)
        val bytes = buffer.readByteArray()
        val contentType = body.contentType()
        val clonedRequest = request.newBuilder()
            .method(request.method, bytes.toRequestBody(contentType))
            .build()
        return Pair(clonedRequest, bytes)
    }

    /**
     * Merekonstruksi Request asal menuju Worker Proxy dengan menyalin seluruh state:
     * - Target URL di-encode sebagai parameter '?url='
     * - Headers dinamis (User-Agent, Referer, Origin, X-Requested-With, Accept, dll.)
     * - Cookie sesi aktif dari CookieManager (jika belum ada di header)
     * - Token otorisasi Worker dan Firebase AppCheck
     * - Inkrementasi header kedalaman bypass (X-CineManga-Bypass-Depth)
     * - RequestBody utuh untuk metode POST / PUT / PATCH
     */
    fun reconstructRequest(original: Request, prebufferedBytes: ByteArray? = null): Request {
        val targetUrl = original.url.toString()
        val encodedTarget = URLEncoder.encode(targetUrl, "UTF-8")
        val workerUrl = if (workerBaseUrl.endsWith("/")) {
            "${workerBaseUrl}?url=$encodedTarget"
        } else {
            "${workerBaseUrl}/?url=$encodedTarget"
        }

        val builder = original.newBuilder().url(workerUrl)

        // Inkrementasi header batas percobaan rekursi (Bypass Depth)
        val currentDepth = original.header(HEADER_BYPASS_DEPTH)?.toIntOrNull() ?: 0
        builder.header(HEADER_BYPASS_DEPTH, (currentDepth + 1).toString())

        // Salin dan lengkapi Header Kritis
        if (original.header("User-Agent") == null) {
            builder.header("User-Agent", NetworkModule.userAgent)
        }
        if (original.header("Referer") == null) {
            builder.header("Referer", "${original.url.scheme}://${original.url.host}/")
        }
        if (original.header("Origin") == null) {
            builder.header("Origin", "${original.url.scheme}://${original.url.host}")
        }

        // Injeksi Cookie sesi jika belum tercantum secara eksplisit di Request
        if (original.header("Cookie").isNullOrBlank()) {
            try {
                val cookieMgr = CookieManager.getInstance()
                val storedCookie = cookieMgr?.getCookie(targetUrl)
                if (!storedCookie.isNullOrBlank()) {
                    builder.header("Cookie", storedCookie)
                }
            } catch (_: Throwable) {
                // Diabaikan jika running di luar Android Context (mis. JVM Unit Test)
            }
        }

        // Kredensial Worker Proxy
        builder.header("Authorization", "Bearer $workerAuthToken")
        try {
            val appCheckToken = com.example.data.sync.CloudSyncManager.getAppCheckTokenSync()
            if (!appCheckToken.isNullOrBlank()) {
                builder.header("X-Firebase-AppCheck", appCheckToken)
            }
        } catch (_: Throwable) {}

        // Rekonstruksi RequestBody jika request menggunakan method dengan body
        val originalBody = original.body
        if (originalBody != null) {
            val bytes = prebufferedBytes ?: run {
                val buffer = Buffer()
                originalBody.writeTo(buffer)
                buffer.readByteArray()
            }
            val contentType = originalBody.contentType()
            builder.method(original.method, bytes.toRequestBody(contentType))
        }

        return builder.build()
    }
}
