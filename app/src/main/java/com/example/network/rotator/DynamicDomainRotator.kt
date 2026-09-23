package com.example.network.rotator

import android.content.Context
import com.example.data.sources.SourceBaseUrlStore
import com.example.data.sources.registry.HealthResult
import com.example.data.sources.registry.SourceEndpointRegistry
import com.example.data.sources.registry.SourceHealthProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.URI

/**
 * DynamicDomainRotator:
 * Engine pemantau status HTTP domain secara berkala dan memperbarui domain aktif secara otomatis
 * dari daftar cermin (mirrors) ketika domain lama mengalami gangguan atau diblokir.
 *
 * Formulasi:
 * Domain_active = HealthCheck(DomainList[i]) dimana i in [0, N-1]
 */
class DynamicDomainRotator(
    private val healthProbe: SourceHealthProbe = SourceHealthProbe(),
    private val client: OkHttpClient = com.example.data.NetworkModule.okHttpClient
) {

    companion object {
        private val defaultInstance by lazy { DynamicDomainRotator() }

        fun getActiveDomain(context: Context, sourceId: String, defaultUrl: String): String {
            return defaultInstance.getActiveBaseUrl(context, sourceId, defaultUrl)
        }

        suspend fun rotateDomain(context: Context, sourceId: String, failingUrl: String): String? {
            return defaultInstance.rotateToNextHealthyMirror(context, sourceId, failingUrl)
        }

        fun rewriteUrl(originalUrl: String, newBaseUrl: String): String {
            return SourceHealthProbe.rewriteUrl(originalUrl, newBaseUrl)
        }

        fun isHealthy(url: String): Boolean {
            return SourceHealthProbe.isHealthy(url)
        }
    }

    /**
     * Mengambil base URL aktif untuk sourceId tertentu dengan fallback ke defaultUrl.
     * Menggunakan persistent cache di SourceBaseUrlStore.
     */
    fun getActiveBaseUrl(context: Context, sourceId: String, defaultUrl: String): String {
        val stored = SourceBaseUrlStore.get(context, sourceId, defaultUrl)
        if (stored.isNotBlank()) {
            return stored
        }
        return defaultUrl.trimEnd('/')
    }

    /**
     * Memeriksa kesehatan domain saat ini dan melakukan failover sekuensial ke mirror berikutnya
     * jika domain saat ini mati.
     */
    suspend fun rotateToNextHealthyMirror(
        context: Context,
        sourceId: String,
        failingUrl: String
    ): String? = withContext(Dispatchers.IO) {
        val healthy = healthProbe.probeAndFailover(context, sourceId, failingUrl)
        if (!healthy.isNullOrBlank()) {
            SourceBaseUrlStore.save(context, sourceId, healthy)
            return@withContext healthy
        }
        null
    }

    /**
     * Menjalankan operasi network suspend dengan mekanisme rotasi domain otomatis jika terjadi kegagalan.
     */
    suspend fun <T> executeWithFailover(
        context: Context,
        sourceId: String,
        initialUrl: String,
        maxRetries: Int = 3,
        block: suspend (effectiveUrl: String) -> T
    ): T {
        var currentUrl = initialUrl
        var attempt = 0
        var lastException: Exception? = null

        while (attempt < maxRetries) {
            try {
                return block(currentUrl)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastException = e
                attempt++

                val newMirror = rotateToNextHealthyMirror(context, sourceId, currentUrl)
                if (!newMirror.isNullOrBlank()) {
                    currentUrl = rewriteUrl(currentUrl, newMirror)
                } else {
                    val nextStaticMirror = SourceEndpointRegistry.getNextMirror(sourceId, currentUrl)
                    if (nextStaticMirror != null) {
                        currentUrl = rewriteUrl(currentUrl, nextStaticMirror)
                        SourceBaseUrlStore.save(context, sourceId, nextStaticMirror)
                    } else {
                        break
                    }
                }
            }
        }

        throw lastException ?: java.io.IOException("DynamicDomainRotator failed for source: $sourceId on url: $initialUrl")
    }

    /**
     * Memverifikasi apakah URL atau host tergolong sehat.
     */
    suspend fun checkHealth(url: String): HealthResult {
        return healthProbe.probeUrl(url)
    }
}
