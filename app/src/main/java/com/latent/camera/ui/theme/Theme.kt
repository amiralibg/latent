package com.latent.camera.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The viewfinder is a black surround for a square image. There is no light theme and
 * no accent colour — anything tinted would sit next to the frame and lie about tone.
 *
 * Hierarchy is alpha and weight only. The meter uses monospace so ISO and shutter
 * read like a body readout, not app chrome.
 */
object LatentInk {
    val Full = Color.White
    val Strong = Color.White.copy(alpha = 0.92f)
    val Medium = Color.White.copy(alpha = 0.62f)
    val Soft = Color.White.copy(alpha = 0.38f)
    val Faint = Color.White.copy(alpha = 0.18f)
    val Wash = Color.White.copy(alpha = 0.10f)
    val Lock = Color(0xFFE8C547)
    val Warn = Color(0xFFFF6B6B)

    /** Panels that sit over the viewfinder. Near-black, never grey enough to read as a surface. */
    val Panel = Color(0xFF0A0A0A)
    val Scrim = Color.Black.copy(alpha = 0.72f)
}

/**
 * The type scale, as plain values rather than a Material `Typography`.
 *
 * Nothing here is themed or overridable at runtime: a camera that restyles itself is
 * a camera you have to re-learn, and the whole surface is six styles wide.
 */
object LatentType {

    /** The countdown, and nothing else. */
    val Display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 88.sp,
        letterSpacing = (-1.5).sp,
    )

    val Title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        letterSpacing = (-0.2).sp,
    )

    /** Uppercase strip labels — recipe names, section heads. */
    val Label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 1.2.sp,
    )

    val LabelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
    )

    val LabelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
    )

    val Body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 0.1.sp,
    )

    /** Instrument readout — ISO, shutter, EV, frame counts. */
    val Readout = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.6.sp,
    )
}

/** Inherited by [LatentText] when it is not given one. */
val LocalTextStyle = compositionLocalOf { LatentType.Body }

/** Inherited by [LatentText] and the icons. */
val LocalContentColor = staticCompositionLocalOf { LatentInk.Strong }

/**
 * The whole theme. It provides two things and owns no components — every control in
 * this app is drawn here rather than configured out of a component library, because
 * a camera's controls are the product and a themed `Slider` is somebody else's.
 */
@Composable
fun LatentTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTextStyle provides LatentType.Body,
        LocalContentColor provides LatentInk.Strong,
        content = content,
    )
}
