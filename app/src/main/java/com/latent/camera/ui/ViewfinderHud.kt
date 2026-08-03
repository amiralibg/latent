package com.latent.camera.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.latent.camera.ui.theme.LatentInk

/**
 * The exposure ladder, shown only while a vertical drag is happening.
 *
 * A permanent rail would be one more thing sitting on the frame; the gesture is the
 * control, and this is its readout. It appears where the eye already is — beside the
 * frame, not under it — and leaves as soon as the finger does.
 */
@Composable
fun ExposureLadder(
    exposureIndex: Int,
    range: IntRange,
    stepEv: Float,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 90 else 320),
        label = "exposureLadderAlpha",
    )
    if (alpha <= 0.01f) return

    val span = (range.last - range.first).coerceAtLeast(1)
    val position = (exposureIndex - range.first).toFloat() / span

    Row(
        modifier = modifier
            .fillMaxHeight()
            .alpha(alpha)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = formatEv(exposureIndex * stepEv),
            style = MaterialTheme.typography.bodyMedium,
            color = LatentInk.Full,
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
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

            // The pointer, riding the current value.
            val y = size.height * (1f - position)
            drawCircle(
                color = LatentInk.Full,
                radius = 2.5.dp.toPx(),
                center = Offset(size.width * 0.5f, y),
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
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 80 else 400),
        label = "recipeFlashAlpha",
    )
    if (alpha <= 0.01f) return

    Box(modifier = modifier.alpha(alpha)) {
        Text(
            text = name.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = LatentInk.Full,
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(Color.Black.copy(alpha = 0.55f))
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
            .clip(RoundedCornerShape(3.dp))
            .background(LatentInk.Lock.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(LatentInk.Lock),
        )
        Text(
            text = "AF/AE LOCK",
            style = MaterialTheme.typography.labelSmall,
            color = LatentInk.Lock,
        )
    }
}
