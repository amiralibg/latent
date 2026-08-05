package com.latent.camera.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Detents per full travel. Coarse enough to feel, fine enough not to buzz. */
private const val SliderDetents = 40

/**
 * The look sliders.
 *
 * A rail with a bar riding it, not a Material track with a puck: the fill starts at
 * the value's *neutral* point rather than at the left end, so a tone control that can
 * go both ways shows which way it has gone. Contrast at -0.4 fills leftward from the
 * middle; grain at 0.2 fills rightward from nothing. That distinction is the whole
 * reason to draw this by hand.
 */
@Composable
fun LatentSlider(
    value: Float,
    from: Float,
    to: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    var widthPx by remember { mutableIntStateOf(0) }
    var lastDetent by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var dragging by remember { mutableStateOf(false) }
    var fingerX by remember { mutableFloatStateOf(0f) }

    val span = (to - from).takeIf { abs(it) > 1e-6f } ?: 1f
    val fraction = ((value - from) / span).coerceIn(0f, 1f)

    // Bipolar ranges fill from the middle; everything else from the left.
    val originFraction = if (from < 0f && to > 0f) ((0f - from) / span) else 0f

    val thumbHeight by animateDpAsState(
        targetValue = if (dragging) 22.dp else 17.dp,
        animationSpec = Motion.release(),
        label = "sliderThumbHeight",
    )
    val railAlpha by animateFloatAsState(
        targetValue = if (dragging) 1f else 0.75f,
        animationSpec = Motion.state(),
        label = "sliderRailAlpha",
    )

    fun emit(x: Float) {
        if (widthPx <= 0) return
        val next = from + (x / widthPx).coerceIn(0f, 1f) * span
        val detent = ((next - from) / span * SliderDetents).roundToInt()
        if (detent != lastDetent) {
            lastDetent = detent
            if (detent == 0 || detent == SliderDetents) Feedback.limit(haptics)
            else Feedback.tick(haptics)
        }
        onChange(next)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width }
            // `draggable` rather than a raw horizontal drag detector, because these
            // sliders live inside a scrolling sheet: it waits for horizontal slop and
            // yields to the vertical scroll, so flicking the panel past a slider does
            // not drag the slider with it. That bug is invisible in a screenshot and
            // ruinous in use — you scroll to reach Grain and arrive with the contrast
            // moved.
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = enabled,
                state = rememberDraggableState { delta ->
                    fingerX += delta
                    emit(fingerX)
                },
                onDragStarted = { position ->
                    dragging = true
                    fingerX = position.x
                    emit(position.x)
                },
                onDragStopped = {
                    dragging = false
                    Feedback.release(haptics)
                },
            )
            // Tap to jump. `draggable` waits for slop, so a tap never reaches it and
            // the two gesture handlers do not fight over the same pointer.
            .pointerInput(enabled, widthPx, from, to) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    fingerX = offset.x
                    emit(offset.x)
                    Feedback.confirm(haptics)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(thumbHeight)) {
            val railY = size.height / 2f
            val railThickness = 1.5.dp.toPx()
            val thumbWidth = 3.dp.toPx()
            val travel = size.width - thumbWidth
            val alpha = if (enabled) railAlpha else 0.3f

            drawRoundRect(
                color = LatentInk.Faint.copy(alpha = LatentInk.Faint.alpha * alpha),
                topLeft = Offset(0f, railY - railThickness / 2f),
                size = Size(size.width, railThickness),
                cornerRadius = CornerRadius(railThickness / 2f),
            )

            // The travelled part. Drawn from the origin, so which direction the value
            // has been taken is readable without looking at the number.
            val originX = originFraction * travel + thumbWidth / 2f
            val valueX = fraction * travel + thumbWidth / 2f
            drawRoundRect(
                color = LatentInk.Strong.copy(alpha = LatentInk.Strong.alpha * alpha),
                topLeft = Offset(minOf(originX, valueX), railY - railThickness / 2f),
                size = Size(abs(valueX - originX), railThickness),
                cornerRadius = CornerRadius(railThickness / 2f),
            )

            if (originFraction > 0.01f) {
                drawRoundRect(
                    color = LatentInk.Soft,
                    topLeft = Offset(originX - 0.5.dp.toPx(), railY - 4.dp.toPx()),
                    size = Size(1.dp.toPx(), 8.dp.toPx()),
                )
            }

            drawRoundRect(
                color = if (enabled) LatentInk.Full else LatentInk.Soft,
                topLeft = Offset(fraction * travel, 0f),
                size = Size(thumbWidth, size.height),
                cornerRadius = CornerRadius(thumbWidth / 2f),
            )
        }
    }
}

