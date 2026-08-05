package com.latent.camera.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * One motion vocabulary for the whole app.
 *
 * The rule this encodes: a control answers the finger immediately and settles slowly.
 * Everything that acknowledges a touch is under 120ms, because past that the touch and
 * the response stop feeling like one event; everything that *reveals* something is
 * springy and a little slower, because that motion is what says where the thing came
 * from. Nothing here fades in over 300ms — this is a camera, and a camera that makes
 * you wait for its chrome has already cost you the frame.
 *
 * The specs are functions rather than values so the same curve can drive a float, a
 * colour, a Dp or a size. There is no second set of numbers for "the colour version".
 */
object Motion {

    /** Fast out, hard stop. The house curve. */
    val Sharp: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Leaving. Slower than arriving, so a dismissal reads as deliberate. */
    val Exit: Easing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

    /** Touch acknowledgement — the way down. */
    fun <T> press(): FiniteAnimationSpec<T> = tween(80, easing = Sharp)

    /** Touch release. Springy: this is where the physical quality comes from. */
    fun <T> release(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** A value changed and the chrome has to catch up. */
    fun <T> state(): FiniteAnimationSpec<T> = tween(160, easing = Sharp)

    /** Something appeared. */
    fun <T> enter(): FiniteAnimationSpec<T> = tween(140, easing = Sharp)

    /** Something left. Overlays hang around a beat so the eye can follow them out. */
    fun <T> leave(): FiniteAnimationSpec<T> = tween(260, easing = Exit)

    /** Panels and sheets travelling under a thumb. */
    fun <T> panel(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** A thing that lands — the reticle, a new thumbnail, the selected recipe. */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.5f,
        stiffness = Spring.StiffnessMedium,
    )

    /** Layout growing or shrinking (the aids panel, the manual deck). */
    fun <T> resize(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMediumLow,
    )
}
