package com.latent.camera.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val SheetShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

/** Past this much of its own height, letting go dismisses rather than springs back. */
private const val DismissFraction = 0.32f

/** Or past this flick speed, in pixels per second, however far it has actually moved. */
private const val DismissVelocity = 900f

/**
 * A panel that rises from the bottom edge.
 *
 * It is not a dialog and not a `ModalBottomSheet`: it is a sibling layer in the same
 * composition, which is what lets the viewfinder keep rendering behind it — the whole
 * reason the recipe editor exists is to watch a slider change the live picture, and a
 * sheet in its own window would put a second surface between you and that.
 *
 * The handle is the only draggable part. Dragging anywhere would mean every scroll
 * near the top of a long panel is a coin flip between moving the content and throwing
 * the sheet away.
 */
@Composable
fun LatentSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    /** 0 is fully open, 1 is fully off the bottom edge. */
    val progress = remember { Animatable(1f) }
    var panelHeight by remember { mutableIntStateOf(0) }

    // Nothing animates until the panel has been measured, because the travel is its
    // own height and until then there is nowhere to travel from.
    LaunchedEffect(panelHeight) {
        if (panelHeight > 0 && progress.value == 1f) progress.animateTo(0f, Motion.panel())
    }

    val close: () -> Unit = {
        scope.launch {
            progress.animateTo(1f, Motion.panel())
            onDismiss()
        }
    }

    BackHandler(onBack = close)

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (1f - progress.value).coerceIn(0f, 1f) }
                .background(LatentInk.Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = close,
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .statusBarsPadding()
                .onSizeChanged { panelHeight = it.height }
                .graphicsLayer {
                    translationY = progress.value * panelHeight
                    // One frame passes between layout and the first animation tick.
                    // Without this the panel is briefly drawn sitting open.
                    alpha = if (panelHeight == 0) 0f else 1f
                }
                .clip(SheetShape)
                .background(LatentInk.Panel)
                // A hairline along the top edge. The panel is near-black and it rises
                // over a viewfinder that is often also near-black, so without this the
                // sheet has no edge at all in low light — it reads as the picture
                // going dark rather than as a panel arriving.
                .border(1.dp, LatentInk.Faint, SheetShape)
                // Swallows taps that miss a control, so they dismiss nothing.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            if (panelHeight <= 0) return@rememberDraggableState
                            scope.launch {
                                progress.snapTo(
                                    (progress.value + delta / panelHeight).coerceIn(0f, 1f),
                                )
                            }
                        },
                        onDragStopped = { velocity ->
                            if (progress.value > DismissFraction || velocity > DismissVelocity) {
                                Feedback.release(haptics)
                                progress.animateTo(1f, Motion.panel())
                                onDismiss()
                            } else {
                                progress.animateTo(0f, Motion.panel())
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(LatentInk.Soft),
                )
            }

            content()

            Box(
                Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.Transparent),
            )
        }
    }
}