/**
 * A labelled slider with its value beside it — the row the recipe editor is built out
 * of. The readout is monospaced so a dragged value does not shuffle the layout.
 */
@Composable
fun LatentSliderRow(
    label: String,
    value: Float,
    from: Float,
    to: Float,
    modifier: Modifier = Modifier,
    format: (Float) -> String = ::formatSliderValue,
    onChange: (Float) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LatentText(
            text = label,
            style = LatentType.Body,
            color = LatentInk.Medium,
            maxLines = 1,
            modifier = Modifier.width(96.dp),
        )
        LatentSlider(
            value = value.coerceIn(minOf(from, to), maxOf(from, to)),
            from = from,
            to = to,
            onChange = onChange,
            modifier = Modifier.weight(1f),
        )
        LatentText(
            text = format(value),
            style = LatentType.Readout,
            color = LatentInk.Strong,
            align = TextAlign.End,
            maxLines = 1,
            modifier = Modifier
                .width(52.dp)
                .padding(start = 8.dp),
        )
    }
}

/** Two decimals below ten, otherwise the readout jitters more than it informs. */
fun formatSliderValue(value: Float): String =
    if (abs(value) >= 10f) {
        value.roundToInt().toString()
    } else {
        "%.2f".format(value)
    }

/**
 * A text field with a rule under it. The rule is the only affordance: a box would be
 * the loudest thing in a panel whose job is to not compete with the picture behind it.
 */
@Composable
fun LatentTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val ruleColor by animateColorAsState(
        targetValue = if (focused) LatentInk.Full else LatentInk.Faint,
        animationSpec = Motion.state(),
        label = "fieldRule",
    )
    val ruleHeight by animateDpAsState(
        targetValue = if (focused) 1.5.dp else 1.dp,
        animationSpec = Motion.state(),
        label = "fieldRuleHeight",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        LatentText(
            text = label.uppercase(),
            style = LatentType.LabelSmall,
            color = if (focused) LatentInk.Medium else LatentInk.Soft,
        )
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LatentType.Title.copy(color = LatentInk.Full),
            cursorBrush = SolidColor(LatentInk.Full),
            interactionSource = interactionSource,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(ruleHeight)
                .background(ruleColor),
        )
    }
}

/**
 * A word you can press. Outlined rather than filled — a filled button in a black
 * viewfinder app is a lamp.
 */
@Composable
fun LatentButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LatentInk.Full,
    filled: Boolean = false,
) {
    Box(
        modifier = modifier
            .tactile(onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(if (filled) tint else Color.Transparent)
            .border(1.dp, if (filled) Color.Transparent else tint.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        LatentText(
            text = label.uppercase(),
            style = LatentType.LabelMedium,
            color = if (filled) Color.Black else tint,
        )
    }
}

/**
 * The one small toggle in the app: aids, mix presets, manual parameters, confirmations.
 *
 * Selection is a wash behind the label and full-strength ink in it, both animated —
 * a chip that changes instantly reads as a redraw, and a chip that fades reads as an
 * answer to the tap.
 */
@Composable
fun LatentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = LatentType.LabelSmall,
    tint: Color = LatentInk.Full,
    idleTint: Color = LatentInk.Soft,
    horizontalPadding: androidx.compose.ui.unit.Dp = 11.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 8.dp,
    onLongClick: (() -> Unit)? = null,
) {
    val background by animateColorAsState(
        targetValue = if (selected) LatentInk.Wash else Color.Transparent,
        animationSpec = Motion.state(),
        label = "chipBackground",
    )
    val content by animateColorAsState(
        targetValue = if (selected) tint else idleTint,
        animationSpec = Motion.state(),
        label = "chipContent",
    )

    Box(
        modifier = modifier
            .tactile(onLongClick = onLongClick, onClick = onClick)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        LatentText(text = label, style = style, color = content)
    }
}

/** A hairline between sections. */
@Composable
fun LatentDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LatentInk.Faint.copy(alpha = 0.5f)),
    )
}
