package com.example.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Kategori ukuran jendela layar sesuai spesifikasi Android Material 3 Adaptive.
 */
enum class AppWindowWidthSizeClass {
    COMPACT,   // W < 600dp (Smartphone portrait)
    MEDIUM,    // 600dp <= W < 840dp (Foldable unfolded, Small Tablet)
    EXPANDED   // W >= 840dp (Large Tablet, Desktop, ChromeOS)
}

enum class AppWindowHeightSizeClass {
    COMPACT,   // H < 480dp (Smartphone landscape)
    MEDIUM,    // 480dp <= H < 900dp
    EXPANDED   // H >= 900dp (Tall tablets/portrait monitors)
}

/**
 * Data class penampung klasifikasi ukuran layar dan resolusi dimensi aktif.
 */
data class AppWindowSizeClass(
    val widthSizeClass: AppWindowWidthSizeClass,
    val heightSizeClass: AppWindowHeightSizeClass,
    val widthDp: Dp,
    val heightDp: Dp
) {
    val isCompact: Boolean get() = widthSizeClass == AppWindowWidthSizeClass.COMPACT
    val isMedium: Boolean get() = widthSizeClass == AppWindowWidthSizeClass.MEDIUM
    val isExpanded: Boolean get() = widthSizeClass == AppWindowWidthSizeClass.EXPANDED
    val isLargeScreen: Boolean get() = !isCompact
}

/**
 * WindowSizeClassResolver:
 * Menghitung WindowSizeClass dari konfigurasi tampilan layar saat ini
 * sesuai formulasi:
 * - Compact: W < 600dp
 * - Medium: 600dp <= W < 840dp
 * - Expanded: W >= 840dp
 */
object WindowSizeClassResolver {

    fun resolveFromDp(widthDp: Dp, heightDp: Dp): AppWindowSizeClass {
        val widthClass = when {
            widthDp < 600.dp -> AppWindowWidthSizeClass.COMPACT
            widthDp < 840.dp -> AppWindowWidthSizeClass.MEDIUM
            else -> AppWindowWidthSizeClass.EXPANDED
        }

        val heightClass = when {
            heightDp < 480.dp -> AppWindowHeightSizeClass.COMPACT
            heightDp < 900.dp -> AppWindowHeightSizeClass.MEDIUM
            else -> AppWindowHeightSizeClass.EXPANDED
        }

        return AppWindowSizeClass(
            widthSizeClass = widthClass,
            heightSizeClass = heightClass,
            widthDp = widthDp,
            heightDp = heightDp
        )
    }
}

/**
 * Composable helper untuk membaca WindowSizeClass secara reaktif terhadap rotasi & resize multi-window.
 */
@Composable
fun rememberAppWindowSizeClass(): AppWindowSizeClass {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    return remember(screenWidth, screenHeight) {
        WindowSizeClassResolver.resolveFromDp(screenWidth, screenHeight)
    }
}
