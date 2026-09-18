package com.example.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object VideoCache {
    private var instance: SimpleCache? = null

    /**
     * Singleton instance of SimpleCache using 500MB LeastRecentlyUsedCacheEvictor
     * for HLS segment (.ts / .m3u8) and video stream disk caching.
     */
    @Synchronized
    fun getInstance(context: Context): SimpleCache {
        if (instance == null) {
            val cacheSize = 500L * 1024 * 1024 // 500MB LRU Cache allocation
            val evictor = LeastRecentlyUsedCacheEvictor(cacheSize)
            val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            val cacheDir = File(context.applicationContext.cacheDir, "media_hls_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            instance = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return instance!!
    }

    /**
     * Helper factory to build a CacheDataSource.Factory configured with VideoCache
     * and FLAG_IGNORE_CACHE_ON_ERROR for high stream resilience.
     */
    fun buildCacheDataSourceFactory(
        context: Context,
        upstreamDataSourceFactory: DataSource.Factory
    ): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(getInstance(context))
            .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}

