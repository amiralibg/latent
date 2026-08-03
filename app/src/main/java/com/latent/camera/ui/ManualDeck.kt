package com.latent.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.latent.camera.camera.CameraCapabilities
import com.latent.camera.camera.CameraState
import com.latent.camera.camera.MeteredValues
import com.latent.camera.ui.theme.LatentInk
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.roundToInt

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
            Text(
                text = "AUTO",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.manual.isAnyManual) LatentInk.Medium else LatentInk.Soft,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
                    Text(
                        text = "HOLD STEADY",
                        style = MaterialTheme.typography.labelSmall,
                        color = LatentInk.Warn,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            ManualParam.Focus -> if (capabilities.supportsManualFocus) {
                FocusHint(
                    state = state,
                    capabilities = capabilities,
                    onEngage = {
                        if (state.manual.focusDioptres == null) {
                            onFocus(
                                state.metered.focusDistanceDioptres ?: 0f,
                            )
                        }
                    },
                    onAuto = { onFocus(null) },
                )
            }
        }
    }
}

@Composable
private fun ParamChip(
    label: String,
    selected: Boolean,
    engaged: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = when {
            selected -> LatentInk.Full
            engaged -> LatentInk.Medium
            else -> LatentInk.Soft
        },
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) LatentInk.Wash else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun FocusHint(
    state: CameraState,
    capabilities: CameraCapabilities,
    onEngage: () -> Unit,
    onAuto: () -> Unit,
) {
    val manual = state.manual.focusDioptres != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(RecipeStripHeight)
            .clickable(onClick = { if (manual) onAuto() else onEngage() }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (manual) {
                formatFocusDistance(state.manual.focusDioptres ?: 0f)
            } else {
                "DRAG LEFT RAIL"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = LatentInk.Strong,
        )
        Text(
            text = if (manual) "TAP FOR AUTO" else "TAP TO ENGAGE",
            style = MaterialTheme.typography.labelSmall,
            color = LatentInk.Soft,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Vertical focus rail on the left of the frame. Near at the bottom, infinity at
 * the top — the way a focus ring feels when the camera is upright.
 */
@Composable
fun FocusRail(
    dioptres: Float,
    maxDioptres: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var accum by remember { mutableFloatStateOf(0f) }
    val max = maxDioptres.coerceAtLeast(0.01f)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(48.dp)
            .pointerInput(max, dioptres) {
                detectVerticalDragGestures(
                    onDragEnd = { accum = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // Drag up → infinity (fewer dioptres); down → nearer.
                        accum += dragAmount
                        val spanPx = size.height * 0.7f
                        if (abs(accum) >= 1f) {
                            val delta = (accum / spanPx) * max
                            accum = 0f
                            onChange((dioptres + delta).coerceIn(0f, max))
                        }
                    },
                )
            }
            .padding(vertical = 48.dp, horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight(0.55f),
        ) {
            Text("∞", style = MaterialTheme.typography.labelSmall, color = LatentInk.Soft)
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(LatentInk.Faint, LatentInk.Medium, LatentInk.Faint),
                        ),
                        RoundedCornerShape(1.dp),
                    ),
            )
            Text("N", style = MaterialTheme.typography.labelSmall, color = LatentInk.Soft)
        }
    }
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

    LaunchedEffect(listState) {
        snapshotFlow { centredIndex to listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (index, scrolling) ->
                if (!scrolling && index != currentSelected) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(index)
                }
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
                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .height(RecipeStripHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = if (selected) LatentInk.Full else LatentInk.Soft,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) LatentInk.Wash else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(index) },
                            )
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
