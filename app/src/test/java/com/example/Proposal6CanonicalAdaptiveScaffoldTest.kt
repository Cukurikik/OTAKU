package com.example

import androidx.compose.ui.unit.dp
import com.example.ui.adaptive.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class Proposal6CanonicalAdaptiveScaffoldTest {

    @Test
    fun testWindowSizeClassResolverThresholds() {
        // 1. Compact: W < 600dp
        val compactWindow = WindowSizeClassResolver.resolveFromDp(widthDp = 390.dp, heightDp = 844.dp)
        assertEquals(AppWindowWidthSizeClass.COMPACT, compactWindow.widthSizeClass)
        assertEquals(AppWindowHeightSizeClass.MEDIUM, compactWindow.heightSizeClass)
        assertTrue(compactWindow.isCompact)
        assertFalse(compactWindow.isLargeScreen)

        // 2. Medium: 600dp <= W < 840dp
        val mediumWindow = WindowSizeClassResolver.resolveFromDp(widthDp = 720.dp, heightDp = 900.dp)
        assertEquals(AppWindowWidthSizeClass.MEDIUM, mediumWindow.widthSizeClass)
        assertEquals(AppWindowHeightSizeClass.EXPANDED, mediumWindow.heightSizeClass)
        assertTrue(mediumWindow.isMedium)
        assertTrue(mediumWindow.isLargeScreen)

        // 3. Expanded: W >= 840dp
        val expandedWindow = WindowSizeClassResolver.resolveFromDp(widthDp = 1080.dp, heightDp = 800.dp)
        assertEquals(AppWindowWidthSizeClass.EXPANDED, expandedWindow.widthSizeClass)
        assertTrue(expandedWindow.isExpanded)
        assertTrue(expandedWindow.isLargeScreen)
    }

    @Test
    fun testCanonicalAdaptiveResolverLayoutModes() {
        val compactClass = WindowSizeClassResolver.resolveFromDp(400.dp, 800.dp)
        val mediumClass = WindowSizeClassResolver.resolveFromDp(700.dp, 900.dp)
        val expandedClass = WindowSizeClassResolver.resolveFromDp(1000.dp, 800.dp)

        val normalPosture = FoldablePosture(isHalfFolded = false, isTabletop = false)
        val tabletopPosture = FoldablePosture(isHalfFolded = true, isTabletop = true, hingeAngleDegrees = 90f)
        val bookPosture = FoldablePosture(isHalfFolded = true, isTabletop = false, isBookPosture = true)

        // Smartphone Portrait -> Single Column
        val mode1 = CanonicalAdaptiveResolver.resolveLayoutMode(compactClass, normalPosture)
        assertEquals(AdaptiveLayoutMode.SINGLE_COLUMN, mode1)

        // Tablet / PC Expanded -> Side By Side
        val mode2 = CanonicalAdaptiveResolver.resolveLayoutMode(expandedClass, normalPosture)
        assertEquals(AdaptiveLayoutMode.SIDE_BY_SIDE, mode2)

        // Small Tablet / Foldable Unfolded -> Side By Side
        val mode3 = CanonicalAdaptiveResolver.resolveLayoutMode(mediumClass, normalPosture)
        assertEquals(AdaptiveLayoutMode.SIDE_BY_SIDE, mode3)

        // Foldable Ditekuk 90 derajat (Tabletop) -> Tabletop Split Layout (Prioritas Utama)
        val mode4 = CanonicalAdaptiveResolver.resolveLayoutMode(compactClass, tabletopPosture)
        assertEquals(AdaptiveLayoutMode.TABLETOP_SPLIT, mode4)

        val mode5 = CanonicalAdaptiveResolver.resolveLayoutMode(expandedClass, tabletopPosture)
        assertEquals(AdaptiveLayoutMode.TABLETOP_SPLIT, mode5)
    }

    @Test
    fun testFoldablePostureViewportRatioCalculation() {
        val flatPosture = FoldablePosture(isHalfFolded = false, isTabletop = false)
        assertEquals(1.0f, flatPosture.calculateTopViewportRatio(), 0.001f)

        // Formulasi: Viewport_top = H_display * sin(theta_hinge / 2), theta = 90 deg -> sin(45 deg) approx 0.7071
        val tabletopPosture = FoldablePosture(
            isHalfFolded = true,
            isTabletop = true,
            hingeAngleDegrees = 90f
        )
        val expectedRatio = kotlin.math.sin(Math.toRadians(45.0)).toFloat()
        assertEquals(expectedRatio, tabletopPosture.calculateTopViewportRatio(), 0.001f)
    }

    @Test
    fun testFoldablePostureStateTrackerNullFallback() {
        val parsed = FoldablePostureStateTracker.parseLayoutInfo(null)
        assertFalse(parsed.isHalfFolded)
        assertFalse(parsed.isTabletop)
        assertFalse(parsed.isBookPosture)
        assertEquals(180f, parsed.hingeAngleDegrees, 0.001f)
        assertNull(parsed.hingeBounds)
    }
}
