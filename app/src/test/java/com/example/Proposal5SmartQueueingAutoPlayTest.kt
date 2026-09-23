package com.example

import com.example.data.repository.PlaybackStateRepository
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.drama.DramaAutoQueueEngine
import com.example.data.sources.drama.PreFetchWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Proposal5SmartQueueingAutoPlayTest {

    private lateinit var testScope: TestScope
    private lateinit var playbackStateRepository: PlaybackStateRepository
    private lateinit var preFetchWorker: PreFetchWorker
    private lateinit var autoQueueEngine: DramaAutoQueueEngine

    @Before
    fun setup() {
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        playbackStateRepository = PlaybackStateRepository(historyDao = null, chapterDao = null, scope = testScope)
        preFetchWorker = PreFetchWorker(repository = null, scope = testScope)
        autoQueueEngine = DramaAutoQueueEngine(playbackStateRepository, preFetchWorker)
    }

    @Test
    fun testPlaybackStateRepositoryPositionPersistence() = testScope.runTest {
        playbackStateRepository.savePosition(
            episodeId = "drama_ep_1",
            currentPosMs = 120_000L,
            totalDurationMs = 300_000L,
            isFinished = false,
            seriesId = "series_queen_of_tears",
            seriesTitle = "Queen of Tears"
        )
        advanceUntilIdle()

        assertEquals(120_000L, playbackStateRepository.getPosition("drama_ep_1"))
        assertEquals("drama_ep_1", playbackStateRepository.getLastWatchedEpisode("series_queen_of_tears"))

        playbackStateRepository.savePosition(
            episodeNumber = 2,
            currentPosMs = 45_000L,
            totalDurationMs = 100_000L
        )
        advanceUntilIdle()

        assertEquals(45_000L, playbackStateRepository.getPosition(2))
    }

    @Test
    fun testAutoQueueEngineThresholdFormulationBelow80Percent() {
        var callbackInvoked = false
        preFetchWorker.setCustomFetchBlock { target ->
            callbackInvoked = true
            "https://cdn.example.com/stream/$target.m3u8"
        }

        val totalDuration = 100_000L // 100 seconds
        val currentPosition = 75_000L // 75% (< 80%)

        autoQueueEngine.onPlaybackProgressChanged(
            currentPosMs = currentPosition,
            totalDurationMs = totalDuration,
            currentEpisode = 1
        )

        assertFalse("Pre-fetch must remain Idle when progress < 80%", autoQueueEngine.isPreFetched)
        assertNull(autoQueueEngine.nextEpisodeStream)
        assertFalse(callbackInvoked)
    }

    @Test
    fun testAutoQueueEngineThresholdFormulationAtOrAbove80Percent() = testScope.runTest {
        preFetchWorker.setCustomFetchBlock { target ->
            "https://cdn.example.com/stream/$target.m3u8"
        }

        val totalDuration = 100_000L // 100 seconds
        val currentPosition = 80_000L // 80% (t_trigger reached)

        autoQueueEngine.onPlaybackProgressChanged(
            currentPosMs = currentPosition,
            totalDurationMs = totalDuration,
            currentEpisode = 1
        )

        assertTrue("Pre-fetch must be triggered when progress >= 80%", autoQueueEngine.isPreFetched)

        // Advance coroutines
        advanceUntilIdle()

        // Verify instant stream parameter injection
        assertEquals("https://cdn.example.com/stream/ep_2.m3u8", autoQueueEngine.nextEpisodeStream)
        assertNotNull(autoQueueEngine.nextEpisodeStreamInfo)

        // Verify instant zero-latency transition consumption
        val transitionedStream = autoQueueEngine.getNextStreamForTransition()
        assertEquals("https://cdn.example.com/stream/ep_2.m3u8", transitionedStream)

        // Cache must be consumed and reset
        assertNull(autoQueueEngine.nextEpisodeStream)
        assertFalse(autoQueueEngine.isPreFetched)
    }

    @Test
    fun testAutoQueueEngineWithUrlAndSourceVariant() = testScope.runTest {
        preFetchWorker.setCustomFetchBlock { targetUrl ->
            "https://server2.cdn.net/hls/video_resolved.m3u8"
        }

        val totalDuration = 200_000L
        val currentPosition = 165_000L // 82.5% (> 80%)

        autoQueueEngine.onPlaybackProgressChanged(
            currentPosMs = currentPosition,
            totalDurationMs = totalDuration,
            currentEpisodeId = "ep-05",
            nextEpisodeUrl = "https://drama.com/watch/ep-06",
            sourceId = "viki",
            seriesId = "drama-123"
        )

        assertTrue(autoQueueEngine.isPreFetched)
        advanceUntilIdle()

        val info = autoQueueEngine.getNextStreamInfoForTransition()
        assertNotNull(info)
        assertEquals("https://server2.cdn.net/hls/video_resolved.m3u8", info?.embedUrl)
        assertNull(autoQueueEngine.nextEpisodeStreamInfo)
    }

    @Test
    fun testManualPreFetchInjectionAndReset() {
        val testStreamUrl = "https://vip.stream.org/master.m3u8"
        autoQueueEngine.setPreFetchedStream(testStreamUrl)

        assertTrue(autoQueueEngine.isPreFetched)
        assertEquals(testStreamUrl, autoQueueEngine.nextEpisodeStream)

        autoQueueEngine.reset()
        assertFalse(autoQueueEngine.isPreFetched)
        assertNull(autoQueueEngine.nextEpisodeStream)
        assertNull(autoQueueEngine.nextEpisodeStreamInfo)
    }
}
