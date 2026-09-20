package com.example

import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import com.example.data.sources.VideoStreamInfo
import com.example.ui.viewmodels.PlayerState
import com.example.ui.viewmodels.VideoPlayerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit Test Suite for Proposal 6: Multi-Server Mirror Switcher.
 * Tests server state propagation, switching logic, quality preservation, and fallback mechanisms.
 */
class ServerSwitcherEngineTest {

    @Test
    fun testServerListPropagation() {
        val servers = listOf(
            VideoServer(
                name = "Server Utama (Blogger)",
                embedUrl = "https://blogger.com/video/123",
                qualities = listOf(
                    VideoQuality("1080p", "https://stream.blogger.com/1080.mp4"),
                    VideoQuality("720p", "https://stream.blogger.com/720.mp4")
                )
            ),
            VideoServer(
                name = "Server Fast (Hydrax)",
                embedUrl = "https://hydrax.net/watch/456",
                qualities = listOf(
                    VideoQuality("720p", "https://stream.hydrax.net/720.m3u8", isM3U8 = true)
                )
            ),
            VideoServer(
                name = "Server VIP (StreamWish)",
                embedUrl = "https://streamwish.to/e/789",
                qualities = listOf(
                    VideoQuality("480p", "https://streamwish.to/480.mp4")
                )
            )
        )

        val viewModel = VideoPlayerViewModel(
            initialEpisodeId = "ep-101",
            initialTitle = "Episode 1"
        )

        viewModel.setAvailableServers(servers)

        val state = viewModel.uiState.value
        assertEquals(3, state.availableServers.size)
        assertEquals("Server Utama (Blogger)", state.currentServerName)
        assertEquals("Server Utama (Blogger)", state.currentServer?.name)
    }

    @Test
    fun testInstantServerSwitching() {
        val serverA = VideoServer(
            name = "Server Utama",
            embedUrl = "https://server1.com/play/1",
            qualities = listOf(VideoQuality("720p", "https://server1.com/720.mp4"))
        )
        val serverB = VideoServer(
            name = "Server Mirror Hydrax",
            embedUrl = "https://hydrax.net/embed/2",
            qualities = listOf(VideoQuality("1080p", "https://hydrax.net/1080.mp4"))
        )

        val viewModel = VideoPlayerViewModel(
            initialEpisodeId = "ep-101",
            initialTitle = "Episode 1"
        )
        viewModel.setAvailableServers(listOf(serverA, serverB))

        // Initial check
        assertEquals("Server Utama", viewModel.uiState.value.currentServerName)

        // Switch to server B
        viewModel.switchServer(serverB, currentPositionMs = 45000L)

        val newState = viewModel.uiState.value
        assertEquals("Server Mirror Hydrax", newState.currentServerName)
        assertEquals("Server Mirror Hydrax", newState.currentServer?.name)
        assertEquals("1080p", newState.currentQualityLabel)

        val streamState = newState.streamInfo
        assertTrue(streamState is PlayerState.Success)
        val streamInfo = (streamState as PlayerState.Success).data
        assertEquals("https://hydrax.net/embed/2", streamInfo.embedUrl)
        assertEquals(1, streamInfo.qualities.size)
        assertEquals("1080p", streamInfo.qualities.first().qualityLabel)
    }

    @Test
    fun testServerSwitcherToggleState() {
        val viewModel = VideoPlayerViewModel(
            initialEpisodeId = "ep-101",
            initialTitle = "Episode 1"
        )

        assertEquals(false, viewModel.uiState.value.showServerSwitcher)

        viewModel.toggleServerSwitcher(true)
        assertEquals(true, viewModel.uiState.value.showServerSwitcher)

        viewModel.toggleServerSwitcher(false)
        assertEquals(false, viewModel.uiState.value.showServerSwitcher)
    }

    @Test
    fun testVideoStreamInfoMultiServerIntegrity() {
        val servers = listOf(
            VideoServer("Fast3", "https://fast3.com/v/1"),
            VideoServer("StreamWish", "https://streamwish.com/v/2")
        )
        val info = VideoStreamInfo(
            episodeId = "ep-200",
            title = "Episode 200",
            embedUrl = "https://fast3.com/v/1",
            servers = servers
        )

        assertEquals(2, info.servers.size)
        assertEquals("Fast3", info.servers[0].name)
        assertEquals("StreamWish", info.servers[1].name)
    }
}
