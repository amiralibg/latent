package com.latent.camera.ui.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * Text, drawn on [BasicText].
 *
 * The Material `Text` carries a colour resolution chain, a `LocalTextStyle` merge and
 * a theme lookup that this app has no use for — there is one palette, it is white at
 * six alphas, and every call site here already knows which one it wants.
 */
@Composable
fun LatentText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    style: TextStyle = LocalTextStyle.current,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align ?: style.textAlign),
        maxLines = maxLines,
        overflow = overflow,
    )
}
