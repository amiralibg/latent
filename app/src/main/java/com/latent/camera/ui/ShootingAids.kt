package com.latent.camera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.latent.camera.settings.AidsSettings
import com.latent.camera.settings.AidsState
import com.latent.camera.settings.GridMode
import com.latent.camera.ui.theme.LatentInk
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val AidChipShape = RoundedCornerShape(4.dp)

/**
 * Compose overlays that never touch the graded GL frame — grids, level, histogram.
 * Peaking and zebra are drawn in the preview-only GL pass instead.
 */
@Composable
fun GridOverlay(mode: GridMode, modifier: Modifier = Modifier) {
    if (mode == GridMode.Off) return
    val color = LatentInk.Full.copy(alpha = 0.28f)
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val stroke = 1.dp.toPx()
        when (mode) {
            GridMode.Thirds -> {
                drawLine(color, Offset(w / 3f, 0f), Offset(w / 3f, h), stroke)
                drawLine(color, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), stroke)
                drawLine(color, Offset(0f, h / 3f), Offset(w, h / 3f), stroke)
                drawLine(color, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), stroke)
            }
            GridMode.Centre -> {
                drawLine(color, Offset(w / 2f, 0f), Offset(w / 2f, h), stroke)
                drawLine(color, Offset(0f, h / 2f), Offset(w, h / 2f), stroke)
            }
            GridMode.Diagonals -> {
                drawLine(color, Offset(0f, 0f), Offset(w, h), stroke)
                drawLine(color, Offset(w, 0f), Offset(0f, h), stroke)
            }
            GridMode.Golden -> {
                // Square golden section: φ⁻¹ ≈ 0.618 from each edge.
                val g = 0.618f
                drawLine(color, Offset(w * (1f - g), 0f), Offset(w * (1f - g), h), stroke)
                drawLine(color, Offset(w * g, 0f), Offset(w * g, h), stroke)
                drawLine(color, Offset(0f, h * (1f - g)), Offset(w, h * (1f - g)), stroke)
                drawLine(color, Offset(0f, h * g), Offset(w, h * g), stroke)
            }
            GridMode.Off -> Unit
        }
    }
}

@Composable
fun LevelOverlay(rollDegrees: Float, modifier: Modifier = Modifier) {
    val level = abs(rollDegrees) < 0.8f
    val color = if (level) LatentInk.Lock else LatentInk.Full.copy(alpha = 0.75f)
    Canvas(modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val half = size.width * 0.28f
        val rad = Math.toRadians(rollDegrees.toDouble())
        val dx = (cos(rad) * half).toFloat()
        val dy = (sin(rad) * half).toFloat()
        val stroke = if (level) 2.dp.toPx() else 1.2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(cx - dx, cy - dy),
            end = Offset(cx + dx, cy + dy),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        // Fixed reference ticks at true horizon.
        val tick = 10.dp.toPx()
        drawLine(
            color = LatentInk.Soft,
            start = Offset(cx - half - tick, cy),
            end = Offset(cx - half, cy),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = LatentInk.Soft,
            start = Offset(cx + half, cy),
            end = Offset(cx + half + tick, cy),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        if (level) {
            drawCircle(color = LatentInk.Lock, radius = 3.dp.toPx(), center = Offset(cx, cy))
        }
    }
}

@Composable
fun HistogramOverlay(bins: FloatArray?, modifier: Modifier = Modifier) {
    if (bins == null || bins.isEmpty()) return
    Box(
        modifier = modifier
            .width(120.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(4.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val n = bins.size
            val barW = size.width / n
            val maxH = size.height
            for (i in bins.indices) {
                val h = (bins[i].coerceIn(0f, 1f) * maxH)
                if (h <= 0f) continue
                drawRect(
                    color = LatentInk.Full.copy(alpha = 0.55f),
                    topLeft = Offset(i * barW, maxH - h),
                    size = Size(barW, h),
                )
            }
            // Mid / clip guides
            val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
            drawLine(
                LatentInk.Soft,
                Offset(size.width * 0.5f, 0f),
                Offset(size.width * 0.5f, maxH),
                1.dp.toPx(),
                pathEffect = dash,
            )
        }
    }
}

/**
 * Compact toggles for shooting aids. Lives above the capture deck so the shutter
 * reach never changes.
 */
@Composable
fun AidsBar(
    aids: AidsState,
    settings: AidsSettings,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AidToggle("PEAK", aids.peaking) { settings.setPeaking(!aids.peaking) }
        AidToggle("ZEBRA", aids.zebra) { settings.setZebra(!aids.zebra) }
        AidToggle("HIST", aids.histogram) { settings.setHistogram(!aids.histogram) }
        AidToggle("LEVEL", aids.level) { settings.setLevel(!aids.level) }
        AidToggle(
            label = when (aids.grid) {
                GridMode.Off -> "GRID"
                GridMode.Thirds -> "3rds"
                GridMode.Centre -> "CROSS"
                GridMode.Diagonals -> "DIAG"
                GridMode.Golden -> "φ"
            },
            active = aids.grid != GridMode.Off,
            onClick = { settings.cycleGrid() },
        )
    }
}

@Composable
private fun AidToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
        color = if (active) LatentInk.Full else LatentInk.Soft,
        modifier = Modifier
            .clip(AidChipShape)
            .background(if (active) LatentInk.Wash else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}
