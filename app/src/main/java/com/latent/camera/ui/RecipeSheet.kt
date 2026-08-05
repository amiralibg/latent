package com.latent.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.latent.camera.look.ChannelMix
import com.latent.camera.look.GrainPreset
import com.latent.camera.look.Recipe
import com.latent.camera.ui.theme.Feedback
import com.latent.camera.ui.theme.LatentChip
import com.latent.camera.ui.theme.LatentDivider
import com.latent.camera.ui.theme.LatentInk
import com.latent.camera.ui.theme.LatentSheet
import com.latent.camera.ui.theme.LatentSliderRow
import com.latent.camera.ui.theme.LatentText
import com.latent.camera.ui.theme.LatentTextField
import com.latent.camera.ui.theme.LatentType

/**
 * Editing for the loaded recipe.
 *
 * One slider per stage, in the order the shader applies them, because a look is
 * built in that order and a control panel that scrambles it teaches the wrong mental
 * model. Every change reaches the viewfinder on the next frame — nothing here has an
 * apply button, and the sheet is a layer rather than a window so the frame behind it
 * keeps grading live while you drag.
 */
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
    val haptics = LocalHapticFeedback.current

    // Leave the top third of the screen clear: the whole point of grading here is
    // watching the square change, so the sheet must never cover it entirely.
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.66f

    LatentSheet(onDismiss = onDismiss) {
        // Local, because a rename round-trips through Room and the recipe would
        // arrive back a keystroke behind the finger.
        var typedName by remember(recipe.id) { mutableStateOf(recipe.name) }

        // The header sits outside the scroll. Seven stages is a long way to travel,
        // and a panel that scrolls its own title away leaves you adjusting sliders
        // with no reminder of which recipe you are changing.
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            LatentTextField(
                value = typedName,
                onValueChange = {
                    typedName = it
                    onRename(it)
                },
                label = "Recipe",
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LatentChip(
                    label = "DUPLICATE",
                    selected = false,
                    idleTint = LatentInk.Medium,
                    onClick = {
                        Feedback.confirm(haptics)
                        onDuplicate()
                    },
                )
                LatentChip(
                    label = "RESET",
                    selected = false,
                    idleTint = LatentInk.Medium,
                    onClick = {
                        Feedback.confirm(haptics)
                        onReset()
                    },
                )
                // Pushed away from the other two on purpose: the one irreversible
                // action here should not sit a thumb's width from "duplicate".
                Spacer(Modifier.weight(1f))
                LatentChip(
                    label = "DELETE",
                    selected = false,
                    idleTint = LatentInk.Warn,
                    onClick = {
                        Feedback.confirm(haptics)
                        onDelete()
                    },
                )
            }

            Spacer(Modifier.height(14.dp))
        }

        LatentDivider()

        Column(
            modifier = Modifier
                .heightIn(max = maxSheetHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Stage("1", "Filter")
            // The mix is the contrast filter. Presets are named triples, and the
            // three weights underneath are the same thing spelled out.
            ChipRow {
                MixPresets.forEach { (label, mix) ->
                    LatentChip(
                        label = label.uppercase(),
                        selected = mix == recipe.channelMix,
                        onClick = { onChange(recipe.copy(channelMix = mix)) },
                    )
                }
            }
            LatentSliderRow("Red", recipe.channelMix.r, 0f, 1f) {
                onChange(recipe.copy(channelMix = recipe.channelMix.copy(r = it)))
            }
            LatentSliderRow("Green", recipe.channelMix.g, 0f, 1f) {
                onChange(recipe.copy(channelMix = recipe.channelMix.copy(g = it)))
            }
            LatentSliderRow("Blue", recipe.channelMix.b, 0f, 1f) {
                onChange(recipe.copy(channelMix = recipe.channelMix.copy(b = it)))
            }

            Stage("2", "Tone")
            LatentSliderRow("Lift", recipe.lift, -0.2f, 0.4f) {
                onChange(recipe.copy(lift = it))
            }
            LatentSliderRow("Gamma", recipe.gamma, 0.4f, 2.5f) {
                onChange(recipe.copy(gamma = it))
            }
            LatentSliderRow("Gain", recipe.gain, 0.5f, 2f) {
                onChange(recipe.copy(gain = it))
            }
            LatentSliderRow("Contrast", recipe.contrast, -1f, 1f) {
                onChange(recipe.copy(contrast = it))
            }

            Stage("3", "Clarity")
            LatentSliderRow("Amount", recipe.clarity, -1f, 1.5f) {
                onChange(recipe.copy(clarity = it))
            }
            LatentSliderRow("Radius", recipe.clarityRadius, 0.004f, 0.12f) {
                onChange(recipe.copy(clarityRadius = it))
            }

            Stage("4", "Halation")
            LatentSliderRow("Amount", recipe.halation, 0f, 1f) {
                onChange(recipe.copy(halation = it))
            }
            LatentSliderRow("Threshold", recipe.halationThreshold, 0.3f, 0.98f) {
                onChange(recipe.copy(halationThreshold = it))
            }
            LatentSliderRow("Radius", recipe.halationRadius, 0.01f, 0.25f) {
                onChange(recipe.copy(halationRadius = it))
            }

            Stage("5", "Grain")
            // Named stocks first, then the two numbers behind them. Picking "Coarse"
            // is the decision; moving Size to 3.9 afterwards is a preference.
            ChipRow {
                GrainPreset.entries.forEach { preset ->
                    LatentChip(
                        label = preset.label.uppercase(),
                        selected = recipe.grainPreset == preset,
                        onClick = { onChange(recipe.withGrain(preset)) },
                    )
                }
            }
            LatentSliderRow("Amount", recipe.grain, 0f, 0.6f) {
                onChange(recipe.copy(grain = it))
            }
            LatentSliderRow(
                label = "Size",
                value = recipe.grainSize,
                from = 1f,
                to = 8f,
                format = { "%.1f".format(it) },
                onChange = { onChange(recipe.copy(grainSize = it)) },
            )
            LatentText(
                text = "Grain is measured against the frame, not the pixel grid — " +
                    "what the viewfinder shows is what the file gets.",
                style = LatentType.Body,
                color = LatentInk.Soft,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )

            Stage("6", "Toning")
            LatentSliderRow("Selenium — Sepia", recipe.toning, -1f, 1f) {
                onChange(recipe.copy(toning = it))
            }

            Stage("7", "Vignette")
            LatentSliderRow("Amount", recipe.vignette, 0f, 1f) {
                onChange(recipe.copy(vignette = it))
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

private val MixPresets = listOf(
    "Neutral" to ChannelMix.Neutral,
    "Yellow" to ChannelMix.Yellow,
    "Orange" to ChannelMix.Orange,
    "Red" to ChannelMix.Red,
    "Green" to ChannelMix.Green,
)

/**
 * The stage number is not decoration — it is the order the shader runs in.
 *
 * The rule running off to the right edge is what turns seven consecutive slider blocks
 * into seven sections. Without it the panel is one long undifferentiated list and you
 * navigate it by reading labels rather than by shape.
 */
@Composable
private fun Stage(number: String, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LatentText(number, style = LatentType.LabelSmall, color = LatentInk.Faint)
        LatentText(title.uppercase(), style = LatentType.LabelSmall, color = LatentInk.Medium)
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(LatentInk.Faint.copy(alpha = 0.45f)),
        )
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}
