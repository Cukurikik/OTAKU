package com.example.media.shader

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.SettingsManager

@OptIn(UnstableApi::class)
object AnimeShaderManager {

    fun applyShaderEffect(
        player: Player?,
        mode: AnimeShaderMode,
        intensity: Float = 1.0f
    ) {
        val exoPlayer = player as? ExoPlayer ?: return
        if (mode == AnimeShaderMode.OFF) {
            exoPlayer.setVideoEffects(emptyList())
        } else {
            val effect = AnimeGlShaderEffect(mode, intensity)
            exoPlayer.setVideoEffects(listOf(effect))
        }
    }

    fun applySavedShaderEffect(context: Context, player: Player?) {
        val settingsManager = SettingsManager(context)
        val modeStr = settingsManager.animeShaderMode
        val mode = try {
            AnimeShaderMode.valueOf(modeStr)
        } catch (e: Exception) {
            AnimeShaderMode.ANIME_HDR
        }
        val intensity = settingsManager.animeShaderIntensity
        applyShaderEffect(player, mode, intensity)
    }

    fun saveAndApplyShaderEffect(
        context: Context,
        player: Player?,
        mode: AnimeShaderMode,
        intensity: Float
    ) {
        val settingsManager = SettingsManager(context)
        settingsManager.animeShaderMode = mode.name
        settingsManager.animeShaderIntensity = intensity
        applyShaderEffect(player, mode, intensity)
    }
}
