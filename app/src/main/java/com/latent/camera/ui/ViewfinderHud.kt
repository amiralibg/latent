package com.latent.camera.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.latent.camera.ui.theme.LatentInk
import com.latent.camera.ui.theme.LatentText
import com.latent.camera.ui.theme.LatentType
import com.latent.camera.ui.theme.Motion

/**
 * The exposure ladder, shown only while a vertical drag is happening.
 *
 * A permanent rail would be one more thing sitting on the frame; the gesture is the
 * control, and this is its readout. It appears where the eye already is — beside the
 * frame, not under it — and leaves as soon as the finger does. It also slides in from
 * the edge it lives on, so the first thing you see is which way the rail runs.
 */
@Composable
fun ExposureLadder(
    exposureIndex: Int,
    range: IntRange,
    stepEv: Float,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val reveal by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) Motion.enter() else Motion.leave(),
        label = "exposureLadderReveal",
    )
    if (reveal <= 0.01f) return

    val span = (range.last - range.first).coerceAtLeast(1)
    val position = (exposureIndex - range.first).toFloat() / span

    // The pointer chases the value rather than jumping to it, which is what turns a
    // series of discrete EV steps into something that reads as one continuous dial.
    val pointer by animateFloatAsState(
        targetValue = position,
        animationSpec = Motion.state(),
        label = "exposurePointer",
    )

    Row(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                alpha = reveal
                translationX = (1f - reveal) * 24.dp.toPx()
            }
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LatentText(
            text = formatEv(exposureIndex * stepEv),
            style = LatentType.Readout,
            color = LatentInk.Full,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )

        Canvas(
            modifier = Modifier
                .width(18.dp)
                .fillMaxHeight(0.62f),
        ) {
            val ticks = span + 1
            val stroke = 1.dp.toPx()
            // Whole stops get a full tick; the device's sub-stop increments get a
            // short one, so the ladder reads as stops rather than as raw indices.
            val perStop = if (stepEv > 0f) (1f / stepEv).toInt().coerceAtLeast(1) else 1

            for (i in 0 until ticks) {
                val index = range.first + i
                val y = size.height * (1f - i.toFloat() / span)
                val whole = index % perStop == 0
                val length = if (whole) size.width else size.width * 0.45f
                drawLine(
                    color = if (index == exposureIndex) LatentInk.Full else LatentInk.Faint,
                    start = Offset(size.width - length, y),
                    end = Offset(size.width, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }

            drawCircle(
                color = LatentInk.Full,
                radius = 2.5.dp.toPx(),
                center = Offset(size.width * 0.5f, size.height * (1f - pointer)),
            )
        }
    }
}

/**
 * The recipe name, thrown up large on the frame while a horizontal swipe moves
 * through the strip. The strip itself is below the frame and out of the eyeline
 * mid-gesture, so the confirmation has to be where the picture is.
 */
@Composable
fun RecipeFlash(name: String, visible: Boolean, modifier: Modifier = Modifier) {
    val reveal by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) Motion.enter() else Motion.leave(),
        label = "recipeFlashReveal",
    )
    if (reveal <= 0.01f) return

    Box(
        modifier = modifier.graphicsLayer {
            alpha = reveal
            // A hair of scale on the way in. Enough to catch the eye mid-swipe,
            // not enough to read as a pop-up.
            val s = 0.94f + reveal * 0.06f
            scaleX = s
            scaleY = s
        },
    ) {
        LatentText(
            text = name.uppercase(),
            style = LatentType.Label,
            color = LatentInk.Full,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/**
 * A one-line message thrown on the frame and taken away again — the grain stock you
 * just cycled to, the aid you just turned on. Same slot and same motion as the recipe
 * flash, because they answer the same question: *what did that button just do?*
 */
@Composable
fun ActionFlash(message: String?, modifier: Modifier = Modifier) {
    val reveal by animateFloatAsState(
        targetValue = if (message != null) 1f else 0f,
        animationSpec = if (message != null) Motion.enter() else Motion.leave(),
        label = "actionFlashReveal",
    )
    if (reveal <= 0.01f) return

    Box(
        modifier = modifier.graphicsLayer {
            alpha = reveal
            translationY = (1f - reveal) * 10.dp.toPx()
        },
    ) {
        LatentText(
            text = message.orEmpty(),
            style = LatentType.LabelMedium,
            color = LatentInk.Full,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/**
 * The AF/AE lock badge. A lock you cannot see is a lock you will forget you set and
 * then blame the camera for, so it stays on the frame until it is cleared, and the
 * badge is the thing that clears it.
 */
@Composable
fun LockBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(LatentInk.Lock.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(LatentInk.Lock),
        )
        LatentText(
            text = "AF/AE LOCK",
            style = LatentType.LabelSmall,
            color = LatentInk.Lock,
        )
    }
}
