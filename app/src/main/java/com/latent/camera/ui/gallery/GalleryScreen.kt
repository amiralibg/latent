package com.latent.camera.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.latent.camera.data.CaptureRecord
import com.latent.camera.ui.BackIcon
import com.latent.camera.ui.ChromeIconButton
import com.latent.camera.ui.theme.LatentInk
import com.latent.camera.ui.theme.LatentText
import com.latent.camera.ui.theme.LatentType
import com.latent.camera.ui.theme.Motion
import com.latent.camera.ui.theme.tactile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Three across is what makes a square grid read as a contact sheet and not a list. */
private const val COLUMNS = 3

private val SheetGap = 2.dp

/**
 * The contact sheet. Square cells, newest first, app captures only — the same frames
 * the app wrote, in the order it wrote them.
 */
@Composable
fun ContactSheet(
    captures: List<CaptureRecord>,
    onOpen: (CaptureRecord) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val density = LocalDensity.current

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
                text = "FRAMES",
                style = LatentType.LabelMedium,
                color = LatentInk.Strong,
            )
            Spacer(Modifier.weight(1f))
            LatentText(
                text = captures.size.toString(),
                style = LatentType.Readout,
                color = LatentInk.Soft,
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        if (captures.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LatentText(
                    text = "NOTHING SHOT YET",
                    style = LatentType.LabelMedium,
                    color = LatentInk.Soft,
                    align = TextAlign.Center,
                )
            }
            return@Column
        }

        // Cells are decoded at the size they are drawn at, so the sheet measures
        // itself before asking for a single bitmap.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cellPx = with(density) { (maxWidth / COLUMNS).roundToPx() }

            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(SheetGap),
                verticalArrangement = Arrangement.spacedBy(SheetGap),
            ) {
                items(captures, key = { it.stem }) { record ->
                    ContactCell(
                        record = record,
                        cellPx = cellPx,
                        onClick = { onOpen(record) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactCell(record: CaptureRecord, cellPx: Int, onClick: () -> Unit) {
    val uri = remember(record.outputUri) { android.net.Uri.parse(record.outputUri) }
    val bitmap by rememberThumbnail(uri.takeIf { cellPx > 0 }, cellPx.coerceAtLeast(1))

    // Cells decode off the main thread and land whenever they land. Fading each one in
    // as it arrives turns a grid that pops into a sheet that develops.
    val arrival by animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0f,
        animationSpec = Motion.enter(),
        label = "cellArrival",
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .tactile(pressScale = 0.96f, onClick = onClick)
            .background(LatentInk.Wash),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = arrival },
            )
        }
        // A re-grade sits in the sheet next to the render it came from; the dot is
        // what tells you which is which without opening both.
        if (record.regradedFrom != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(LatentInk.Lock),
            )
        }
    }
}

private val stampFormat = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())

fun formatCapturedAt(millis: Long): String = stampFormat.format(Date(millis))
