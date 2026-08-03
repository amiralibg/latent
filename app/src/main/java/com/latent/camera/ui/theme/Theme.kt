package com.latent.camera.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
}

private val LatentColors = darkColorScheme(
    primary = LatentInk.Full,
    onPrimary = Color.Black,
    secondary = LatentInk.Medium,
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = LatentInk.Full,
    surface = Color(0xFF0A0A0A),
    onSurface = LatentInk.Full,
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = LatentInk.Medium,
    outline = LatentInk.Faint,
    secondaryContainer = LatentInk.Wash,
    onSecondaryContainer = LatentInk.Full,
    error = LatentInk.Warn,
    onError = Color.Black,
)

private val LatentType = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 88.sp,
        letterSpacing = (-1.5).sp,
        color = LatentInk.Full,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        letterSpacing = (-0.2).sp,
        color = LatentInk.Strong,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 1.2.sp,
        color = LatentInk.Strong,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
        color = LatentInk.Medium,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        color = LatentInk.Soft,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 0.1.sp,
        color = LatentInk.Medium,
    ),
    /** Instrument readout — ISO, shutter, EV. */
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.6.sp,
        color = LatentInk.Strong,
    ),
)

@Composable
fun LatentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LatentColors,
        typography = LatentType,
        content = content,
    )
}
