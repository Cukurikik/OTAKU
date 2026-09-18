package com.example.media.shader

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AnimeShaderTest {

    @Test
    fun testShaderModeEnumMetadata() {
        assertEquals(4, AnimeShaderMode.entries.size)
        
        val hdrMode = AnimeShaderMode.ANIME_HDR
        assertEquals("Anime HDR Simulation", hdrMode.displayName)
        assertNotNull(hdrMode.description)

        val sharpMode = AnimeShaderMode.SHARPENING_DEBLOCKING
        assertEquals("Sharpening & Deblocking", sharpMode.displayName)
        
        val ultraMode = AnimeShaderMode.ULTRA_HDR_SHARP
        assertEquals("Ultra Anime HDR + Sharpening", ultraMode.displayName)

        val offMode = AnimeShaderMode.OFF
        assertEquals("Nonaktif", offMode.displayName)
    }

    @Test
    fun testAnimeGlShaderEffectConstruction() {
        val effectHdr = AnimeGlShaderEffect(AnimeShaderMode.ANIME_HDR, 1.2f)
        assertEquals(AnimeShaderMode.ANIME_HDR, effectHdr.mode)
        assertEquals(1.2f, effectHdr.intensity, 0.001f)

        val effectUltra = AnimeGlShaderEffect(AnimeShaderMode.ULTRA_HDR_SHARP, 1.5f)
        assertEquals(AnimeShaderMode.ULTRA_HDR_SHARP, effectUltra.mode)
        assertEquals(1.5f, effectUltra.intensity, 0.001f)
    }

    @Test
    fun testAnimeShaderManagerPersistence() {
        val context = RuntimeEnvironment.getApplication()

        // Save ULTRA_HDR_SHARP mode and 1.8f intensity
        AnimeShaderManager.saveAndApplyShaderEffect(
            context = context,
            player = null,
            mode = AnimeShaderMode.ULTRA_HDR_SHARP,
            intensity = 1.8f
        )

        // Read back from SettingsManager via manager helper
        val settingsManager = com.example.data.SettingsManager(context)
        assertEquals("ULTRA_HDR_SHARP", settingsManager.animeShaderMode)
        assertEquals(1.8f, settingsManager.animeShaderIntensity, 0.001f)
    }
}
