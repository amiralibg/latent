package com.latent.camera.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

/**
 * What a drag across the frame is for. The axis is decided once, when the finger
 * first passes touch slop, and then held for the rest of the gesture — a drag that
 * re-classified itself halfway would swap the recipe while you were setting exposure.
 */
enum class ViewfinderDrag {
    /** Vertical. Up is brighter, the way every exposure dial is engraved. */
    Exposure,

    /** Horizontal. The same movement as the recipe strip below the frame. */
    Recipe,
}

/**
 * Every gesture the viewfinder answers to, resolved by one detector.
 *
 * This is deliberately not four stacked `pointerInput` modifiers. Tap, long press,
 * pan and pinch all begin with the same finger going down in the same place, so
 * composing separate detectors means each one guessing what the others are doing —
 * the usual result being a pinch that also nudges exposure, or a tap-to-focus that
 * only lands if you were perfectly still. Deciding once, here, is the difference
 * between a viewfinder that feels like glass and one that feels like a web page.
 *
 * The decision, in order of how the gesture reveals itself:
 * - a second finger arrives → pinch zoom
 * - the finger passes touch slop → drag, on whichever axis it moved further along
 * - the finger lifts first → tap
 * - none of the above before the long-press timeout → hold, which locks
 */
suspend fun PointerInputScope.detectViewfinderGestures(
    onTap: (Offset) -> Unit,
    onHold: (Offset) -> Unit,
    onZoom: (Float) -> Unit,
    onDragStart: (ViewfinderDrag) -> Unit,
    /** Signed pixels along the axis, positive meaning brighter or later in the strip. */
    onDrag: (ViewfinderDrag, Float) -> Unit,
    onDragEnd: (ViewfinderDrag) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val origin = down.position
    val slop = viewConfiguration.touchSlop
    var pan = Offset.Zero

    val decision = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) return@withTimeoutOrNull Decision.Zoom

            val change = event.changes.firstOrNull { it.id == down.id }
                ?: return@withTimeoutOrNull Decision.Tap
            if (!change.pressed) return@withTimeoutOrNull Decision.Tap

            pan += change.positionChange()
            if (pan.getDistance() > slop) {
                return@withTimeoutOrNull Decision.Drag(
                    if (abs(pan.y) > abs(pan.x)) {
                        ViewfinderDrag.Exposure
                    } else {
                        ViewfinderDrag.Recipe
                    },
                )
            }
        }
        // Unreachable: the loop only exits by returning.
        @Suppress("UNREACHABLE_CODE")
        Decision.Tap
    }

    when (decision) {
        // The timeout won, so the finger is still down and has not moved.
        null -> {
            onHold(origin)
            awaitRelease(down.id)
        }

        Decision.Tap -> onTap(origin)

        Decision.Zoom -> {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.none { it.pressed }) break
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    onZoom(zoom)
                    event.changes.forEach { it.consume() }
                }
            }
        }

        is Decision.Drag -> {
            val axis = decision.axis
            onDragStart(axis)
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val moved = change.positionChange()
                    // Screen coordinates run down and right; both dials run the other
                    // way, so the sign flips once here rather than at every caller.
                    val along = when (axis) {
                        ViewfinderDrag.Exposure -> -moved.y
                        ViewfinderDrag.Recipe -> -moved.x
                    }
                    if (along != 0f) onDrag(axis, along)
                    change.consume()
                }
            } finally {
                onDragEnd(axis)
            }
        }
    }
}

private sealed interface Decision {
    data object Tap : Decision
    data object Zoom : Decision
    data class Drag(val axis: ViewfinderDrag) : Decision
}

/**
 * Swallow the rest of a gesture that has already been acted on. Without this a hold
 * would be followed by whatever the finger did next on its way up.
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitRelease(
    id: androidx.compose.ui.input.pointer.PointerId,
) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return
        change.consume()
        if (!change.pressed) return
    }
}
