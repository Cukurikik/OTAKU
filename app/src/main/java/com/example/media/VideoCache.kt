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
 * High-performance VideoCache Singleton for HLS and media chunk caching
 * based on 500MB LeastRecentlyUsedCacheEvictor.
 */
@OptIn(UnstableApi::class)
object VideoCache {
    private var instance: SimpleCache? = null

    @Synchronized
    fun getInstance(context: Context): SimpleCache {
        return com.example.data.VideoCache.getInstance(context)
    }

    fun buildCacheDataSourceFactory(
        context: Context,
        upstreamDataSourceFactory: DataSource.Factory
    ): DataSource.Factory {
        return com.example.data.VideoCache.buildCacheDataSourceFactory(context, upstreamDataSourceFactory)
    }
}
