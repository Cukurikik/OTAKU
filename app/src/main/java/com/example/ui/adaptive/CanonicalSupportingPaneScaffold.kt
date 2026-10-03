package com.example.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Mode tata letak adaptif yang diaktifkan berdasarkan form factor dan engsel.
 */
enum class AdaptiveLayoutMode {
    TABLETOP_SPLIT,
    SIDE_BY_SIDE,
    SINGLE_COLUMN
}

/**
 * Helper untuk menentukan mode tata letak adaptif.
 */
object CanonicalAdaptiveResolver {
    fun resolveLayoutMode(
        windowSizeClass: AppWindowSizeClass,
        posture: FoldablePosture
    ): AdaptiveLayoutMode {
        return when {
            posture.isTabletop || (posture.isHalfFolded && !posture.isBookPosture) -> AdaptiveLayoutMode.TABLETOP_SPLIT
            windowSizeClass.isLargeScreen -> AdaptiveLayoutMode.SIDE_BY_SIDE
            else -> AdaptiveLayoutMode.SINGLE_COLUMN
        }
    }
}

/**
 * CanonicalSupportingPaneScaffold:
 * Komponen tata letak adaptif Material 3 Canonical Layout untuk Smartphone, Tablet, Foldable & Desktop ChromeOS.
 *
 * Formulasi:
 * Layout_mode = TabletopSplit jika IsHalfFolded = true
 *             = SideBySide jika WindowClass in {Medium, Expanded}
 *             = SingleColumn jika WindowClass = Compact
 */
@Composable
fun CanonicalSupportingPaneScaffold(
    windowSizeClass: AppWindowSizeClass = rememberAppWindowSizeClass(),
    posture: FoldablePosture = rememberFoldablePosture(),
    modifier: Modifier = Modifier,
    navigationRailContent: (@Composable () -> Unit)? = null,
    indexContent: (@Composable () -> Unit)? = null,
    detailContent: @Composable () -> Unit,
    controlBoardContent: (@Composable () -> Unit)? = null,
    bottomBarContent: (@Composable () -> Unit)? = null,
    indexPaneWidth: Dp = 340.dp
) {
    val layoutMode = CanonicalAdaptiveResolver.resolveLayoutMode(windowSizeClass, posture)

    when (layoutMode) {
        AdaptiveLayoutMode.TABLETOP_SPLIT -> {
            // 1. Tabletop Mode untuk Foldable ditekuk 90 derajat
            TabletopSplitLayout(
                posture = posture,
                topViewportContent = detailContent,
                bottomControlBoardContent = {
                    controlBoardContent?.invoke() ?: indexContent?.invoke() ?: Box(Modifier.fillMaxSize())
                },
                modifier = modifier
            )
        }

        AdaptiveLayoutMode.SIDE_BY_SIDE -> {
            // 2. Canonical 3-Pane / 2-Pane untuk Tablet, PC, & ChromeOS
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Navigation Rail permanen di sisi kiri
                    if (navigationRailContent != null) {
                        navigationRailContent()
                        VerticalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }

                    // Panel Tengah: Episode Index / Chapter List
                    if (indexContent != null) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 280.dp, max = indexPaneWidth)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            indexContent()
                        }
                        VerticalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }

                    // Panel Kanan: Player / Reader / Content Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        detailContent()
                    }
                }
            }
        }

        AdaptiveLayoutMode.SINGLE_COLUMN -> {
            // 3. Single Column Standar untuk Smartphone Portrait
            Scaffold(
                modifier = modifier.fillMaxSize(),
                bottomBar = {
                    bottomBarContent?.invoke()
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    detailContent()
                }
            }
        }
    }
}
