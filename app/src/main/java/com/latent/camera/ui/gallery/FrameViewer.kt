package com.latent.camera.ui.gallery

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.latent.camera.data.CaptureRecord
import com.latent.camera.look.Recipe
import com.latent.camera.ui.BackIcon
import com.latent.camera.ui.CheckIcon
import com.latent.camera.ui.ChromeIconButton
import com.latent.camera.ui.DeleteIcon
import com.latent.camera.ui.RegradeIcon
import com.latent.camera.ui.ShareIcon
import com.latent.camera.ui.theme.Feedback
import com.latent.camera.ui.theme.LatentInk
import com.latent.camera.ui.theme.LatentText
import com.latent.camera.ui.theme.LatentType
import com.latent.camera.ui.theme.Motion
import com.latent.camera.ui.theme.tactile

/**
 * One frame, full width, with the shooting data under it and the three things you can
 * do to it. Swiping moves through the sheet rather than closing the viewer, so a roll
 * can be reviewed without going back to the grid between every shot.
 */
@Composable
fun FrameViewer(
    captures: List<CaptureRecord>,
    startIndex: Int,
    recipes: List<Recipe>,
    onRegrade: (CaptureRecord, Recipe) -> Unit,
    onShare: (CaptureRecord) -> Unit,
    onDelete: (CaptureRecord) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    if (captures.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, captures.lastIndex),
        pageCount = { captures.size },
    )
    val density = LocalDensity.current

    var regrading by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val current = captures.getOrNull(pagerState.currentPage)

    /**
     * The frame being looked at, by identity rather than by position.
     *
     * A re-grade inserts a row at the top of the sheet and a delete removes one, so
     * position is not stable while the viewer is open — without this, rendering a
     * frame again would silently slide the viewer onto its neighbour.
     */
    var anchor by remember { mutableStateOf(captures.getOrNull(startIndex)?.stem) }

    LaunchedEffect(pagerState.currentPage) {
        captures.getOrNull(pagerState.currentPage)?.let { anchor = it.stem }
        // Opening a different frame is a different decision; neither panel should
        // carry across a swipe.
        regrading = false
        confirmingDelete = false
    }

    LaunchedEffect(captures) {
        val index = captures.indexOfFirst { it.stem == anchor }
        when {
            index < 0 ->
                // The anchored frame was deleted. Whatever has taken its place is
                // what the viewer is now showing, so follow that rather than jump.
                anchor = captures.getOrNull(pagerState.currentPage)?.stem
            index != pagerState.currentPage -> pagerState.scrollToPage(index)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChromeIconButton(onClick = onBack, active = false) { BackIcon() }
            LatentText(
                text = current?.let { formatCapturedAt(it.capturedAt) }.orEmpty(),
                style = LatentType.Body,
                color = LatentInk.Medium,
            )
            Spacer(Modifier.weight(1f))
            LatentText(
                text = "${pagerState.currentPage + 1}/${captures.size}",
                style = LatentType.Readout,
                color = LatentInk.Soft,
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        // Decoded to the width it is drawn at, not to the file's own resolution: a
        // 3060px square costs 37MB as a bitmap and the pager holds three of them.
        val viewerPx = with(density) { screenWidth().roundToPx() }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val record = captures[page]
            val uri = remember(record.outputUri) { Uri.parse(record.outputUri) }
            val bitmap by rememberThumbnail(uri, viewerPx)

            // A full-width frame takes a moment to decode. Fading it up out of the
            // empty square is the honest version of that wait: the alternative is a
            // grey box that abruptly becomes a photograph.
            val arrival by animateFloatAsState(
                targetValue = if (bitmap != null) 1f else 0f,
                animationSpec = Motion.enter(),
                label = "frameArrival",
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(LatentInk.Wash),
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = arrival },
                    )
                }
            }
        }

        current?.let { record ->
            FrameFacts(record)
        }

        Spacer(Modifier.weight(1f))

        AnimatedVisibility(
            visible = regrading && current != null,
            enter = fadeIn(Motion.enter()) + expandVertically(Motion.resize()),
            exit = fadeOut(Motion.leave()) + shrinkVertically(Motion.resize()),
        ) {
            current?.let { record ->
                RegradePicker(
                    recipes = recipes,
                    currentName = record.recipeName,
                    onPick = { recipe ->
                        onRegrade(record, recipe)
                        regrading = false
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = confirmingDelete && current != null,
            enter = fadeIn(Motion.enter()) + expandVertically(Motion.resize()),
            exit = fadeOut(Motion.leave()) + shrinkVertically(Motion.resize()),
        ) {
            current?.let { record ->
                // The original only goes with it when no other render still points at
                // it — a re-grade and the shot it came from share one source file.
                val sourceShared = record.sourceUri != null &&
                    captures.count { it.sourceUri == record.sourceUri } > 1

                DeleteConfirm(
                    hasOriginal = record.canRegrade && !sourceShared,
                    onConfirm = {
                        confirmingDelete = false
                        onDelete(record)
                    },
                    onCancel = { confirmingDelete = false },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A shot saved without its original has nothing to re-render from, so the
            // control is absent rather than present and inert.
            if (current?.canRegrade == true) {
                ChromeIconButton(
                    onClick = {
                        regrading = !regrading
                        confirmingDelete = false
                    },
                    active = regrading,
                ) { RegradeIcon() }
            }
            ChromeIconButton(
                onClick = { current?.let(onShare) },
                active = false,
            ) { ShareIcon() }
            ChromeIconButton(
                onClick = {
                    confirmingDelete = !confirmingDelete
                    regrading = false
                },
                active = confirmingDelete,
            ) { DeleteIcon(tint = if (confirmingDelete) LatentInk.Warn else LatentInk.Strong) }
        }

        Spacer(Modifier.height(12.dp))
    }
}

/** What this frame is: the recipe it was rendered with and what else survives of it. */
@Composable
private fun FrameFacts(record: CaptureRecord, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LatentText(
            text = record.recipeName.uppercase(),
            style = LatentType.LabelMedium,
            color = LatentInk.Full,
        )
        Spacer(Modifier.weight(1f))
        if (record.regradedFrom != null) Fact("RE-GRADE", LatentInk.Lock)
        if (record.canRegrade) Fact("RAW SRC") else Fact("FINAL", LatentInk.Soft)
        if (record.dngUri != null) Fact("DNG")
    }
}

/** The frame is full-bleed square, so the viewport's width is its side. */
@Composable
private fun screenWidth(): androidx.compose.ui.unit.Dp =
    androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp

@Composable
private fun Fact(label: String, tint: Color = LatentInk.Medium) {
    LatentText(
        text = label,
        style = LatentType.LabelSmall,
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(LatentInk.Wash)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

/**
 * Pick the recipe to render this frame under again. It writes a new file rather than
 * replacing the old one — the original is untouched, so both renders are equally
 * valid outputs of it and neither has to win.
 */
@Composable
private fun RegradePicker(
    recipes: List<Recipe>,
    currentName: String,
    onPick: (Recipe) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LatentText(
            text = "RENDER AGAIN AS",
            style = LatentType.LabelSmall,
            color = LatentInk.Soft,
            modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(recipes, key = { it.id }) { recipe ->
                val isCurrent = recipe.name == currentName
                Row(
                    modifier = Modifier
                        .tactile { onPick(recipe) }
                        .clip(RoundedCornerShape(6.dp))
                        .background(LatentInk.Wash)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isCurrent) CheckIcon(size = 12.dp, tint = LatentInk.Soft)
                    LatentText(
                        text = recipe.name.uppercase(),
                        style = LatentType.LabelMedium,
                        color = if (isCurrent) LatentInk.Soft else LatentInk.Full,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun DeleteConfirm(
    hasOriginal: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LatentText(
            // Saying what goes with it, because the original is the thing that cannot
            // be recovered and the graded frame is the thing you are looking at.
            text = if (hasOriginal) {
                "DELETE THIS FRAME AND ITS ORIGINAL?"
            } else {
                "DELETE THIS FRAME?"
            },
            style = LatentType.LabelSmall,
            color = LatentInk.Warn,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConfirmChip("CANCEL", LatentInk.Medium, onCancel)
            ConfirmChip("DELETE", LatentInk.Warn, onConfirm)
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * Both sides of an irreversible choice, weighted the same.
 *
 * Deletion here takes the colour original with it, so the destructive option is not
 * made easier to hit than the way out — only redder.
 */
@Composable
private fun ConfirmChip(label: String, tint: Color, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    LatentText(
        text = label,
        style = LatentType.LabelMedium,
        color = tint,
        modifier = Modifier
            .tactile {
                Feedback.confirm(haptics)
                onClick()
            }
            .clip(RoundedCornerShape(6.dp))
            .background(LatentInk.Wash)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    )
}
