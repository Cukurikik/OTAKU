package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.models.Chapter
import com.example.ui.player.PlaylistQueue
import com.example.ui.viewmodels.PlayerViewModel
import com.example.ui.viewmodels.VideoPlayerViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = OtakuHubApp::class)
class ContinuousPlaybackEngineTest {

    @Test
    fun testPlaylistQueueAscendingOrdering() {
        val episodes = listOf(
            Chapter(id = "ep-1", title = "Episode 1", url = "https://example.com/drama/ep1"),
            Chapter(id = "ep-2", title = "Episode 2", url = "https://example.com/drama/ep2"),
            Chapter(id = "ep-3", title = "Episode 3", url = "https://example.com/drama/ep3")
        )

        val queue = PlaylistQueue(initialEpisodes = episodes, initialEpisodeId = "ep-1")
        assertTrue(queue.hasNext())
        assertFalse(queue.hasPrevious())
        assertEquals("ep-2", queue.getNextEpisode()?.id)
        assertEquals("https://example.com/drama/ep2", queue.getNextEpisodeUrl())

        // Advance to Episode 2
        queue.setCurrentEpisodeId("ep-2")
        assertTrue(queue.hasNext())
        assertTrue(queue.hasPrevious())
        assertEquals("ep-3", queue.getNextEpisode()?.id)
        assertEquals("ep-1", queue.getPreviousEpisode()?.id)

        // Advance to Episode 3 (Last episode)
        queue.setCurrentEpisodeId("ep-3")
        assertFalse(queue.hasNext())
        assertTrue(queue.hasPrevious())
        assertNull(queue.getNextEpisode())
    }

    @Test
    fun testPlaylistQueueDescendingOrdering() {
        // Scraped sites often list episodes newest first: Ep 12 -> Ep 10
        val episodes = listOf(
            Chapter(id = "ep-12", title = "Episode 12", url = "https://example.com/drama/ep12"),
            Chapter(id = "ep-11", title = "Episode 11", url = "https://example.com/drama/ep11"),
            Chapter(id = "ep-10", title = "Episode 10", url = "https://example.com/drama/ep10")
        )

        // Watching Episode 10 (chronologically next is Episode 11, which is at previous index in descending list)
        val queue = PlaylistQueue(initialEpisodes = episodes, initialEpisodeId = "ep-10")
        assertTrue(queue.hasNext())
        assertEquals("ep-11", queue.getNextEpisode()?.id)

        // Watching Episode 11
        queue.setCurrentEpisodeId("ep-11")
        assertEquals("ep-12", queue.getNextEpisode()?.id)
        assertEquals("ep-10", queue.getPreviousEpisode()?.id)

        // Watching Episode 12 (latest)
        queue.setCurrentEpisodeId("ep-12")
        assertNull(queue.getNextEpisode())
    }

    @Test
    fun testVideoPlayerViewModelContinuousPlaybackTriggerAndCancellation() {
        val viewModel = VideoPlayerViewModel(
            initialEpisodeId = "ep-1",
            itemId = "series-1",
            initialTitle = "Episode 1",
            sourceId = "test_source"
        )

        val episodes = listOf(
            Chapter(id = "ep-1", title = "Episode 1", url = "https://example.com/ep1"),
            Chapter(id = "ep-2", title = "Episode 2", url = "https://example.com/ep2")
        )
        viewModel.setPlaylistEpisodes(episodes, "ep-1")

        // 1. Duration 120s, currentPosition 100s -> t_sisa = 20s (> 10s) -> Overlay must NOT be shown
        viewModel.checkContinuousPlayback(currentPosition = 100_000L, duration = 120_000L)
        assertFalse("Overlay should not trigger when remaining time > 10s", viewModel.uiState.value.showAutoNextOverlay)

        // 2. Duration 120s, currentPosition 112s -> t_sisa = 8s (<= 10s) -> Overlay MUST be shown
        viewModel.checkContinuousPlayback(currentPosition = 112_000L, duration = 120_000L)
        assertTrue("Overlay should trigger when remaining time <= 10s", viewModel.uiState.value.showAutoNextOverlay)
        assertEquals(5, viewModel.uiState.value.autoNextCountdownSeconds)

        // 3. User cancels auto-next
        viewModel.cancelAutoNext()
        assertFalse("Overlay should be dismissed after cancel", viewModel.uiState.value.showAutoNextOverlay)
        assertTrue("isAutoNextCancelled should be true", viewModel.isAutoNextCancelled)

        // 4. Repeated check should NOT re-trigger overlay if cancelled
        viewModel.checkContinuousPlayback(currentPosition = 115_000L, duration = 120_000L)
        assertFalse("Overlay should not re-trigger after being cancelled", viewModel.uiState.value.showAutoNextOverlay)

        // 5. On end-of-stream when cancelled, does NOT auto-advance
        viewModel.onEndOfStream()
        assertEquals("ep-1", viewModel.uiState.value.currentEpisodeId)

        // 6. Manual Play Next Episode resets cancellation and advances to Episode 2
        viewModel.playNextEpisode()
        assertEquals("ep-2", viewModel.uiState.value.currentEpisodeId)
        assertFalse("Cancellation should be reset on manual or new episode play", viewModel.isAutoNextCancelled)
    }

    @Test
    fun testPlayerViewModelContinuousPlaybackIntegration() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<OtakuHubApp>()
        val settings = com.example.data.SettingsManager(app)

        val viewModel = PlayerViewModel(
            initialEpisodeId = "ep-1",
            itemId = "drama-queen-1",
            initialTitle = "Episode 1",
            sourceId = "test_source",
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

        val episodes = listOf(
            Chapter(id = "ep-1", title = "Episode 1", url = "https://example.com/ep1"),
            Chapter(id = "ep-2", title = "Episode 2", url = "https://example.com/ep2")
        )
        viewModel.playlistQueue.setEpisodes(episodes, "ep-1")
        viewModel.nextEpisode.value = episodes[1]

        // Check countdown trigger at remaining time 9s
        viewModel.checkContinuousPlayback(currentPosition = 51_000L, duration = 60_000L)
        assertTrue("PlayerViewModel should show countdown overlay at remaining <= 10s", viewModel.showAutoNextOverlay.value)

        // User cancels
        viewModel.cancelAutoNext()
        assertFalse(viewModel.showAutoNextOverlay.value)
        assertTrue(viewModel.isAutoNextCancelled)

        // Advance to next episode
        viewModel.playNextEpisode()
        assertEquals("Episode 2", viewModel.currentTitle.value)
        assertFalse(viewModel.isAutoNextCancelled)
    }
}
