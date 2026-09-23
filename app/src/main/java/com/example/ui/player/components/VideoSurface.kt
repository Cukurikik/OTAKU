package com.example.ui.player.components

import android.content.Context
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
fun applySubtitleStyleToPlayerView(
    playerView: PlayerView,
    textColor: Int,
    bgColor: Int,
    fontSizeSp: Float
) {
    playerView.subtitleView?.let { subView ->
        com.example.ui.player.AnimeTrackSelectionEngine.configureSubtitleStyle(
            subtitleView = subView,
            fontSizeSp = fontSizeSp,
            textColor = textColor,
            bgColor = bgColor
        )
    }
}

/**
 * Modular Video Surface component that encapsulates ExoPlayer's PlayerView rendering.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    player: Player,
    useController: Boolean,
    subtitleTextColor: Int,
    subtitleBgColor: Int,
    subtitleFontSizeSp: Float,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onPlayerViewCreated: (PlayerView) -> Unit = {}
) {
    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                this.resizeMode = resizeMode
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                keepScreenOn = true
                applySubtitleStyleToPlayerView(
                    playerView = this,
                    textColor = subtitleTextColor,
                    bgColor = subtitleBgColor,
                    fontSizeSp = subtitleFontSizeSp
                )
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus()
                onPlayerViewCreated(this)
            }
        },
        update = { playerView ->
            if (playerView.player != player) {
                playerView.player = player
            }
            if (playerView.resizeMode != resizeMode) {
                playerView.resizeMode = resizeMode
            }
            playerView.useController = useController
            applySubtitleStyleToPlayerView(
                playerView = playerView,
                textColor = subtitleTextColor,
                bgColor = subtitleBgColor,
                fontSizeSp = subtitleFontSizeSp
            )
        },
        onRelease = { playerView ->
            playerView.player = null
        }
    )
}
