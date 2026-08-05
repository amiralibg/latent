package com.latent.camera.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.latent.camera.R
import com.latent.camera.ui.theme.LatentInk
import com.latent.camera.ui.theme.Motion

/**
 * UI icons, drawn from the reicon outline set. One stroke weight, open forms.
 *
 * Tint is animated everywhere rather than switched, because these icons carry state —
 * whether the timer is armed, whether the shutter is silent — and a tint that snaps
 * reads as a redraw while a tint that moves reads as an answer to the tap that caused
 * it. An inactive control is quieter, never a different colour.
 */
@Composable
fun TimerIcon(
    active: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    ReiconIcon(R.drawable.ic_timer, stateTint(active), modifier, size)
}

@Composable
fun SoundIcon(
    silent: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val strike by animateFloatAsState(
        targetValue = if (silent) 1f else 0f,
        animationSpec = Motion.settle(),
        label = "soundStrike",
    )
    Box(modifier.size(size)) {
        ReiconIcon(
            R.drawable.ic_sound,
            stateTint(silent, activeColor = LatentInk.Soft, idleColor = LatentInk.Medium),
            Modifier,
            size,
        )
        // The bar is drawn on rather than swapped for a second asset, so it can be
        // struck through: muting the shutter should look like an action, not a state.
        if (strike > 0.01f) {
            Canvas(Modifier.matchParentSize()) {
                val start = Offset(this.size.width * 0.2f, this.size.height * 0.8f)
                val end = Offset(this.size.width * 0.8f, this.size.height * 0.2f)
                drawLine(
                    color = LatentInk.Full,
                    start = start,
                    end = Offset(
                        start.x + (end.x - start.x) * strike,
                        start.y + (end.y - start.y) * strike,
                    ),
                    strokeWidth = this.size.minDimension * 0.09f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
fun ModeIcon(
    manual: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    ReiconIcon(R.drawable.ic_mode, stateTint(manual), modifier, size)
}

/**
 * Turn the camera round.
 *
 * The glyph rotates half a turn every time the facing changes, so the icon performs
 * the thing it does. A flip is one of the few controls whose result is off screen for
 * a moment while the camera reopens, and the rotation is what covers that gap — the
 * answer to the tap arrives before the new preview does.
 */
@Composable
fun FlipIcon(front: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val turn by animateFloatAsState(
        targetValue = if (front) 180f else 0f,
        animationSpec = Motion.settle(),
        label = "flipTurn",
    )
    ReiconIcon(
        R.drawable.ic_flip,
        stateTint(front),
        modifier.graphicsLayer { rotationZ = turn },
        size,
    )
}

/** A frame grid — the aids panel, not the grid setting. */
@Composable
fun AidsIcon(active: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    ReiconIcon(R.drawable.ic_aids, stateTint(active), modifier, size)
}

/** Three sliders. Settings, drawn as what settings actually are here. */
@Composable
fun SettingsIcon(modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = LatentInk.Medium) {
    ReiconIcon(R.drawable.ic_settings, tint, modifier, size)
}

/** A stack of frames. The contact sheet. */
@Composable
fun GalleryIcon(modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = LatentInk.Medium) {
    ReiconIcon(R.drawable.ic_gallery, tint, modifier, size)
}

@Composable
fun BackIcon(modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = LatentInk.Strong) {
    ReiconIcon(R.drawable.ic_back, tint, modifier, size)
}

@Composable
fun ShareIcon(modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = LatentInk.Strong) {
    ReiconIcon(R.drawable.ic_share, tint, modifier, size)
}

@Composable
fun DeleteIcon(modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = LatentInk.Strong) {
    ReiconIcon(R.drawable.ic_delete, tint, modifier, size)
}

/** A frame with an arrow curling back into it: render this one again. */
@Composable
fun RegradeIcon(modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = LatentInk.Strong) {
    ReiconIcon(R.drawable.ic_regrade, tint, modifier, size)
}

@Composable
fun CheckIcon(modifier: Modifier = Modifier, size: Dp = 18.dp, tint: Color = LatentInk.Full) {
    ReiconIcon(R.drawable.ic_check, tint, modifier, size)
}

/**
 * Grain, drawn rather than borrowed: no icon set has a mark for this, and the obvious
 * substitutes (a blur drop, a noise wave) all describe something else.
 *
 * The scatter is the readout. Dots grow with the amount and spread with the size, so
 * the button in the top bar is showing you the setting rather than labelling it — at a
 * glance, coarse grain and fine grain are different icons.
 */
@Composable
fun GrainIcon(
    amount: Float,
    grainSize: Float,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val tint by animateColorAsState(
        targetValue = if (amount > 0.001f) LatentInk.Full else LatentInk.Medium,
        animationSpec = Motion.state(),
        label = "grainTint",
    )
    val strength by animateFloatAsState(
        targetValue = (amount / 0.5f).coerceIn(0f, 1f),
        animationSpec = Motion.settle(),
        label = "grainStrength",
    )
    val coarseness by animateFloatAsState(
        targetValue = ((grainSize - 1f) / 5f).coerceIn(0f, 1f),
        animationSpec = Motion.settle(),
        label = "grainCoarseness",
    )

    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        // A fixed scatter, so the icon is stable between recompositions and reads as
        // one mark rather than as animated static.
        val dots = GrainScatter
        val base = side * 0.055f
        val spread = 0.9f + coarseness * 0.55f

        dots.forEachIndexed { index, (nx, ny) ->
            val jitter = ((index * 37) % 11) / 10f
            val radius = base * (0.55f + jitter * 0.75f) * spread
            drawCircle(
                color = tint.copy(alpha = tint.alpha * (0.3f + 0.7f * strength) * (0.55f + jitter * 0.45f)),
                radius = radius,
                center = Offset(nx * side, ny * side),
            )
        }
    }
}

/** Twelve positions inside the unit square, chosen to look scattered and not to clump. */
private val GrainScatter = listOf(
    0.18f to 0.22f, 0.47f to 0.13f, 0.79f to 0.24f,
    0.29f to 0.44f, 0.62f to 0.39f, 0.88f to 0.52f,
    0.13f to 0.61f, 0.42f to 0.68f, 0.71f to 0.72f,
    0.24f to 0.86f, 0.55f to 0.9f, 0.86f to 0.83f,
)

/** Active is full-strength ink; inactive is quieter. Never a second colour. */
@Composable
private fun stateTint(
    active: Boolean,
    activeColor: Color = LatentInk.Full,
    idleColor: Color = LatentInk.Medium,
): Color {
    val tint by animateColorAsState(
        targetValue = if (active) activeColor else idleColor,
        animationSpec = Motion.state(),
        label = "iconTint",
    )
    return tint
}

/**
 * The one place a drawable becomes pixels. Material's `Icon` would do this too, but it
 * also drags in a theme lookup and a `LocalContentColor` chain that this app resolves
 * at the call site anyway.
 */
@Composable
private fun ReiconIcon(
    drawable: Int,
    tint: Color,
    modifier: Modifier,
    size: Dp,
) {
    // SrcIn carries the tint's own alpha into the result, so the six ink levels work
    // here exactly as they do on text — no second opacity to keep in step.
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}
