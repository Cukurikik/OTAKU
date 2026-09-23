package com.example.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.SimpleCache
import java.util.concurrent.atomic.AtomicLong

/**
 * HlsSegmentCacheProxy:
 * Local segment cache proxy and coordinator for HLS (.m3u8, .ts, .m4s) and high-bitrate video streams.
 *
 * Implements formulation:
 * Cache_hit = ServeFromDisk(Segment_i) if Segment_i in DiskCache
 *             FetchFromCDN(Segment_i) if Segment_i not in DiskCache
 *
 * Provides:
 * - Deterministic segment cache key extraction (stripping volatile CDN auth tokens)
 * - Real-time Cache Hit / Miss telemetry
 * - Instant seek response by avoiding redundant network re-downloads
 */
@OptIn(UnstableApi::class)
class HlsSegmentCacheProxy(
    private val context: Context,
    private val cacheDataSourceFactory: CacheDataSourceFactory = CacheDataSourceFactory(context)
) {

    private val simpleCache: SimpleCache
        get() = cacheDataSourceFactory.simpleCache

    private val _cacheHitCount = AtomicLong(0L)
    private val _cacheMissCount = AtomicLong(0L)
    private val _bytesFromCache = AtomicLong(0L)
    private val _bytesFromNetwork = AtomicLong(0L)

    val cacheHitCount: Long
        get() = _cacheHitCount.get()

    val cacheMissCount: Long
        get() = _cacheMissCount.get()

    val bytesFromCache: Long
        get() = _bytesFromCache.get()

    val bytesFromNetwork: Long
        get() = _bytesFromNetwork.get()

    /**
     * Custom deterministic CacheKeyFactory for HLS chunks.
     * Removes dynamic temporary tokens, expirations, and signatures from URLs
     * so chunks remain reusable across requests.
     */
    val deterministicCacheKeyFactory: CacheKeyFactory = CacheKeyFactory { dataSpec ->
        buildDeterministicCacheKey(dataSpec.uri.toString())
    }

    /**
     * Checks if a video or HLS segment is already fully or partially present in the disk cache.
     */
    fun isSegmentCached(uriString: String, position: Long = 0L, length: Long = 1024L): Boolean {
        if (uriString.isBlank()) return false
        val cacheKey = buildDeterministicCacheKey(uriString)
        val isCached = simpleCache.isCached(cacheKey, position, length)
        return isCached
    }

    /**
     * Resolves segment request following the formulation:
     * Cache_hit: ServeFromDisk if cached
     * Cache_miss: FetchFromCDN if not cached
     */
    fun resolveSegmentSource(uriString: String): SegmentSource {
        val cached = isSegmentCached(uriString)
        return if (cached) {
            _cacheHitCount.incrementAndGet()
            SegmentSource.DISK_CACHE
        } else {
            _cacheMissCount.incrementAndGet()
            SegmentSource.CDN_NETWORK
        }
    }

    /**
     * Records data transfer telemetry.
     */
    fun recordTransfer(bytes: Long, fromDisk: Boolean) {
        if (fromDisk) {
            _bytesFromCache.addAndGet(bytes)
        } else {
            _bytesFromNetwork.addAndGet(bytes)
        }
    }

    /**
     * Calculates current cache hit ratio:
     * Cache_hit_ratio = Hits / (Hits + Misses)
     */
    fun getCacheHitRatio(): Float {
        val hits = _cacheHitCount.get()
        val total = hits + _cacheMissCount.get()
        if (total <= 0L) return 0f
        return (hits.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Creates an optimized CacheDataSource.Factory with deterministic HLS segment key resolution.
     */
    fun createHlsCachingDataSourceFactory(upstreamFactory: DataSource.Factory): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheKeyFactory(deterministicCacheKeyFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Returns total current cached bytes in disk cache.
     */
    fun getDiskCacheSizeBytes(): Long {
        return simpleCache.cacheSpace
    }

    /**
     * Clears telemetry metrics.
     */
    fun resetMetrics() {
        _cacheHitCount.set(0L)
        _cacheMissCount.set(0L)
        _bytesFromCache.set(0L)
        _bytesFromNetwork.set(0L)
    }

    companion object {
        enum class SegmentSource {
            DISK_CACHE,
            CDN_NETWORK
        }

        /**
         * Strips query parameters or sanitizes URL to create a deterministic cache key.
         */
        fun buildDeterministicCacheKey(uriString: String): String {
            return try {
                val uri = Uri.parse(uriString)
                val path = uri.path ?: uriString
                // If it's a media chunk (.ts, .m4s, .mp4, .aac), query tokens should not fragment cache
                if (path.endsWith(".ts") || path.endsWith(".m4s") || path.endsWith(".mp4") || path.endsWith(".aac")) {
                    "${uri.scheme}://${uri.host}$path"
                } else {
                    uriString
                }
            } catch (e: Exception) {
                uriString
            }
        }
    }
}
