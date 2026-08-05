package com.latent.camera.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.latent.camera.camera.CameraState
import com.latent.camera.camera.MeteredValues
import com.latent.camera.ui.theme.Feedback
import com.latent.camera.ui.theme.LatentChip
import com.latent.camera.ui.theme.LatentInk
import com.latent.camera.ui.theme.LatentText
import com.latent.camera.ui.theme.LatentType
import com.latent.camera.ui.theme.Motion
import com.latent.camera.ui.theme.tactile
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

enum class ManualParam { Iso, Shutter, Focus }

/**
 * Inline manual deck — replaces the recipe strip while you're riding the camera.
 * Tick dials, not a settings sheet: the viewfinder stays the reference.
 */
@Composable
fun ManualDeck(
    state: CameraState,
    param: ManualParam,
    onParamChange: (ManualParam) -> Unit,
    onIso: (Int?) -> Unit,
    onShutter: (Long?) -> Unit,
    onFocus: (Float?) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val capabilities = state.capabilities ?: return
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (capabilities.supportsManualIso) {
                    ParamChip(
                        label = "ISO",
                        selected = param == ManualParam.Iso,
                        engaged = state.manual.iso != null,
                        onClick = { onParamChange(ManualParam.Iso) },
                    )
                }
                if (capabilities.supportsManualShutter) {
                    ParamChip(
                        label = "SS",
                        selected = param == ManualParam.Shutter,
                        engaged = state.manual.exposureTimeNs != null,
                        onClick = { onParamChange(ManualParam.Shutter) },
                    )
                }
                if (capabilities.supportsManualFocus) {
                    ParamChip(
                        label = "FOCUS",
                        selected = param == ManualParam.Focus,
                        engaged = state.manual.focusDioptres != null,
                        onClick = { onParamChange(ManualParam.Focus) },
                    )
                }
            }
            LatentChip(
                label = "AUTO",
                selected = false,
                idleTint = if (state.manual.isAnyManual) LatentInk.Medium else LatentInk.Soft,
                horizontalPadding = 10.dp,
                onClick = {
                    Feedback.confirm(haptics)
                    onClear()
                },
            )
        }

        Spacer(Modifier.height(4.dp))

        when (param) {
            ManualParam.Iso -> if (capabilities.supportsManualIso) {
                val range = capabilities.isoRange ?: 100..3200
                val stops = isoStops(range)
                val current = state.manual.iso
                    ?: state.metered.iso
                    ?: stops[stops.size / 2]
                TickDial(
                    labels = stops.map { it.toString() },
                    selectedIndex = stops.indexOfClosest(current).coerceAtLeast(0),
                    onSelect = { index ->
                        if (state.manual.iso == null) {
                            // Engaging ISO also holds shutter — Camera2 AE is all-or-nothing.
                            onIso(stops[index])
                        } else {
                            onIso(stops[index])
                        }
                    },
                    onEngageIfNeeded = {
                        if (state.manual.iso == null) onIso(current)
                    },
                )
            }
            ManualParam.Shutter -> if (capabilities.supportsManualShutter) {
                val range = capabilities.exposureTimeRangeNs ?: return
                val stops = shutterStops(range)
                val current = state.manual.exposureTimeNs
                    ?: state.metered.exposureTimeNs
                    ?: stops[stops.size / 2]
                TickDial(
                    labels = stops.map(MeteredValues::formatShutter),
                    selectedIndex = stops.indexOfClosest(current).coerceAtLeast(0),
                    onSelect = { index -> onShutter(stops[index]) },
                    onEngageIfNeeded = {
                        if (state.manual.exposureTimeNs == null) onShutter(current)
                    },
                )
                if (state.handheldWarning) {
                    LatentText(
                        text = "HOLD STEADY",
                        style = LatentType.LabelSmall,
                        color = LatentInk.Warn,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            ManualParam.Focus -> if (capabilities.supportsManualFocus) {
                // The same dial as ISO and shutter, deliberately. Focus used to be a
                // vertical rail hidden down the left of the frame, which meant the
                // FOCUS chip selected a control that was somewhere else on screen and
                // had to be explained in words. A parameter you pick from a row of
                // distances needs no instructions and behaves like its two neighbours.
                val stops = focusStops(capabilities.minFocusDistanceDioptres)
                val current = state.manual.focusDioptres
                    ?: state.metered.focusDistanceDioptres
                    ?: 0f
                TickDial(
                    labels = stops.map(::formatFocusDistance),
                    selectedIndex = stops.indexOfClosestDioptre(current),
                    onSelect = { index -> onFocus(stops[index]) },
                    onEngageIfNeeded = {
                        if (state.manual.focusDioptres == null) onFocus(current)
                    },
                )
                if (!capabilities.focusDistanceCalibrated) {
                    // The device says its dioptre scale is arbitrary, so the metre
                    // labels are a rough guide rather than a measurement. Better to
                    // admit that than to print "0.5 m" and be believed.
                    LatentText(
                        text = "DISTANCES APPROXIMATE",
                        style = LatentType.LabelSmall,
                        color = LatentInk.Soft,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Selected is which dial the deck is showing; engaged is whether that parameter has
 * actually been taken off auto. They are different facts and they need different ink —
 * a chip you are looking at is not the same as a chip that is holding the camera.
 */
@Composable
private fun ParamChip(
    label: String,
    selected: Boolean,
    engaged: Boolean,
    onClick: () -> Unit,
) {
    LatentChip(
        label = label,
        selected = selected,
        idleTint = if (engaged) LatentInk.Medium else LatentInk.Soft,
        horizontalPadding = 12.dp,
        onClick = onClick,
    )
}

/**
 * Centre-snapped tick dial — the photographic control, not a Material slider.
 */
@Composable
fun TickDial(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onEngageIfNeeded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var widthPx by remember { mutableIntStateOf(0) }
    val itemWidth = 72.dp
    val itemWidthPx = with(density) { itemWidth.roundToPx() }
    val sidePadding = with(density) {
        ((widthPx - itemWidthPx) / 2).coerceAtLeast(0).toDp()
    }

    val flingBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center,
    )

    // Item offsets are relative to the content area, not the row's edge — see
    // RecipeStrip. Comparing them to the content centre, not the row centre,
    // is what keeps the value under the hairline the one that gets set.
    val centredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCentre =
                (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
            layoutInfo.visibleItemsInfo.minByOrNull { item ->
                abs(item.offset + item.size / 2f - viewportCentre)
            }?.index ?: selectedIndex
        }
    }

    // Same stale-capture trap as RecipeStrip: selectedIndex is a StateFlow that
    // arrives back through the parent, so the collector must read it fresh.
    val currentSelected by rememberUpdatedState(selectedIndex)

    LaunchedEffect(Unit) { onEngageIfNeeded() }

    // Falling edge of the scroll only — see the note in RecipeStrip. A dial that
    // selects on relayout will walk ISO a stop sideways every time the deck redraws.
    LaunchedEffect(listState) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (wasScrolling && !scrolling) {
                    val index = centredIndex
                    if (index != currentSelected) {
                        Feedback.tick(haptics)
                        onSelect(index)
                    }
                }
                wasScrolling = scrolling
            }
    }

    LaunchedEffect(selectedIndex, labels.size, widthPx) {
        if (widthPx > 0 && centredIndex != selectedIndex) {
            listState.animateScrollToItem(selectedIndex.coerceIn(0, labels.lastIndex))
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(RecipeStripHeight)
                .onSizeChanged { widthPx = it.width },
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(horizontal = sidePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(labels) { index, label ->
                val selected = index == selectedIndex
                // The value under the hairline is a little larger than its neighbours.
                // Scale, not just ink: at arm's length the size difference is what
                // makes the dial readable while it is still moving.
                val emphasis by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.86f,
                    animationSpec = Motion.settle(),
                    label = "dialEmphasis",
                )
                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .height(RecipeStripHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    LatentText(
                        text = label,
                        style = LatentType.Readout,
                        align = TextAlign.Center,
                        color = if (selected) LatentInk.Full else LatentInk.Soft,
                        modifier = Modifier
                            .tactile { onSelect(index) }
                            .graphicsLayer {
                                scaleX = emphasis
                                scaleY = emphasis
                            }
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) LatentInk.Wash else Color.Transparent)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
        // Centre hairline — the value under the mark is the one that is set.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(1.dp)
                .height(20.dp)
                .background(LatentInk.Faint),
        )
    }
}

/**
 * Focus stops, infinity first and closest last — the direction a focus ring turns.
 *
 * The dial is built from distances a photographer would name rather than from evenly
 * spaced dioptres. Dioptres are 1/metres, so an even split in dioptre space spends
 * half the dial between 5cm and 10cm and gives everything from 2m to infinity a single
 * stop. Choosing the metres and converting is what makes the middle of the dial the
 * part you actually shoot in.
 */
private fun focusStops(minFocusDioptres: Float): List<Float> {
    val max = minFocusDioptres.coerceAtLeast(0.5f)
    val metres = listOf(10f, 5f, 3f, 2f, 1.5f, 1f, 0.7f, 0.5f, 0.35f, 0.25f, 0.2f, 0.15f, 0.1f)
    return (listOf(0f) + metres.map { 1f / it })
        // Nothing past what the lens can actually do, but always offer the true
        // close-focus limit so the near end of the dial is the near end of the lens.
        .filter { it < max }
        .plus(max)
        .distinctBy { (it * 50f).roundToInt() }
        .sorted()
}

private fun List<Float>.indexOfClosestDioptre(value: Float): Int =
    indices.minByOrNull { abs(this[it] - value) } ?: 0

fun formatFocusDistance(dioptres: Float): String {
    if (dioptres <= 0.05f) return "∞"
    val metres = 1f / dioptres
    return if (metres >= 1f) {
        "${(metres * 10).roundToInt() / 10f} m"
    } else {
        "${(metres * 100).roundToInt()} cm"
    }
}

private fun List<Int>.indexOfClosest(value: Int): Int =
    indices.minByOrNull { abs(this[it] - value) } ?: 0

private fun List<Long>.indexOfClosest(value: Long): Int =
    indices.minByOrNull {
        abs(
            kotlin.math.ln(this[it].toDouble().coerceAtLeast(1.0)) -
                kotlin.math.ln(value.toDouble().coerceAtLeast(1.0)),
        )
    } ?: 0
