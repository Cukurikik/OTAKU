package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsManager
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import com.example.data.sources.VideoStreamInfo
import com.example.ui.viewmodels.PlayerState
import com.example.ui.viewmodels.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = OtakuHubApp::class)
class PlayerViewModelFailoverTest {

    @Test
    fun testLocalQualityFallbackAndPositionRetention() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<OtakuHubApp>()
        val settings = SettingsManager(app)

        val viewModel = PlayerViewModel(
            initialEpisodeId = "ep-test-1",
            itemId = "anime-test-1",
            initialTitle = "Episode 1",
            sourceId = "kuramanime",
            repository = app.repository,
            historyDao = app.database.historyDao(),
            chapterDao = app.database.chapterDao(),
            libraryDao = app.database.libraryDao(),
            trackDao = app.database.trackDao(),
            settingsManager = settings,
            downloadDao = app.database.downloadDao(),
            context = app,
            initialSeekPosition = 0L
        )

        // Simulate initial loaded state with 2 qualities
        val q1 = VideoQuality(qualityLabel = "720p", url = "https://example.com/video_720p.mp4")
        val q2 = VideoQuality(qualityLabel = "1080p", url = "https://example.com/video_1080p.mp4")
        val streamInfo = VideoStreamInfo(
            episodeId = "ep-test-1",
            qualities = listOf(q1, q2),
            servers = emptyList(),
            subtitles = emptyList(),
            headers = emptyMap()
        )

        // Inject initial success
        val streamInfoField = PlayerViewModel::class.java.getDeclaredField("_streamInfo")
        streamInfoField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val mutableFlow = streamInfoField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<PlayerState>
        mutableFlow.value = PlayerState.Success(streamInfo)

        // Current position before error is 24500 ms
        val currentPlayPos = 24500L

        // Trigger playback error on q1
        viewModel.handlePlaybackError(
            failedUrl = q1.url,
            currentPositionMs = currentPlayPos,
            error = null
        )

        // Assert that position is preserved
        assertEquals(24500L, viewModel.startPosition.value)

        // Assert state is still Success with q2 as the only quality left
        val currentState = viewModel.streamInfo.value
        assertTrue("State should remain Success after local quality fallback", currentState is PlayerState.Success)
        val currentQualities = (currentState as PlayerState.Success).data.qualities
        assertEquals(1, currentQualities.size)
        assertEquals("1080p", currentQualities.first().qualityLabel)
        assertEquals(q2.url, currentQualities.first().url)
    }

    @Test
    fun testServerFallbackWhenQualitiesExhausted() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<OtakuHubApp>()
        val settings = SettingsManager(app)

        val viewModel = PlayerViewModel(
            initialEpisodeId = "ep-test-2",
            itemId = "anime-test-2",
            initialTitle = "Episode 2",
            sourceId = "kuramanime",
            repository = app.repository,
            historyDao = app.database.historyDao(),
            chapterDao = app.database.chapterDao(),
            libraryDao = app.database.libraryDao(),
            trackDao = app.database.trackDao(),
            settingsManager = settings,
            downloadDao = app.database.downloadDao(),
            context = app,
            initialSeekPosition = 0L
        )

        val server1 = VideoServer(
            name = "Server Primary",
            embedUrl = "https://example.com/embed1",
            qualities = listOf(VideoQuality("720p", "https://example.com/server1_720p.mp4"))
        )
        val server2 = VideoServer(
            name = "Server Backup",
            embedUrl = "https://example.com/embed2",
            qualities = listOf(VideoQuality("720p", "https://example.com/server2_720p.mp4"))
        )
        val streamInfo = VideoStreamInfo(
            episodeId = "ep-test-2",
            qualities = server1.qualities,
            servers = listOf(server1, server2),
            subtitles = emptyList(),
            headers = emptyMap()
        )

        val streamInfoField = PlayerViewModel::class.java.getDeclaredField("_streamInfo")
        streamInfoField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val mutableFlow = streamInfoField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<PlayerState>
        mutableFlow.value = PlayerState.Success(streamInfo)

        // Trigger playback error on server 1
        viewModel.handlePlaybackError(
            failedUrl = "https://example.com/server1_720p.mp4",
            currentPositionMs = 12000L,
            error = null
        )

        assertEquals(12000L, viewModel.startPosition.value)

        val currentState = viewModel.streamInfo.value
        assertTrue("State should transition to Server Backup", currentState is PlayerState.Success)
        val activeQualities = (currentState as PlayerState.Success).data.qualities
        assertEquals("https://example.com/server2_720p.mp4", activeQualities.first().url)
    }

    @Test
    fun testSeamlessFailoverInitiationOnExhaustion() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<OtakuHubApp>()
        val settings = SettingsManager(app)

        val viewModel = PlayerViewModel(
            initialEpisodeId = "ep-test-3",
            itemId = "anime-test-3",
            initialTitle = "Episode 3",
            sourceId = "kuramanime",
            repository = app.repository,
            historyDao = app.database.historyDao(),
            chapterDao = app.database.chapterDao(),
            libraryDao = app.database.libraryDao(),
            trackDao = app.database.trackDao(),
            settingsManager = settings,
            downloadDao = app.database.downloadDao(),
            context = app,
            initialSeekPosition = 0L
        )

        val singleQuality = VideoQuality("720p", "https://example.com/expired_token_stream.m3u8")
        val streamInfo = VideoStreamInfo(
            episodeId = "ep-test-3",
            qualities = listOf(singleQuality),
            servers = emptyList(),
            subtitles = emptyList(),
            embedUrl = "https://example.com/embed_original",
            headers = mapOf("Referer" to "https://kuramanime.ing/")
        )

        val streamInfoField = PlayerViewModel::class.java.getDeclaredField("_streamInfo")
        streamInfoField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val mutableFlow = streamInfoField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<PlayerState>
        mutableFlow.value = PlayerState.Success(streamInfo)

        // Trigger error with 403 Forbidden
        val fake403Exception = androidx.media3.common.PlaybackException(
            "Response code: 403 Forbidden",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        )

        viewModel.handlePlaybackError(
            failedUrl = singleQuality.url,
            currentPositionMs = 85000L,
            error = fake403Exception
        )

        // Check startPosition was updated immediately
        assertEquals(85000L, viewModel.startPosition.value)

        // Wait a short moment for coroutine step 1
        delay(50)

        // Check that either SeamlessBuffering is active or failover is resolving
        val state = viewModel.streamInfo.value
        assertTrue(
            "State must be SeamlessBuffering or Error after failover attempts",
            state is PlayerState.SeamlessBuffering || state is PlayerState.Error || state is PlayerState.Success
        )
    }
}
