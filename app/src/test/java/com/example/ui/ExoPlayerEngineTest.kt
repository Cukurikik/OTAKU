package com.example.ui

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.data.models.Chapter
import com.example.data.sources.Subtitle
import com.example.media.AspectRatioMode
import com.example.media.ExoPlayerManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExoPlayerEngineTest {

    @Test
    fun testSubtitleMimeTypeInference() {
        assertEquals(MimeTypes.TEXT_VTT, ExoPlayerManager.inferSubtitleMimeType("https://stream.example.com/subs.vtt"))
        assertEquals(MimeTypes.TEXT_VTT, ExoPlayerManager.inferSubtitleMimeType("https://stream.example.com/subs.vtt?token=abc"))
        assertEquals(MimeTypes.TEXT_VTT, ExoPlayerManager.inferSubtitleMimeType("vtt"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, ExoPlayerManager.inferSubtitleMimeType("https://stream.example.com/subs.srt"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, ExoPlayerManager.inferSubtitleMimeType("srt"))
        assertEquals(MimeTypes.TEXT_SSA, ExoPlayerManager.inferSubtitleMimeType("https://stream.example.com/subs.ass"))
        assertEquals(MimeTypes.TEXT_SSA, ExoPlayerManager.inferSubtitleMimeType("ass"))
    }

    @Test
    fun testMediaItemSubtitleConfigurationCreation() {
        val mediaItem = ExoPlayerManager.createMediaItemWithSubtitles(
            videoUrl = "https://stream.example.com/video.mp4",
            subtitleUrl = "https://stream.example.com/sub_id.vtt"
        )

        assertNotNull(mediaItem.localConfiguration)
        val subtitleConfigs = mediaItem.localConfiguration!!.subtitleConfigurations
        assertEquals(1, subtitleConfigs.size)
        assertEquals("https://stream.example.com/sub_id.vtt", subtitleConfigs[0].uri.toString())
        assertEquals(MimeTypes.TEXT_VTT, subtitleConfigs[0].mimeType)
        assertEquals("id", subtitleConfigs[0].language)
        assertEquals(C.SELECTION_FLAG_DEFAULT, subtitleConfigs[0].selectionFlags)
    }

    @Test
    fun testMultiSubtitleMediaItemCreation() {
        val subtitles = listOf(
            Subtitle(url = "https://stream.example.com/id.vtt", language = "Indonesia", format = "vtt"),
            Subtitle(url = "https://stream.example.com/en.srt", language = "English", format = "srt")
        )

        val mediaItem = ExoPlayerManager.createMediaItemWithSubtitles(
            videoUrl = "https://stream.example.com/master.m3u8",
            subtitles = subtitles
        )

        val configs = mediaItem.localConfiguration!!.subtitleConfigurations
        assertEquals(2, configs.size)

        assertEquals("https://stream.example.com/id.vtt", configs[0].uri.toString())
        assertEquals(MimeTypes.TEXT_VTT, configs[0].mimeType)
        assertEquals("Indonesia", configs[0].language)
        assertEquals(C.SELECTION_FLAG_DEFAULT, configs[0].selectionFlags)

        assertEquals("https://stream.example.com/en.srt", configs[1].uri.toString())
        assertEquals(MimeTypes.APPLICATION_SUBRIP, configs[1].mimeType)
        assertEquals("English", configs[1].language)
    }

    @Test
    fun testAspectRatioModes() {
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioMode.FIT.resizeMode)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioMode.ZOOM.resizeMode)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FILL, AspectRatioMode.FILL.resizeMode)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, AspectRatioMode.FIXED_16_9.resizeMode)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT, AspectRatioMode.FIXED_4_3.resizeMode)
    }

    @Test
    fun testNextEpisodeCalculationAscending() {
        val ep1 = Chapter(id = "1", title = "Episode 1", url = "ep-1")
        val ep2 = Chapter(id = "2", title = "Episode 2", url = "ep-2")
        val ep3 = Chapter(id = "3", title = "Episode 3", url = "ep-3")
        val episodes = listOf(ep1, ep2, ep3)

        // Given current is ep1, next is ep2
        val currentIndex = episodes.indexOfFirst { it.url == "ep-1" }
        val nextEp = if (currentIndex != -1 && currentIndex + 1 < episodes.size) episodes[currentIndex + 1] else null
        assertEquals(ep2, nextEp)

        // Given current is ep3 (last), next is null
        val lastIndex = episodes.indexOfFirst { it.url == "ep-3" }
        val noNext = if (lastIndex != -1 && lastIndex + 1 < episodes.size) episodes[lastIndex + 1] else null
        assertNull(noNext)
    }
}
