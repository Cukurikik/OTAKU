package com.example.network

import android.util.Log
import com.example.OtakuHubApp
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * AppCheckInterceptor: Automatically retrieves Firebase AppCheck token and injects
 * the 'X-Firebase-AppCheck' header for requests dispatched to the Cloudflare Worker proxy
 * or any protected cloud services.
 */
class AppCheckInterceptor : Interceptor {

    companion object {
        private const val TAG = "AppCheckInterceptor"
        const val HEADER_APP_CHECK = "X-Firebase-AppCheck"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url.toString()

        val isProtectedEndpoint = url.contains("anime-aggregator.njirlah.workers.dev") ||
                url.contains("workers.dev") ||
                url.contains("cloudfunctions.net") ||
                original.header("X-Requires-AppCheck") != null

        val builder = original.newBuilder()
        if (original.header("X-Requires-AppCheck") != null) {
            builder.removeHeader("X-Requires-AppCheck")
        }

        // If not a protected endpoint or already has header, proceed
        if (!isProtectedEndpoint || original.header(HEADER_APP_CHECK) != null) {
            return chain.proceed(builder.build())
        }

        // Inject AppCheck token
        try {
            val token = com.example.data.sync.CloudSyncManager.getAppCheckTokenSync()
            if (!token.isNullOrBlank()) {
                builder.header(HEADER_APP_CHECK, token)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error attaching AppCheck token to request: ${e.message}")
        }

        return chain.proceed(builder.build())
    }
}
