package com.latent.camera.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.latent.camera.camera.CameraState
import com.latent.camera.camera.Lens
import com.latent.camera.camera.MeteredValues
import com.latent.camera.look.Recipe
import com.latent.camera.settings.SaveMode
import com.latent.camera.ui.gallery.rememberThumbnail
import com.latent.camera.ui.theme.LatentInk
import com.latent.camera.ui.theme.LatentText
import com.latent.camera.ui.theme.LatentType
import com.latent.camera.ui.theme.Motion
import com.latent.camera.ui.theme.tactile
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

        // The ratio only appears once you are between the physical lenses, and it
        // fades rather than blinks, because pinching is a continuous gesture and a
        // readout that pops in mid-pinch reads as a glitch.
        AnimatedVisibility(
            visible = showZoom && lenses.size >= 2,
            enter = fadeIn(Motion.enter()),
            exit = fadeOut(Motion.leave()),
        ) {
            LatentText(
                text = formatZoom(zoomRatio),
                style = LatentType.Readout,
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
    val size by animateDpAsState(
        targetValue = if (selected) 36.dp else 30.dp,
        animationSpec = Motion.settle(),
        label = "lensDiscSize",
    )
    val fill by animateColorAsState(
        targetValue = if (selected) LatentInk.Full else LatentInk.Wash,
        animationSpec = Motion.state(),
        label = "lensDiscFill",
    )
    val ink by animateColorAsState(
        targetValue = if (selected) Color.Black else LatentInk.Strong,
        animationSpec = Motion.state(),
        label = "lensDiscInk",
    )

    Box(
        modifier = Modifier
            .size(size)
            .tactile(onClick = onClick)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else LatentInk.Faint,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        LatentText(
            text = label,
            style = LatentType.Readout.copy(fontSize = if (selected) 12.sp else 11.sp),
            color = ink,
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
    // Three mutually exclusive things want this strip. Crossfading between them keeps
    // a transient warning from feeling like the meter crashed and came back.
    val mode = when {
        state.error != null -> MeterMode.Error
        state.handheldWarning -> MeterMode.Warning
        else -> MeterMode.Values
    }

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
        Crossfade(
            targetState = mode,
            animationSpec = tween(180, easing = Motion.Sharp),
            label = "meterMode",
            modifier = Modifier.align(Alignment.Center),
        ) { current ->
            when (current) {
                MeterMode.Error -> LatentText(
                    modifier = Modifier.tactile(onClick = onDismissError),
                    text = state.error.orEmpty().uppercase(),
                    style = LatentType.LabelMedium,
                    color = LatentInk.Warn,
                )

                MeterMode.Warning -> PulsingWarning("HOLD STEADY")

                MeterMode.Values -> Row(
                    modifier = if (state.focus?.locked == true) {
                        Modifier.tactile(onClick = onClearFocusLock)
                    } else {
                        Modifier
                    },
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

private enum class MeterMode { Error, Warning, Values }

/** A warning you are meant to act on within the second, so it breathes. */
@Composable
private fun PulsingWarning(text: String) {
    val transition = rememberInfiniteTransition(label = "warningPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(620, easing = Motion.Sharp),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "warningAlpha",
    )
    LatentText(
        text = text,
        style = LatentType.LabelMedium,
        color = LatentInk.Warn.copy(alpha = alpha),
    )
}

@Composable
private fun MeterCell(label: String, value: String, emphasize: Boolean) {
    val ink by animateColorAsState(
        targetValue = if (emphasize) LatentInk.Full else LatentInk.Strong,
        animationSpec = Motion.state(),
        label = "meterInk",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LatentText(text = label, style = LatentType.LabelSmall, color = LatentInk.Soft)
        Spacer(Modifier.height(2.dp))
        LatentText(text = value, style = LatentType.Readout, color = ink)
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
    recipe: Recipe,
    aidsOpen: Boolean,
    onTimerCycle: () -> Unit,
    onSilentToggle: () -> Unit,
    onGrainCycle: () -> Unit,
    onEditRecipe: () -> Unit,
    onFlipCamera: () -> Unit,
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
            TimerIcon(active = state.timerSeconds > 0 || state.countdown > 0)
        }
        ChromeIconButton(onClick = onSilentToggle, active = state.silentShutter) {
            SoundIcon(silent = state.silentShutter)
        }

        // Grain is the one look stage worth a control up here. It is the stage people
        // reach for constantly, it is the stage that has to be judged against the live
        // picture rather than a slider, and the icon doubles as its readout. Tap
        // cycles the stocks; hold opens the recipe if you want the numbers.
        ChromeIconButton(
            onClick = onGrainCycle,
            onLongClick = onEditRecipe,
            active = recipe.grain > 0.001f,
        ) {
            GrainIcon(amount = recipe.grain, grainSize = recipe.grainSize)
        }

        Spacer(Modifier.weight(1f))

        // Only worth saying when it is not the default, and then worth saying plainly.
        val saveLabel = when (state.saveMode) {
            SaveMode.BwOnly -> "B&W"
            SaveMode.BwAndOriginal -> null
            SaveMode.BwOriginalAndDng -> "DNG"
        }
        AnimatedVisibility(
            visible = saveLabel != null,
            enter = fadeIn(Motion.enter()) + expandHorizontally(Motion.resize()),
            exit = fadeOut(Motion.leave()) + shrinkHorizontally(Motion.resize()),
        ) {
            LatentText(
                text = saveLabel.orEmpty(),
                style = LatentType.LabelSmall,
                color = LatentInk.Medium,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(LatentInk.Wash)
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            )
        }

        // Only on a device that actually has a second camera — a control that does
        // nothing is worse than no control.
        if (state.hasFrontCamera) {
            ChromeIconButton(onClick = onFlipCamera, active = state.frontFacing) {
                FlipIcon(front = state.frontFacing)
            }
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
            GalleryButton(
                uri = lastCaptureUri,
                framesShot = state.framesThisSession,
                onClick = onOpenGallery,
            )
        }

        ShutterButton(
            enabled = shutterEnabled,
            countingDown = state.countdown > 0,
            saving = state.inFlight > 0,
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
 *
 * It kicks when a new frame arrives. That kick is the only confirmation the app gives
 * that a shot was written, and it is deliberately in the corner of the eye rather than
 * over the picture: you should be able to keep shooting through it.
 */
@Composable
fun GalleryButton(
    uri: Uri?,
    framesShot: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val sidePx = with(density) { GalleryButtonSide.roundToPx() }
    val thumbnail by rememberThumbnail(uri, sidePx)

    var landed by remember { mutableStateOf(false) }
    LaunchedEffect(framesShot) {
        if (framesShot > 0) {
            landed = true
            kotlinx.coroutines.delay(220)
            landed = false
        }
    }
    val kick by animateFloatAsState(
        targetValue = if (landed) 1.16f else 1f,
        animationSpec = Motion.settle(),
        label = "galleryKick",
    )

    Box(
        modifier = modifier
            .size(GalleryButtonSide)
            .tactile(onClick = onClick)
            .graphicsLayer {
                scaleX = kick
                scaleY = kick
            }
            .clip(RoundedCornerShape(4.dp))
            .background(LatentInk.Wash)
            .border(1.dp, LatentInk.Faint, RoundedCornerShape(4.dp)),
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

/** The lit disc inside a 48dp target. Small enough that neighbours stay separate. */
private val ChromeDiscSide = 38.dp

@Composable
fun ChromeIconButton(
    onClick: () -> Unit,
    active: Boolean,
    badge: String? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val wash by animateColorAsState(
        targetValue = if (active) LatentInk.Wash else Color.Transparent,
        animationSpec = Motion.state(),
        label = "chromeWash",
    )

    // The touch target and the disc are deliberately different sizes. The target stays
    // thumb-sized; the wash is drawn smaller and centred inside it, so two lit buttons
    // sitting next to each other read as two buttons rather than one long blob. Sizing
    // the background to the hit area is what made the top row look like a smear.
    Box(
        modifier = Modifier
            .size(48.dp)
            .tactile(onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(ChromeDiscSide)
                .clip(CircleShape)
                .background(wash),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        AnimatedVisibility(
            visible = badge != null,
            enter = fadeIn(Motion.enter()),
            exit = fadeOut(Motion.leave()),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            LatentText(
                text = badge.orEmpty(),
                style = LatentType.LabelSmall,
                color = LatentInk.Full,
                modifier = Modifier.padding(top = 6.dp, end = 6.dp),
            )
        }
    }
}

/**
 * The shutter.
 *
 * Two rings and a disc. The disc answers the finger; the outer ring carries state that
 * is not about this press — a countdown running, a frame still being written — so the
 * thing under the thumb never has to mean two things at once. Nothing here gates the
 * click: the button is never disabled while a save is in flight, because the app's
 * first rule is that the next frame is always available.
 */
@Composable
fun ShutterButton(
    enabled: Boolean,
    countingDown: Boolean,
    saving: Boolean,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.28f
            countingDown -> 0.55f
            else -> 1f
        },
        animationSpec = Motion.state(),
        label = "shutterAlpha",
    )

    val transition = rememberInfiniteTransition(label = "shutterActivity")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = Motion.Sharp)),
        label = "shutterSweep",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(560, easing = Motion.Sharp),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shutterBreathe",
    )

    val ringScale by animateFloatAsState(
        targetValue = if (countingDown) breathe else 1f,
        animationSpec = Motion.state(),
        label = "shutterRing",
    )

    Box(
        modifier = Modifier.size(84.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = ringScale
                    scaleY = ringScale
                },
        ) {
            val stroke = 1.5.dp.toPx()
            drawCircle(
                color = LatentInk.Full.copy(alpha = alpha),
                radius = size.minDimension / 2f - stroke / 2f,
                style = Stroke(width = stroke),
            )
            // A frame is still being written. The arc is on the ring rather than over
            // the picture, and it never touches the disc, so it cannot be mistaken for
            // the shutter being busy.
            if (saving) {
                val inset = stroke * 2.5f
                drawArc(
                    color = LatentInk.Full,
                    startAngle = sweep,
                    sweepAngle = 66f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(62.dp)
                .tactile(
                    enabled = enabled,
                    pressScale = 0.84f,
                    pressAlpha = 0.9f,
                    haptic = null,
                    onClick = onClick,
                )
                .clip(CircleShape)
                .background(LatentInk.Full.copy(alpha = alpha)),
        )
    }
}

/** Focus reticle — corner brackets, not a filled box sitting on the frame. */
@Composable
fun FocusReticle(locked: Boolean, modifier: Modifier = Modifier) {
    val color = if (locked) LatentInk.Lock else LatentInk.Full

    // Lands slightly large and settles, which is what makes tap-to-focus feel like it
    // was aimed rather than like a box that appeared.
    var arrived by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { arrived = true }
    val scale by animateFloatAsState(
        targetValue = if (arrived) 1f else 1.35f,
        animationSpec = Motion.settle(),
        label = "reticleScale",
    )
    val fade by animateFloatAsState(
        targetValue = if (arrived) 1f else 0f,
        animationSpec = Motion.enter(),
        label = "reticleFade",
    )

    Canvas(
        modifier = modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = fade
            },
    ) {
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
