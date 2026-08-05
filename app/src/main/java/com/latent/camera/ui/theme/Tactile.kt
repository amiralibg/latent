package com.latent.camera.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * What a control does when it is touched.
 *
 * Every interactive thing in this app goes through here, which is the point: a camera
 * is operated by feel, often with the phone away from the eye, and a control that
 * gives nothing back on contact is a control you have to look at to trust. The tick
 * fires on press rather than on click, because the finger is asking "did you get
 * that?" at touch-down, not at release — and because a press that ends in a slide-off
 * still deserves an answer about where the boundary was.
 *
 * Place it **first** in the chain. The scale is a `graphicsLayer`, so anything drawn
 * before it — a background, a border, a clip — sits outside the transform and will not
 * move with the rest of the control.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tactile(
    enabled: Boolean = true,
    pressScale: Float = 0.93f,
    pressAlpha: Float = 0.7f,
    haptic: HapticFeedbackType? = HapticFeedbackType.VirtualKey,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val active = pressed && enabled

    // Down is instant and up is springy: the control has to arrive under the finger
    // before the finger has finished landing, but bouncing back is where the physical
    // quality comes from.
    val scale by animateFloatAsState(
        targetValue = if (active) pressScale else 1f,
        animationSpec = if (active) Motion.press() else Motion.release(),
        label = "tactileScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (active) pressAlpha else 1f,
        animationSpec = if (active) Motion.press() else Motion.release(),
        label = "tactileAlpha",
    )

    LaunchedEffect(active) {
        if (active && haptic != null) haptics.performHapticFeedback(haptic)
    }

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                    onClick = onClick,
                )
            } else {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                )
            },
        )
}

/**
 * The named gestures, so a call site says what it means rather than picking a constant.
 * Keeping the mapping in one place is what stops "confirm" feeling different in the
 * settings sheet than it does in the viewfinder.
 */
object Feedback {

    /** A setting moved to its next value. */
    fun toggle(haptics: HapticFeedback, on: Boolean) {
        haptics.performHapticFeedback(
            if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
        )
    }

    /** A dial or strip crossed a detent. The lightest thing available. */
    fun tick(haptics: HapticFeedback) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    /** A dial hit the end of its travel. */
    fun limit(haptics: HapticFeedback) {
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }

    /** Something happened that the user cannot undo, or a panel committed. */
    fun confirm(haptics: HapticFeedback) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    /** The shutter. Deliberately the heaviest thing the app does. */
    fun shutter(haptics: HapticFeedback) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /** A gesture let go of the frame. */
    fun release(haptics: HapticFeedback) {
        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
    }
}
