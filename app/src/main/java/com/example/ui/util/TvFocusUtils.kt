package com.example.ui.util

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * TV Focus Color Constant: High-visibility amber/gold highlight for Android TV remote D-Pad navigation.
 */
val TvFocusAccentColor = Color(0xFFFFD54F)

/**
 * Custom Modifier for Android TV D-Pad Remote Focus & Ergonomics.
 * Provides:
 * 1. 1.05x smooth scale-up visual feedback when focused via D-Pad
 * 2. High-contrast border highlight ring around focused cards
 * 3. Elevated zIndex (2.0f) so the scaled card sits above neighboring items without clipping
 * 4. D-Pad Center (KEYCODE_DPAD_CENTER) & Enter keyboard triggering
 * 5. Optional custom FocusRequester to maintain focus position across rows
 */
fun Modifier.tvDpadFocusable(
    focusRequester: FocusRequester? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 3.dp,
    scaleFactor: Float = 1.05f,
    shape: Shape = RoundedCornerShape(10.dp),
    onClick: (() -> Unit)? = null
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) scaleFactor else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "tvDpadScale"
    )
    val effectiveBorderColor = borderColor ?: TvFocusAccentColor

    val baseModifier = if (focusRequester != null) {
        this.focusRequester(focusRequester)
    } else {
        this
    }

    baseModifier
        .onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        }
        .then(
            if (onClick != null) {
                Modifier.onKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP &&
                        (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                         keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                         keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                    ) {
                        onClick()
                        true
                    } else {
                        false
                    }
                }
            } else Modifier
        )
        .focusable()
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }
        .zIndex(if (isFocused) 3f else 0f)
        .border(
            width = if (isFocused) borderWidth else 0.dp,
            color = if (isFocused) effectiveBorderColor else Color.Transparent,
            shape = shape
        )
}
