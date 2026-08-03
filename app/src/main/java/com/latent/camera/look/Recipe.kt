package com.latent.camera.look

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A recipe is the shader's uniform set plus a name. Nothing else.
 *
 * Every field below is a uniform in `latent.frag` under exactly this name. That
 * correspondence is what the whole look engine leans on — when you add a stage, add
 * the field and the uniform together and spell them the same.
 *
 * This is also the Room entity. A recipe has no behaviour and no representation the
 * database would need translating, so a separate persistence model would be two
 * places to forget a field rather than one.
 */
@Entity(tableName = "recipes")
data class Recipe(

    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val name: String,

    /** Order in the strip above the shutter. */
    val position: Int = 0,

    // ------------------------------------------------ 1. channel mix
    @Embedded(prefix = "mix_")
    val channelMix: ChannelMix = ChannelMix.Neutral,

    // ------------------------------------------------ 2. tone curve
    /** Offset. Raises the floor — the flat, printed-paper black. */
    val lift: Float = 0f,
    /** Power. Above 1 opens the midtones, below 1 closes them. */
    val gamma: Float = 1f,
    /** Slope. */
    val gain: Float = 1f,
    /** Symmetric S around mid grey. Endpoints stay put. */
    val contrast: Float = 0f,

    // ------------------------------------------------ 3. clarity
    /** Unsharp amount. Negative softens. */
    val clarity: Float = 0f,
    /** Radius as a fraction of the frame, so it survives a resolution change. */
    val clarityRadius: Float = 0.02f,

    // ------------------------------------------------ 4. halation
    val halation: Float = 0f,
    val halationThreshold: Float = 0.75f,
    val halationRadius: Float = 0.05f,

    // ------------------------------------------------ 5. grain
    val grain: Float = 0f,
    /** Grain cell size in pixels of the rendered frame. */
    val grainSize: Float = 1.5f,

    // ------------------------------------------------ 6. toning
    /** -1 cool selenium, 0 neutral, +1 warm sepia. */
    val toning: Float = 0f,

    // ------------------------------------------------ 7. vignette
    val vignette: Float = 0f,
) {
    /** Everything except identity — name, id and position are not part of the look. */
    fun withLookOf(other: Recipe): Recipe = other.copy(
        id = id,
        name = name,
        position = position,
    )

    companion object {
        /** What the app shoots with before the user has made anything of their own. */
        val Default = Recipe(id = "neutral", name = "Neutral", position = 0)
    }
}

/**
 * The `channelMix` vec3 — a contrast filter expressed as red/green/blue weights.
 * Presets are just named triples; the values are adjustable per recipe.
 */
data class ChannelMix(
    @ColumnInfo(name = "r") val r: Float,
    @ColumnInfo(name = "g") val g: Float,
    @ColumnInfo(name = "b") val b: Float,
) {
    companion object {
        val Neutral = ChannelMix(0.2126f, 0.7152f, 0.0722f)
        val Yellow = ChannelMix(0.50f, 0.40f, 0.10f)
        val Orange = ChannelMix(0.65f, 0.30f, 0.05f)

        /** Darkens sky, separates cloud. */
        val Red = ChannelMix(0.80f, 0.15f, 0.05f)

        /** Foliage and skin. */
        val Green = ChannelMix(0.20f, 0.70f, 0.10f)
    }
}

/**
 * What a fresh install starts with: the classic contrast filters, lightly graded so
 * each one shows what its mix does rather than sitting flat. These are starting
 * points to be edited and renamed, not a curated set.
 */
object RecipePresets {

    fun seed(): List<Recipe> = listOf(
        Recipe(
            id = "neutral",
            name = "Neutral",
            position = 0,
            channelMix = ChannelMix.Neutral,
        ),
        Recipe(
            id = "yellow",
            name = "Yellow",
            position = 1,
            channelMix = ChannelMix.Yellow,
            contrast = 0.10f,
        ),
        Recipe(
            id = "orange",
            name = "Orange",
            position = 2,
            channelMix = ChannelMix.Orange,
            contrast = 0.18f,
            clarity = 0.15f,
        ),
        Recipe(
            id = "red-sky",
            name = "Red Sky",
            position = 3,
            channelMix = ChannelMix.Red,
            contrast = 0.25f,
            clarity = 0.20f,
            vignette = 0.15f,
        ),
        Recipe(
            id = "green",
            name = "Green",
            position = 4,
            channelMix = ChannelMix.Green,
            contrast = 0.08f,
        ),
    )
}
