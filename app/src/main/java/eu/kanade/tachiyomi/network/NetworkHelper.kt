package eu.kanade.tachiyomi.network

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class NetworkHelper(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "network_cache")
    private val cacheSize = 10L * 1024L * 1024L // 10 MiB

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(com.example.data.sources.WebkitCookieJar())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cache(Cache(cacheDir, cacheSize))
            .build()
    }

    val cloudflareClient: OkHttpClient by lazy {
        client.newBuilder()
            .addInterceptor(eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor(context))
            .build()
    }
}
