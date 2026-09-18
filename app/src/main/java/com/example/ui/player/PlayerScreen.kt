package com.example.ui.player

import androidx.compose.runtime.Composable
import com.example.ui.screens.PlayerScreen as CorePlayerScreen
import com.example.ui.viewmodels.PlayerViewModel

/**
 * PlayerScreen wrapper in ui.player package for seamless architecture alignment.
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    isTvMode: Boolean = false
) {
    CorePlayerScreen(
        viewModel = viewModel,
        onBack = onBack,
        isTvMode = isTvMode
    )
}
