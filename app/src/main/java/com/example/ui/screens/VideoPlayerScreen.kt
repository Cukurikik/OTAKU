package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.viewmodels.PlayerViewModel
import com.example.ui.viewmodels.VideoPlayerViewModel

/**
 * VideoPlayerScreen for continuous drama and anime playback.
 * Complies with Proposal 5 Technical Specification:
 * - Auto-Next Countdown Overlay
 * - Continuous Stream Query
 * - Full integration with PlayerViewModel and VideoPlayerViewModel.
 */
@Composable
fun VideoPlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    isTvMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    PlayerScreen(
        viewModel = viewModel,
        onBack = onBack,
        isTvMode = isTvMode
    )
}
