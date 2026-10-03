package com.example.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * TabletopModeController:
 * Mengelola transisi layout dan pembagian area kerja saat perangkat lipat
 * berada dalam posisi Tabletop Mode (ditekuk 90 derajat secara horizontal).
 *
 * Layar Atas: Clean Media Viewport (Video Player / Comic Reader) tanpa overlay kontrol yang mengganggu.
 * Layar Bawah: Remote Control Board, Track Scrubbing, Quick Episode/Chapter Switcher, and Live Comments.
 */
@Composable
fun TabletopSplitLayout(
    posture: FoldablePosture,
    topViewportContent: @Composable () -> Unit,
    bottomControlBoardContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val topRatio = posture.calculateTopViewportRatio().coerceIn(0.4f, 0.6f)

    Column(modifier = modifier.fillMaxSize()) {
        // Layar Atas: Clean Video / Media Player
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(topRatio)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            topViewportContent()
        }

        // Hinge Separation Indicator Bar
        HorizontalDivider(
            thickness = 2.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )

        // Layar Bawah: Interactive Remote Control Board
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f - topRatio),
            color = MaterialTheme.colorScheme.surface
        ) {
            bottomControlBoardContent()
        }
    }
}
