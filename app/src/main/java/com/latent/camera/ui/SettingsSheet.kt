package com.latent.camera.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.latent.camera.settings.SaveMode
import com.latent.camera.ui.theme.Feedback
import com.latent.camera.ui.theme.LatentDivider
import com.latent.camera.ui.theme.LatentInk
import com.latent.camera.ui.theme.LatentSheet
import com.latent.camera.ui.theme.LatentText
import com.latent.camera.ui.theme.LatentType
import com.latent.camera.ui.theme.Motion
import com.latent.camera.ui.theme.tactile

/**
 * Everything that is a setting rather than a shooting control.
 *
 * There is very little here by design — the app has no account, no sync and no
 * quality presets, so what is left is the one genuinely consequential choice: how
 * much of each frame survives the shutter.
 */
@Composable
fun SettingsSheet(
    saveMode: SaveMode,
    exportBorder: Boolean,
    dngSupported: Boolean,
    hardwareLevel: String?,
    onSaveMode: (SaveMode) -> Unit,
    onExportBorder: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    LatentSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            SectionLabel("What the shutter saves")

            Choice(
                title = "Black and white only",
                detail = "One file per frame. Nothing to re-grade from later.",
                selected = saveMode == SaveMode.BwOnly,
                onClick = {
                    Feedback.toggle(haptics, true)
                    onSaveMode(SaveMode.BwOnly)
                },
            )
            Choice(
                title = "Black and white + colour original",
                detail = "The default. Keeps the untouched frame so any shot can be rendered again.",
                selected = saveMode == SaveMode.BwAndOriginal,
                onClick = {
                    Feedback.toggle(haptics, true)
                    onSaveMode(SaveMode.BwAndOriginal)
                },
            )
            // Absent rather than disabled where the camera cannot produce one: a
            // setting that is visible and inert reads as a bug in the app.
            if (dngSupported) {
                Choice(
                    title = "Add a DNG",
                    detail = "Also writes the sensor RAW. Slower, and several times the storage.",
                    selected = saveMode == SaveMode.BwOriginalAndDng,
                    onClick = {
                        Feedback.toggle(haptics, true)
                        onSaveMode(SaveMode.BwOriginalAndDng)
                    },
                )
            }

            Spacer(Modifier.height(14.dp))
            LatentDivider()

            SectionLabel("Sharing")

            Choice(
                title = "White border",
                detail = "Mounts the frame on white when you share it. The saved file is never touched.",
                selected = exportBorder,
                onClick = {
                    Feedback.toggle(haptics, !exportBorder)
                    onExportBorder(!exportBorder)
                },
            )

            if (hardwareLevel != null) {
                Spacer(Modifier.height(20.dp))
                LatentText(
                    text = "CAMERA2 $hardwareLevel" + if (dngSupported) " · RAW" else "",
                    style = LatentType.LabelSmall,
                    color = LatentInk.Faint,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    LatentText(
        text = text.uppercase(),
        style = LatentType.LabelSmall,
        color = LatentInk.Soft,
        modifier = Modifier.padding(top = 16.dp, bottom = 10.dp),
    )
}

@Composable
private fun Choice(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tactile(pressScale = 0.985f, onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SelectionDot(selected = selected, modifier = Modifier.padding(top = 3.dp))
        Column {
            LatentText(
                text = title,
                style = LatentType.Title,
                color = if (selected) LatentInk.Full else LatentInk.Strong,
            )
            Spacer(Modifier.height(3.dp))
            LatentText(text = detail, style = LatentType.Body, color = LatentInk.Soft)
        }
    }
}

/**
 * The mark that says which one is on. A ring that fills from the centre rather than a
 * checkmark that pops in — the fill is what makes a set of choices read as one control
 * whose state moved, instead of three independent things that happen to be adjacent.
 */
@Composable
fun SelectionDot(selected: Boolean, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 16.dp) {
    val fill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = Motion.settle(),
        label = "selectionFill",
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, if (selected) Color.Transparent else LatentInk.Faint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = fill
                    scaleY = fill
                }
                .clip(CircleShape)
                .background(LatentInk.Full),
        )
        if (fill > 0.6f) {
            Box(
                Modifier.graphicsLayer { alpha = (fill - 0.6f) / 0.4f },
            ) {
                CheckIcon(size = size * 0.68f, tint = Color.Black)
            }
        }
    }
}
