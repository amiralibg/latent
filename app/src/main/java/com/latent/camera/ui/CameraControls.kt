package com.latent.camera.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.latent.camera.camera.CameraState
import com.latent.camera.camera.Lens
import com.latent.camera.camera.MeteredValues
import com.latent.camera.settings.SaveMode
import com.latent.camera.ui.gallery.rememberThumbnail
import com.latent.camera.ui.theme.LatentInk
import kotlin.math.roundToInt

/**
 * Physical lens buttons on the bottom of the square — small filled discs, the way
 * native camera apps mark glass, not text pills competing with the frame.
 */
@Composable
fun LensPicker(
    lenses: List<Lens>,
    active: Lens?,
    zoomRatio: Float,
    onSelect: (Lens) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lenses.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val showZoom = active == null ||
            lenses.none { kotlin.math.abs(it.zoomRatio - zoomRatio) < 0.05f }
        if (showZoom && lenses.size >= 2) {
            Text(
                text = formatZoom(zoomRatio),
                style = MaterialTheme.typography.bodyMedium,
                color = LatentInk.Strong,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (lenses.size >= 2) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                lenses.forEach { lens ->
                    val isOn = active != null &&
                        kotlin.math.abs(active.zoomRatio - lens.zoomRatio) < 0.05f
                    LensDisc(
                        label = lens.label.removeSuffix("×"),
                        selected = isOn,
                        onClick = { onSelect(lens) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LensDisc(label: String, selected: Boolean, onClick: () -> Unit) {
    val size = if (selected) 36.dp else 30.dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) LatentInk.Full else LatentInk.Wash)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else LatentInk.Faint,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (selected) 12.sp else 11.sp,
            color = if (selected) Color.Black else LatentInk.Strong,
        )
    }
}

/**
 * Instrument strip across the top of the frame. Monospace values, quiet labels —
 * a body readout, not a settings row.
 */
@Composable
fun MeterOverlay(
    state: CameraState,
    onDismissError: () -> Unit,
    onClearFocusLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Transparent,
                ),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        when {
            state.error != null -> {
                Text(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clickable(onClick = onDismissError),
                    text = state.error.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = LatentInk.Warn,
                )
            }
            state.handheldWarning -> {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = "HOLD STEADY",
                    style = MaterialTheme.typography.labelMedium,
                    color = LatentInk.Warn,
                )
            }
            else -> {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .then(
                            if (state.focus?.locked == true) {
                                Modifier.clickable(onClick = onClearFocusLock)
                            } else {
                                Modifier
                            },
                        ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MeterCell(
                        label = "ISO",
                        value = (state.manual.iso ?: state.metered.iso)?.toString() ?: "—",
                        emphasize = state.manual.iso != null,
                    )
                    MeterHairline()
                    MeterCell(
                        label = "SS",
                        value = (state.manual.exposureTimeNs ?: state.metered.exposureTimeNs)
                            ?.let(MeteredValues::formatShutter) ?: "—",
                        emphasize = state.manual.exposureTimeNs != null,
                    )
                    if (state.capabilities?.supportsEv == true && !state.manual.isExposureManual) {
                        MeterHairline()
                        MeterCell(
                            label = "EV",
                            value = formatEv(state.exposureEv),
                            emphasize = kotlin.math.abs(state.exposureEv) > 0.05f,
                        )
                    }
                    if (state.focus?.locked == true) {
                        MeterHairline()
                        LockBadge()
                    }
                }
            }
        }
    }
}

@Composable
private fun MeterCell(label: String, value: String, emphasize: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LatentInk.Soft,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (emphasize) LatentInk.Full else LatentInk.Strong,
        )
    }
}

@Composable
private fun MeterHairline() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp)
            .width(1.dp)
            .height(18.dp)
            .background(LatentInk.Faint),
    )
}

/**
 * The strip above the frame: everything that is a mode rather than a shooting
 * control. It lives in the black surround and not on the picture, so the square is
 * never sharing space with app chrome.
 */
@Composable
fun ViewfinderTopBar(
    state: CameraState,
    aidsOpen: Boolean,
    onTimerCycle: () -> Unit,
    onSilentToggle: () -> Unit,
    onToggleAids: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChromeIconButton(
            onClick = onTimerCycle,
            active = state.timerSeconds > 0 || state.countdown > 0,
            badge = state.timerSeconds.takeIf { it > 0 }?.toString(),
        ) {
            TimerIcon(
                seconds = state.timerSeconds,
                active = state.timerSeconds > 0 || state.countdown > 0,
            )
        }
        ChromeIconButton(onClick = onSilentToggle, active = state.silentShutter) {
            SoundIcon(silent = state.silentShutter)
        }

        Spacer(Modifier.weight(1f))

        // Only worth saying when it is not the default, and then worth saying plainly.
        val saveLabel = when (state.saveMode) {
            SaveMode.BwOnly -> "B&W"
            SaveMode.BwAndOriginal -> null
            SaveMode.BwOriginalAndDng -> "DNG"
        }
        if (saveLabel != null) {
            Text(
                text = saveLabel,
                style = MaterialTheme.typography.labelSmall,
                color = LatentInk.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(LatentInk.Wash)
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(4.dp))
        }

        ChromeIconButton(onClick = onToggleAids, active = aidsOpen) {
            AidsIcon(active = aidsOpen || state.aids.anyOn)
        }
        ChromeIconButton(onClick = onSettings, active = false) { SettingsIcon() }
    }
}

