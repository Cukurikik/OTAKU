package com.example.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * CacheDataSourceFactory:
 * High-efficiency segment disk cache provider for high-bitrate media streams (Drama / Anime).
 * Allocates 512 MB LRU cache disk storage to prevent redundant network re-downloads
 * and ensure seamless backward/forward seek operations.
 */
@OptIn(UnstableApi::class)
class CacheDataSourceFactory(
    private val context: Context,
    val maxCacheSize: Long = DEFAULT_MAX_CACHE_SIZE
) {

    companion object {
        const val DEFAULT_MAX_CACHE_SIZE: Long = 1024L * 1024L * 512L // 512 MB Disk Cache
        private const val CACHE_DIR_NAME = "media_segment_cache"

        @Volatile
        private var sharedCacheInstance: SimpleCache? = null

        @Synchronized
        fun getSharedCache(context: Context, maxCacheSize: Long = DEFAULT_MAX_CACHE_SIZE): SimpleCache {
            if (sharedCacheInstance == null) {
                val cacheDir = File(context.applicationContext.cacheDir, CACHE_DIR_NAME)
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                val evictor = LeastRecentlyUsedCacheEvictor(maxCacheSize)
                val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
                sharedCacheInstance = SimpleCache(cacheDir, evictor, databaseProvider)
            }
            return sharedCacheInstance!!
        }
    }

    val simpleCache: SimpleCache by lazy {
        getSharedCache(context, maxCacheSize)
    }

    /**
     * Builds CacheDataSource.Factory wrapped around upstream factory with FLAG_IGNORE_CACHE_ON_ERROR.
     */
    fun createDataSourceFactory(upstreamFactory: DataSource.Factory): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
