package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adapter untuk Tachiyomi NetworkHelper yang mendelegasikan pemrosesan Cloudflare
 * ke implementasi terpusat com.example.network.interceptor.CloudflareInterceptor
 * dengan dukungan Stealth.js dan Interactive Turnstile Challenge Solver.
 */
class CloudflareInterceptor(private val context: Context) : Interceptor {
    private val delegate = com.example.network.interceptor.CloudflareInterceptor(context)

    override fun intercept(chain: Interceptor.Chain): Response {
        return delegate.intercept(chain)
    }
}