/**
 * Bottom chrome: the last frame, the shutter, the mode. Wings of equal weight keep
 * the shutter geometrically centred under the thumb whatever is beside it.
 */
@Composable
fun CaptureBar(
    state: CameraState,
    shutterEnabled: Boolean,
    manualOpen: Boolean,
    lastCaptureUri: Uri?,
    onShutter: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showManual = state.capabilities?.supportsManualSensor == true ||
        state.capabilities?.supportsManualFocus == true
    val manualActive = manualOpen || state.manual.isAnyManual

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            GalleryButton(uri = lastCaptureUri, onClick = onOpenGallery)
        }

        ShutterButton(
            enabled = shutterEnabled,
            countingDown = state.countdown > 0,
            onClick = onShutter,
        )

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (showManual) {
                ChromeIconButton(
                    onClick = onOpenManual,
                    active = manualActive,
                    badge = if (manualActive) "M" else "A",
                ) {
                    ModeIcon(manual = manualActive)
                }
            }
        }
    }
}

/**
 * The last frame, as a small square beside the shutter, and the way into the contact
 * sheet. Showing the shot rather than an icon is what makes a capture feel like it
 * landed somewhere — the frame counter says a number, this says which picture.
 */
@Composable
fun GalleryButton(uri: Uri?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val sidePx = with(density) { GalleryButtonSide.roundToPx() }
    val thumbnail by rememberThumbnail(uri, sidePx)

    Box(
        modifier = modifier
            .size(GalleryButtonSide)
            .clip(RoundedCornerShape(3.dp))
            .background(LatentInk.Wash)
            .border(1.dp, LatentInk.Faint, RoundedCornerShape(3.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val frame = thumbnail
        if (frame != null) {
            Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            GalleryIcon(size = 18.dp, tint = LatentInk.Soft)
        }
    }
}

private val GalleryButtonSide = 44.dp

@Composable
fun ChromeIconButton(
    onClick: () -> Unit,
    active: Boolean,
    badge: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (active) LatentInk.Wash else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = LatentInk.Full,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp),
            )
        }
    }
}

@Composable
fun ShutterButton(
    enabled: Boolean,
    countingDown: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val alpha = when {
        !enabled -> 0.28f
        countingDown -> 0.55f
        else -> 1f
    }

    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .border(1.5.dp, LatentInk.Full.copy(alpha = alpha), CircleShape)
            .padding(5.dp)
            .border(1.dp, LatentInk.Full.copy(alpha = alpha * 0.35f), CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(LatentInk.Full.copy(alpha = alpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
    )
}

/** Focus reticle — corner brackets, not a filled box sitting on the frame. */
@Composable
fun FocusReticle(locked: Boolean, modifier: Modifier = Modifier) {
    val color = if (locked) LatentInk.Lock else LatentInk.Full
    Canvas(modifier = modifier.size(64.dp)) {
        val stroke = 1.5.dp.toPx()
        val arm = 14.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(color, Offset(0f, 0f), Offset(arm, 0f), stroke, StrokeCap.Round)
        drawLine(color, Offset(0f, 0f), Offset(0f, arm), stroke, StrokeCap.Round)
        drawLine(color, Offset(w - arm, 0f), Offset(w, 0f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w, 0f), Offset(w, arm), stroke, StrokeCap.Round)
        drawLine(color, Offset(0f, h), Offset(arm, h), stroke, StrokeCap.Round)
        drawLine(color, Offset(0f, h - arm), Offset(0f, h), stroke, StrokeCap.Round)
        drawLine(color, Offset(w - arm, h), Offset(w, h), stroke, StrokeCap.Round)
        drawLine(color, Offset(w, h - arm), Offset(w, h), stroke, StrokeCap.Round)
        if (locked) {
            drawCircle(color = LatentInk.Lock, radius = 2.dp.toPx(), center = center)
        }
    }
}

fun cycleTimerSeconds(current: Int): Int = when (current) {
    0 -> 3
    3 -> 10
    else -> 0
}

fun formatEv(ev: Float): String {
    if (kotlin.math.abs(ev) < 0.05f) return "±0.0"
    val sign = if (ev > 0f) "+" else ""
    val rounded = (ev * 10f).roundToInt() / 10f
    return "$sign${"%.1f".format(rounded)}"
}

private fun formatZoom(ratio: Float): String {
    val rounded = (ratio * 10).roundToInt() / 10f
    return if (kotlin.math.abs(rounded - rounded.roundToInt()) < 0.05f) {
        "${rounded.roundToInt()}×"
    } else {
        "$rounded×"
    }
}

internal fun isoStops(range: IntRange): List<Int> {
    val standards = listOf(
        25, 32, 40, 50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640,
        800, 1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000,
        10000, 12800, 16000, 20000, 25600,
    )
    val filtered = standards.filter { it in range }
    return filtered.ifEmpty { listOf(range.first, range.last).distinct() }
}

internal fun shutterStops(range: LongRange): List<Long> {
    val denominators = listOf(
        8000, 4000, 2000, 1000, 500, 250, 125, 60, 30, 15, 8, 4, 2,
    )
    val fractions = denominators.map { 1_000_000_000L / it }
    val wholes = listOf(1L, 2L, 4L, 8L, 15L, 30L).map { it * 1_000_000_000L }
    val filtered = (fractions + wholes).filter { it in range }.distinct().sorted()
    return filtered.ifEmpty { listOf(range.first, range.last).distinct().sorted() }
}
