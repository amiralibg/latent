package com.latent.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.latent.camera.settings.SaveMode
import com.latent.camera.ui.theme.LatentInk

/**
 * Everything that is a setting rather than a shooting control.
 *
 * There is very little here by design — the app has no account, no sync and no
 * quality presets, so what is left is the one genuinely consequential choice: how
 * much of each frame survives the shutter.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            SectionLabel("What the shutter saves")

            Choice(
                title = "Black and white only",
                detail = "One file per frame. Nothing to re-grade from later.",
                selected = saveMode == SaveMode.BwOnly,
                onClick = { onSaveMode(SaveMode.BwOnly) },
            )
            Choice(
                title = "Black and white + colour original",
                detail = "The default. Keeps the untouched frame so any shot can be rendered again.",
                selected = saveMode == SaveMode.BwAndOriginal,
                onClick = { onSaveMode(SaveMode.BwAndOriginal) },
            )
            // Absent rather than disabled where the camera cannot produce one: a
            // setting that is visible and inert reads as a bug in the app.
            if (dngSupported) {
                Choice(
                    title = "Add a DNG",
                    detail = "Also writes the sensor RAW. Slower, and several times the storage.",
                    selected = saveMode == SaveMode.BwOriginalAndDng,
                    onClick = { onSaveMode(SaveMode.BwOriginalAndDng) },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 14.dp))

            SectionLabel("Sharing")

            Choice(
                title = "White border",
                detail = "Mounts the frame on white when you share it. The saved file is never touched.",
                selected = exportBorder,
                onClick = { onExportBorder(!exportBorder) },
            )

            if (hardwareLevel != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "CAMERA2 $hardwareLevel" +
                        if (dngSupported) " · RAW" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = LatentInk.Faint,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LatentInk.Soft,
        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
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
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(if (selected) LatentInk.Full else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                CheckIcon(size = 11.dp, tint = Color.Black)
            } else {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(LatentInk.Faint),
                )
            }
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) LatentInk.Full else LatentInk.Strong,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = LatentInk.Soft,
            )
        }
    }
}
