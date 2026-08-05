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

    /**
     * Grain cell size, in pixels of a 1024px frame — see `GRAIN_REFERENCE` in
     * `latent.frag`. It is deliberately *not* in pixels of whatever is being rendered:
     * grain belongs to the picture, so the preview and a full-size export have to lay
     * down the same number of grains across the frame or the viewfinder is lying.
     */
    val grainSize: Float = 3.4f,

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

    /** The named grain this recipe is currently sitting on, if it is sitting on one. */
    val grainPreset: GrainPreset?
        get() = GrainPreset.entries.firstOrNull { it.matches(this) }

    fun withGrain(preset: GrainPreset): Recipe =
        copy(grain = preset.amount, grainSize = preset.size)

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
 * Grain, as the four decisions anyone actually makes about it.
 *
 * A named stock rather than two numbers, because "how grainy" and "how big the grains
 * are" are not independent in any real film — a fast stock is coarse *and* pronounced,
 * and pairing a heavy amount with a tiny cell just looks like sensor noise. The two
 * sliders are still underneath for anyone who disagrees.
 */
enum class GrainPreset(val label: String, val amount: Float, val size: Float) {

    /** A clean digital frame. Still the honest default for a bright, sharp look. */
    Off("Off", 0f, 3.4f),

    /** Slow stock, printed small. Present in the midtones, invisible in a glance. */
    Fine("Fine", 0.13f, 2.4f),

    /** The house grain. Reads as film at arm's length without shouting. */
    Medium("Medium", 0.22f, 3.4f),

    /** 400 pushed a stop: the grain is part of the subject now. */
    Coarse("Coarse", 0.32f, 5.0f),

    /** Available light, pushed hard, and unapologetic about it. */
    Push("Push", 0.46f, 7.2f),

    ;

    fun matches(recipe: Recipe): Boolean =
        kotlin.math.abs(recipe.grain - amount) < 0.005f &&
            (amount == 0f || kotlin.math.abs(recipe.grainSize - size) < 0.05f)
}

/**
 * What a fresh install starts with: the classic contrast filters, lightly graded so
 * each one shows what its mix does rather than sitting flat. These are starting
 * points to be edited and renamed, not a curated set.
 *
 * Each one ships with grain on it. A B&W camera whose first five looks are clean is a
 * camera whose grain engine nobody ever finds — and grain is the one stage here that
 * has to be seen moving in the viewfinder to be judged at all.
 */
object RecipePresets {

    fun seed(): List<Recipe> = listOf(
        Recipe(
            id = "neutral",
            name = "Neutral",
            position = 0,
            channelMix = ChannelMix.Neutral,
        ).withGrain(GrainPreset.Fine),
        Recipe(
            id = "yellow",
            name = "Yellow",
            position = 1,
            channelMix = ChannelMix.Yellow,
            contrast = 0.10f,
        ).withGrain(GrainPreset.Medium),
        Recipe(
            id = "orange",
            name = "Orange",
            position = 2,
            channelMix = ChannelMix.Orange,
            contrast = 0.18f,
            clarity = 0.15f,
        ).withGrain(GrainPreset.Medium),
        Recipe(
            id = "red-sky",
            name = "Red Sky",
            position = 3,
            channelMix = ChannelMix.Red,
            contrast = 0.25f,
            clarity = 0.20f,
            vignette = 0.15f,
        ).withGrain(GrainPreset.Coarse),
        Recipe(
            id = "green",
            name = "Green",
            position = 4,
            channelMix = ChannelMix.Green,
            contrast = 0.08f,
        ).withGrain(GrainPreset.Fine),
        Recipe(
            id = "push",
            name = "Push",
            position = 5,
            channelMix = ChannelMix.Neutral,
            lift = 0.05f,
            contrast = 0.35f,
            clarity = 0.30f,
            halation = 0.25f,
            halationThreshold = 0.72f,
            vignette = 0.22f,
        ).withGrain(GrainPreset.Push),
    )
}
