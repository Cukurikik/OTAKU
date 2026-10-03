package com.example.ui.adaptive

import android.app.Activity
import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.sin

/**
 * Posture state perangkat lipat (Foldable / Dual-Screen).
 */
data class FoldablePosture(
    val isHalfFolded: Boolean = false,
    val isTabletop: Boolean = false,
    val isBookPosture: Boolean = false,
    val isSeparating: Boolean = false,
    val hingeAngleDegrees: Float = 180f,
    val hingeBounds: Rect? = null,
    val occlusionType: FoldingFeature.OcclusionType? = null
) {
    /**
     * Menghitung tinggi relatif viewport atas saat tabletop posture aktif:
     * Viewport_top = H_display * sin(theta_hinge / 2), dimana theta_hinge approx 90 deg
     */
    fun calculateTopViewportRatio(): Float {
        if (!isTabletop) return 1.0f
        val rad = Math.toRadians((hingeAngleDegrees / 2.0).coerceIn(30.0, 90.0))
        return sin(rad).toFloat()
    }
}

/**
 * FoldablePostureStateTracker:
 * Modul pendeteksi sudut engsel & postur perangkat lipat berbasis Jetpack WindowManager.
 */
object FoldablePostureStateTracker {

    /**
     * Mem-parsing WindowLayoutInfo dari WindowManager menjadi FoldablePosture domain.
     */
    fun parseLayoutInfo(layoutInfo: WindowLayoutInfo?): FoldablePosture {
        if (layoutInfo == null) return FoldablePosture()

        val foldingFeature = layoutInfo.displayFeatures
            .filterIsInstance<FoldingFeature>()
            .firstOrNull() ?: return FoldablePosture()

        val isHalfOpened = foldingFeature.state == FoldingFeature.State.HALF_OPENED
        val isHorizontal = foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL
        val isVertical = foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL
        val isSeparating = foldingFeature.isSeparating

        val isTabletop = isHalfOpened && isHorizontal
        val isBook = isHalfOpened && isVertical

        val estimatedAngle = if (isHalfOpened) 90f else 180f

        return FoldablePosture(
            isHalfFolded = isHalfOpened,
            isTabletop = isTabletop,
            isBookPosture = isBook,
            isSeparating = isSeparating,
            hingeAngleDegrees = estimatedAngle,
            hingeBounds = foldingFeature.bounds,
            occlusionType = foldingFeature.occlusionType
        )
    }
}

/**
 * Composable helper untuk memantau status engsel perangkat lipat secara lifecycle-aware.
 */
@Composable
fun rememberFoldablePosture(): FoldablePosture {
    val context = LocalContext.current
    val activity = context as? Activity
    var posture by remember { mutableStateOf(FoldablePosture()) }

    if (activity != null) {
        LaunchedEffect(activity) {
            try {
                WindowInfoTracker.getOrCreate(activity)
                    .windowLayoutInfo(activity)
                    .collectLatest { info ->
                        posture = FoldablePostureStateTracker.parseLayoutInfo(info)
                    }
            } catch (_: Exception) {
                // Fallback untuk perangkat non-foldable atau lingkungan test
                posture = FoldablePosture()
            }
        }
    }

    return posture
}
