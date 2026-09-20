package com.example

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.test.core.app.ApplicationProvider
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import com.example.network.resolver.AnimeMultiServerResolver
import com.example.ui.viewmodels.PlayerState
import com.example.ui.viewmodels.VideoPlayerViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MultiServerFailoverEngineTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testResolverExtractionAndRanking() = runBlocking {
        val resolver = AnimeMultiServerResolver(context)
        val html = """
            <html>
            <body>
                <select id="server">
                    <option value="https://streamwish.to/e/wish123">Streamwish 720p HD</option>
                    <option value="https://filelions.to/v/fl456">FileLions 1080p FHD</option>
                    <option value="https://vidhide.com/v/vh789">VidHide 480p</option>
                </select>
            </body>
            </html>
        """.trimIndent()

        val servers = resolver.extractServersFromHtml(html, "https://example.com/drama/ep-1")
        assertEquals(3, servers.size)
        
        // Streamwish has highest reliability in our ranking map
        assertEquals("Streamwish 720p HD", servers[0].name)
    }

    @Test
    fun testFailoverNextAvailableServerSelection() {
        val resolver = AnimeMultiServerResolver(context)
        val serverWish = VideoServer(name = "Streamwish", embedUrl = "https://streamwish.to/e/1")
        val serverLions = VideoServer(name = "FileLions", embedUrl = "https://filelions.to/v/2")
        val serverVidhide = VideoServer(name = "VidHide", embedUrl = "https://vidhide.com/v/3")

        val available = listOf(serverWish, serverLions, serverVidhide)

        // 1. When serverWish fails, get next
        val next1 = resolver.getNextAvailableServer(serverWish, available, serverWish.embedUrl)
        assertNotNull(next1)
        assertEquals("VidHide", next1?.name) // VidHide (92) > FileLions (90)

        // 2. When serverVidhide also fails, get next
        val next2 = resolver.getNextAvailableServer(next1, available, next1!!.embedUrl)
        assertNotNull(next2)
        assertEquals("FileLions", next2?.name)
    }

    @Test
    fun testVideoPlayerViewModelAutoFailoverOnPlaybackError() = runBlocking {
        val viewModel = VideoPlayerViewModel(
            initialEpisodeId = "ep-1",
            initialTitle = "Episode 1",
            context = context
        )

        val server1 = VideoServer(name = "Server Utama", embedUrl = "https://server1.com/embed")
        val server2 = VideoServer(name = "Server Cadangan 1", embedUrl = "https://streamwish.to/embed")
        val server3 = VideoServer(name = "Server Cadangan 2", embedUrl = "https://vidhide.com/embed")

        viewModel.setAvailableServers(listOf(server1, server2, server3))
        assertEquals("Server Cadangan 1", viewModel.uiState.value.currentServer?.name) // Ranked highest (Streamwish)

        // Trigger Playback Error on current server
        val ex = PlaybackException("HTTP 403 Forbidden", null, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
        viewModel.handlePlayerError(error = ex, failedUrl = "https://streamwish.to/embed")

        // Should automatically switch to next mirror (VidHide)
        assertNotNull(viewModel.uiState.value.currentServer)
        assertEquals("Server Cadangan 2", viewModel.uiState.value.currentServer?.name)
    }

    @Test
    fun testVideoPlayerViewModelExhaustedServersState() {
        val viewModel = VideoPlayerViewModel(
            initialEpisodeId = "ep-1",
            initialTitle = "Episode 1",
            context = context
        )

        val server1 = VideoServer(name = "Single Server", embedUrl = "https://single.com/video.mp4")
        viewModel.setAvailableServers(listOf(server1))

        val ex = PlaybackException("HTTP 404 Not Found", null, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        viewModel.handlePlayerError(error = ex, failedUrl = "https://single.com/video.mp4")

        // When no more servers available, should transition to PlayerState.Error
        val state = viewModel.uiState.value.streamInfo
        assertTrue("State must be Error when all servers are exhausted", state is PlayerState.Error)
    }
}
