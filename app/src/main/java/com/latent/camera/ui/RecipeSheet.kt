package com.latent.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.latent.camera.look.ChannelMix
import com.latent.camera.look.Recipe
import com.latent.camera.ui.theme.LatentInk
import kotlin.math.roundToInt

/**
 * Editing for the loaded recipe.
 *
 * One slider per stage, in the order the shader applies them, because a look is
 * built in that order and a control panel that scrambles it teaches the wrong mental
 * model. Every change reaches the viewfinder on the next frame — nothing here has an
 * apply button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeSheet(
    recipe: Recipe,
    onChange: (Recipe) -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            NameField(name = recipe.name, onRename = onRename)

            Actions(
                onDuplicate = onDuplicate,
                onDelete = onDelete,
                onReset = onReset,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Stage("1 · Filter")
            // The mix is the contrast filter. Presets are named triples, and the
            // three weights underneath are the same thing spelled out.
            MixPresets(active = recipe.channelMix) { onChange(recipe.copy(channelMix = it)) }
            LookSlider("Red", recipe.channelMix.r, 0f, 1f) {
                onChange(recipe.copy(channelMix = recipe.channelMix.copy(r = it)))
            }
            LookSlider("Green", recipe.channelMix.g, 0f, 1f) {
                onChange(recipe.copy(channelMix = recipe.channelMix.copy(g = it)))
            }
            LookSlider("Blue", recipe.channelMix.b, 0f, 1f) {
                onChange(recipe.copy(channelMix = recipe.channelMix.copy(b = it)))
            }

            Stage("2 · Tone")
            LookSlider("Lift", recipe.lift, -0.2f, 0.4f) { onChange(recipe.copy(lift = it)) }
            LookSlider("Gamma", recipe.gamma, 0.4f, 2.5f) { onChange(recipe.copy(gamma = it)) }
            LookSlider("Gain", recipe.gain, 0.5f, 2f) { onChange(recipe.copy(gain = it)) }
            LookSlider("Contrast", recipe.contrast, -1f, 1f) {
                onChange(recipe.copy(contrast = it))
            }

            Stage("3 · Clarity")
            LookSlider("Amount", recipe.clarity, -1f, 1.5f) { onChange(recipe.copy(clarity = it)) }
            LookSlider("Radius", recipe.clarityRadius, 0.004f, 0.12f) {
                onChange(recipe.copy(clarityRadius = it))
            }

            Stage("4 · Halation")
            LookSlider("Amount", recipe.halation, 0f, 1f) { onChange(recipe.copy(halation = it)) }
            LookSlider("Threshold", recipe.halationThreshold, 0.3f, 0.98f) {
                onChange(recipe.copy(halationThreshold = it))
            }
            LookSlider("Radius", recipe.halationRadius, 0.01f, 0.25f) {
                onChange(recipe.copy(halationRadius = it))
            }

            Stage("5 · Grain")
            LookSlider("Amount", recipe.grain, 0f, 0.6f) { onChange(recipe.copy(grain = it)) }
            LookSlider("Size", recipe.grainSize, 0.5f, 8f) { onChange(recipe.copy(grainSize = it)) }

            Stage("6 · Toning")
            LookSlider("Selenium ↔ Sepia", recipe.toning, -1f, 1f) {
                onChange(recipe.copy(toning = it))
            }

            Stage("7 · Vignette")
            LookSlider("Amount", recipe.vignette, 0f, 1f) { onChange(recipe.copy(vignette = it)) }
        }
    }
}

@Composable
private fun NameField(name: String, onRename: (String) -> Unit) {
    var text by remember(name) { mutableStateOf(name) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onRename(it)
        },
        label = { Text("Recipe") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

@Composable
private fun Actions(onDuplicate: () -> Unit, onDelete: () -> Unit, onReset: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = onDuplicate) { Text("Duplicate") }
        TextButton(onClick = onReset) { Text("Reset") }
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}

@Composable
private fun Stage(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun MixPresets(active: ChannelMix, onPick: (ChannelMix) -> Unit) {
    val presets = listOf(
        "Neutral" to ChannelMix.Neutral,
        "Yellow" to ChannelMix.Yellow,
        "Orange" to ChannelMix.Orange,
        "Red" to ChannelMix.Red,
        "Green" to ChannelMix.Green,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        presets.forEach { (label, mix) ->
            val selected = mix == active
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) LatentInk.Full else LatentInk.Soft,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (selected) LatentInk.Wash else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onPick(mix) },
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun LookSlider(
    label: String,
    value: Float,
    from: Float,
    to: Float,
    onChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(112.dp),
        )
        Slider(
            value = value.coerceIn(from, to),
            onValueChange = onChange,
            valueRange = from..to,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = format(value),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier
                .width(48.dp)
                .padding(start = 8.dp),
        )
    }
}

/** Two decimals below ten, otherwise the readout jitters more than it informs. */
private fun format(value: Float): String =
    if (kotlin.math.abs(value) >= 10f) {
        value.roundToInt().toString()
    } else {
        ((value * 100).roundToInt() / 100f).toString()
    }
