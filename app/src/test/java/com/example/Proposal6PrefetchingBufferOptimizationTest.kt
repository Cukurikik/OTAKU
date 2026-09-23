package com.example

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.test.core.app.ApplicationProvider
import com.example.media.AdaptiveLoadControlBuilder
import com.example.media.CacheDataSourceFactory
import com.example.media.ExoPlayerManager
import com.example.media.ExoPlayerMediaItemFactory
import com.example.media.HlsSegmentCacheProxy
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class Proposal6PrefetchingBufferOptimizationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
    }

    @Test
    fun testAdaptiveLoadControlBuilderFormulationAndParameters() {
        val builder = AdaptiveLoadControlBuilder()

        // 1. Verify Buffer Matrix Values
        assertEquals(60_000, builder.minBufferMs)
        assertEquals(180_000, builder.maxBufferMs)
        assertEquals(2500, builder.bufferForPlaybackMs)
        assertEquals(5000, builder.bufferForPlaybackAfterRebufferMs)

        // 2. Verify Formulation: Buffer_capacity = Bitrate_stream * t_buffer_target
        // 5 Mbps (5,000,000 bps) for 180 seconds = 900,000,000 bits = 112,500,000 bytes
        val bitrate5Mbps = 5_000_000L
        val targetBuffer180Sec = 180
        val expectedCapacityBytes = (bitrate5Mbps * targetBuffer180Sec) / 8L // 112,500,000 bytes

        val calculated = AdaptiveLoadControlBuilder.calculateBufferCapacityBytes(bitrate5Mbps, targetBuffer180Sec)
        assertEquals(expectedCapacityBytes, calculated)
        assertEquals(112_500_000L, calculated)

        // 3. Verify DefaultLoadControl Creation with Allocator
        val loadControl = builder.buildDefaultLoadControl()
        assertNotNull("DefaultLoadControl must be built successfully", loadControl)
        assertNotNull("LoadControl allocator must be initialized", loadControl.allocator)
    }

    @Test
    fun testCacheDataSourceFactoryDiskCapacity() {
        val factory = CacheDataSourceFactory(context)
        assertEquals(512L * 1024L * 1024L, factory.maxCacheSize) // 512 MB LRU cache

        val simpleCache = factory.simpleCache
        assertNotNull("SimpleCache must not be null", simpleCache)

        val upstreamFactory = DefaultDataSource.Factory(context)
        val cacheDataSourceFactory = factory.createDataSourceFactory(upstreamFactory)
        assertNotNull("CacheDataSource.Factory must be created", cacheDataSourceFactory)
    }

    @Test
    fun testHlsSegmentCacheProxyFormulationAndDeterministicKeys() {
        val hlsProxy = HlsSegmentCacheProxy(context)

        // 1. Verify Deterministic Cache Key (Stripping volatile CDN tokens from segments)
        val volatileTokenUrl = "https://cdn.anime-aggregator.com/video/ep01/segment123.ts?token=xyz123&expires=1790000000"
        val expectedDeterministicKey = "https://cdn.anime-aggregator.com/video/ep01/segment123.ts"
        val actualKey = HlsSegmentCacheProxy.buildDeterministicCacheKey(volatileTokenUrl)
        assertEquals(expectedDeterministicKey, actualKey)

        // Non-segment URI retains query if necessary
        val nonSegmentUrl = "https://stream.server.com/api/get_master.m3u8?auth=abc"
        assertEquals(nonSegmentUrl, HlsSegmentCacheProxy.buildDeterministicCacheKey(nonSegmentUrl))

        // 2. Verify Formulation:
        // Cache_hit = ServeFromDisk if in DiskCache, else FetchFromCDN
        hlsProxy.resetMetrics()
        val uncachedSegment = "https://cdn.example.com/stream/segment0001.ts"
        val source = hlsProxy.resolveSegmentSource(uncachedSegment)

        assertEquals(HlsSegmentCacheProxy.Companion.SegmentSource.CDN_NETWORK, source)
        assertEquals(0L, hlsProxy.cacheHitCount)
        assertEquals(1L, hlsProxy.cacheMissCount)
        assertEquals(0f, hlsProxy.getCacheHitRatio(), 0.001f)

        // 3. Verify Telemetry Tracking
        hlsProxy.recordTransfer(bytes = 2048L, fromDisk = false)
        assertEquals(2048L, hlsProxy.bytesFromNetwork)
        assertEquals(0L, hlsProxy.bytesFromCache)

        hlsProxy.recordTransfer(bytes = 4096L, fromDisk = true)
        assertEquals(4096L, hlsProxy.bytesFromCache)
    }

    @Test
    fun testExoPlayerMediaItemFactoryHlsOptimization() {
        val testHlsUrl = "https://cdn.anime-aggregator.com/master.m3u8"
        val mediaItem = ExoPlayerMediaItemFactory.createHlsMediaItem(
            hlsUri = testHlsUrl,
            customCacheKey = "hls_ep_01_cache_key"
        )

        assertNotNull(mediaItem)
        assertEquals(testHlsUrl, mediaItem.localConfiguration?.uri.toString())
        assertEquals(MimeTypes.APPLICATION_M3U8, mediaItem.localConfiguration?.mimeType)
        assertEquals("hls_ep_01_cache_key", mediaItem.localConfiguration?.customCacheKey)

        // Test adaptive builder
        val adaptiveItem = ExoPlayerMediaItemFactory.createAdaptiveMediaItem(testHlsUrl)
        assertEquals(MimeTypes.APPLICATION_M3U8, adaptiveItem.localConfiguration?.mimeType)
    }

    @Test
    fun testExoPlayerManagerAdaptiveIntegration() {
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        val trackSelector = DefaultTrackSelector(context, AdaptiveTrackSelection.Factory())

        val player = ExoPlayerManager.buildPlayer(
            context = context,
            trackSelector = trackSelector,
            bandwidthMeter = bandwidthMeter,
            headersProvider = { mapOf("User-Agent" to "AnimeApp/1.0") },
            streamUrlProvider = { "https://cdn.test.com/stream/video.mp4" }
        )

        try {
            assertNotNull("ExoPlayer must be instantiated with AdaptiveLoadControlBuilder and CacheDataSource", player)
        } finally {
            player.release()
        }
    }
}
