package com.latent.camera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.latent.camera.R
import com.latent.camera.ui.theme.LatentInk

/**
 * UI icons, drawn from the reicon outline set. One stroke weight, open forms.
 * Tinted by state — an inactive control is quieter, never a different colour.
 */
@Composable
fun TimerIcon(
    seconds: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val tint = if (active) LatentInk.Full else LatentInk.Medium
    ReiconIcon(R.drawable.ic_timer, tint, modifier, size)
}

@Composable
fun SoundIcon(
    silent: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val tint = if (silent) LatentInk.Soft else LatentInk.Medium
    Box(modifier.size(size)) {
        ReiconIcon(R.drawable.ic_sound, tint, Modifier, size)
        if (silent) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = Stroke(width = this.size.minDimension * 0.09f, cap = StrokeCap.Round)
                drawLine(
                    color = LatentInk.Full,
                    start = Offset(this.size.width * 0.22f, this.size.height * 0.78f),
                    end = Offset(this.size.width * 0.78f, this.size.height * 0.22f),
                    strokeWidth = stroke.width,
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
    val tint = if (manual) LatentInk.Full else LatentInk.Medium
    ReiconIcon(R.drawable.ic_mode, tint, modifier, size)
}

/** A frame grid — the aids panel, not the grid setting. */
@Composable
fun AidsIcon(active: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val tint = if (active) LatentInk.Full else LatentInk.Medium
    ReiconIcon(R.drawable.ic_aids, tint, modifier, size)
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

@Composable
private fun ReiconIcon(
    drawable: Int,
    tint: Color,
    modifier: Modifier,
    size: Dp,
) {
    Icon(
        painter = painterResource(drawable),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}
