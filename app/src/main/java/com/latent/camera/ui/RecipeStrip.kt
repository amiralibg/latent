package com.latent.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.latent.camera.look.Recipe
import com.latent.camera.ui.theme.LatentInk
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

private val ItemWidth = 120.dp

/**
 * Recipes sit directly above the shutter. The loaded recipe is always the one
 * snapped to the centre of the strip — never the one parked at an edge.
 */
@Composable
fun RecipeStrip(
    recipes: List<Recipe>,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    onEditActive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recipes.isEmpty()) {
        Box(modifier.fillMaxWidth().height(RecipeStripHeight))
        return
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var widthPx by remember { mutableIntStateOf(0) }

    val itemWidthPx = with(density) { ItemWidth.roundToPx() }
    val sidePadding = with(density) {
        ((widthPx - itemWidthPx) / 2).coerceAtLeast(0).toDp()
    }

    val flingBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center,
    )

    // Nearest-to-centre while the finger is moving; the controller's activeIndex
    // is what we paint as selected so a half-fling never leaves the highlight
    // stranded on the right.
    //
    // Item offsets are relative to the padded content area, not the row's edge,
    // so the centre is taken from the viewport offsets rather than from
    // viewportSize.width / 2, which would be shifted by the side padding and
    // settle the highlight one item too far along.
    val centredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCentre =
                (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
            layoutInfo.visibleItemsInfo.minByOrNull { item ->
                abs(item.offset + item.size / 2f - viewportCentre)
            }?.index ?: activeIndex
        }
    }

    // activeIndex arrives back through the controller as a StateFlow, so reading
    // the parameter directly inside this long-lived collector would compare
    // against the value it had when the effect launched — a recipe scrolled back
    // to that index would never re-select. rememberUpdatedState keeps the
    // comparison honest without restarting the flow.
    val currentActive by rememberUpdatedState(activeIndex)

    LaunchedEffect(listState) {
        snapshotFlow {
            centredIndex to listState.isScrollInProgress
        }
            .distinctUntilChanged()
            .collect { (index, scrolling) ->
                if (!scrolling && index != currentActive) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(index)
                }
            }
    }

    // Keep the loaded recipe geometrically centred when selection changes from
    // outside a fling (tap, seed, duplicate).
    LaunchedEffect(activeIndex, recipes.size, widthPx) {
        if (widthPx == 0) return@LaunchedEffect
        val viewport = listState.layoutInfo.viewportSize.width
        if (viewport == 0) return@LaunchedEffect
        // With centre snap + side padding, offset 0 places the item in the middle.
        if (centredIndex != activeIndex || !isItemCentered(listState, activeIndex, itemWidthPx)) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(RecipeStripHeight)
            .onSizeChanged { widthPx = it.width },
        state = listState,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(horizontal = sidePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(recipes, key = { _, recipe -> recipe.id }) { index, recipe ->
            RecipeLabel(
                name = recipe.name,
                selected = index == activeIndex,
                onClick = {
                    if (index == activeIndex) onEditActive()
                    else onSelect(index)
                },
            )
        }
    }
}

private fun isItemCentered(
    listState: androidx.compose.foundation.lazy.LazyListState,
    index: Int,
    itemWidthPx: Int,
): Boolean {
    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        ?: return false
    val layoutInfo = listState.layoutInfo
    val viewportCentre =
        (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val itemCentre = item.offset + item.size / 2f
    return abs(itemCentre - viewportCentre) < itemWidthPx * 0.15f
}

@Composable
private fun RecipeLabel(name: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(ItemWidth)
            .height(RecipeStripHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                color = if (selected) LatentInk.Full else LatentInk.Soft,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) LatentInk.Wash else Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
        Box(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .size(3.dp)
                .clip(CircleShape)
                .background(if (selected) LatentInk.Full else LatentInk.Faint.copy(alpha = 0f)),
        )
    }
}
