package com.example

import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.net.URL
import java.net.URLEncoder

class CoilConfig(private val context: Context) : ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return createReaderImageLoader(context)
    }

    companion object {
        fun createReaderImageLoader(context: Context): ImageLoader {
            return ImageLoader.Builder(context)
                .memoryCache {
                    MemoryCache.Builder(context)
                        .maxSizePercent(0.15) // Limit memory cache to 15% to prevent OOM on long Webtoons
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("manga_page_cache"))
                        .maxSizeBytes(500L * 1024 * 1024) // 500MB Disk Cache
                        .build()
                }
                .respectCacheHeaders(false)
                .components {
                    add(com.example.data.sources.local.CbzFetcher.Factory())
                }
                .okHttpClient {
                    buildCoilOkHttpClient()
                }
                .build()
        }

        fun buildCoilOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .addInterceptor(object : Interceptor {
                    override fun intercept(chain: Interceptor.Chain): Response {
                        val request = chain.request()
                        val url = request.url.toString()
                        
                        // Prevent infinite loops if Coil tries to load worker URL directly (though it shouldn't)
                        if (url.startsWith("https://anime-aggregator.njirlah.workers.dev")) {
                            return chain.proceed(request)
                        }
                        
                        val host = try {
                            URL(url).host
                        } catch (e: Exception) {
                            ""
                        }
                        
                        val builder = request.newBuilder()
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        
                        if (host.isNotEmpty()) {
                            builder.addHeader("Referer", "https://$host/")
                            val cookie = android.webkit.CookieManager.getInstance().getCookie("https://$host")
                            if (cookie != null) builder.addHeader("Cookie", cookie)
                        }
                        
                        val response = chain.proceed(builder.build())
                        
                        // If forbidden (Cloudflare block), retry with worker
                        val contentType = response.header("Content-Type") ?: ""
                        if (response.code == 403 || response.code == 503 || contentType.contains("text/html")) {
                            response.close() // Close original response
                            
                            val workerUrl = "https://anime-aggregator.njirlah.workers.dev/?url=" + URLEncoder.encode(url, "UTF-8")
                            val appCheckToken = com.example.data.sync.CloudSyncManager.getAppCheckTokenSync()
                            val workerRequestBuilder = request.newBuilder()
                                .url(workerUrl)
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                .header("Authorization", "Bearer cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4")
                            if (!appCheckToken.isNullOrBlank()) {
                                workerRequestBuilder.header("X-Firebase-AppCheck", appCheckToken)
                            }
                            return chain.proceed(workerRequestBuilder.build())
                        }
                        
                        return response
                    }
                })
                .build()
        }
    }
}

/**
 * Global helper function conforming to specifications for reader image loading
 */
fun createReaderImageLoader(context: Context): ImageLoader {
    return CoilConfig.createReaderImageLoader(context)
}

