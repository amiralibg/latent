package com.latent.camera.gl

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.latent.camera.look.ChannelMix
import com.latent.camera.look.GrainPreset
import com.latent.camera.look.Recipe
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The WYSIWYG guarantee, asserted.
 *
 * The preview and the saved file are supposed to be the same image because they run
 * the same fragment shader. This renders one fixed frame through both paths and
 * compares them. It runs on every phase merge, and it is the only thing standing
 * between a shader change and a preview/export mismatch discovered three phases
 * later — so if it fails, fix the pipeline rather than the threshold.
 */
@RunWith(AndroidJUnit4::class)
class GoldenImageTest {

    private lateinit var probe: PreviewRenderProbe
    private lateinit var offscreen: OffscreenRenderer

    private val assets get() =
        InstrumentationRegistry.getInstrumentation().targetContext.assets

    @Before
    fun setUp() {
        probe = PreviewRenderProbe(assets, TestFrame.SIZE)
        offscreen = OffscreenRenderer(assets)
    }

    @After
    fun tearDown() {
        probe.release()
        offscreen.release()
    }

    @Test
    fun previewAndCapturePathsAgree() {
        assertPathsAgree(Recipe.Default)
    }

    /**
     * A recipe the two paths could plausibly disagree on: a red-filter mix pulls the
     * channels far apart, so any difference in how the two sample or decode colour
     * shows up as a tonal shift rather than cancelling out.
     */
    @Test
    fun previewAndCapturePathsAgreeOnAStrongChannelMix() {
        assertPathsAgree(Recipe(name = "Red", channelMix = ChannelMix.Red))
    }

    /**
     * Every stage engaged at once, hard.
     *
     * This is the case that actually protects the WYSIWYG guarantee now that the
     * chain is seven stages deep: clarity and halation read from mip levels, and
     * grain is a spatial hash, so any difference in how the two paths build their
     * intermediate texture shows up here and nowhere else.
     */
    @Test
    fun previewAndCapturePathsAgreeOnTheFullChain() {
        assertPathsAgree(
            Recipe(
                name = "Everything",
                channelMix = ChannelMix.Orange,
                lift = 0.06f,
                gamma = 1.25f,
                gain = 1.1f,
                contrast = 0.45f,
                clarity = 0.8f,
                clarityRadius = 0.03f,
                halation = 0.6f,
                halationThreshold = 0.6f,
                halationRadius = 0.08f,
                grain = 0.35f,
                grainSize = 2.5f,
                toning = 0.7f,
                vignette = 0.5f,
            ),
        )
    }

    /** Each stage alone, so a failure names the stage that broke rather than "the look". */
    @Test
    fun previewAndCapturePathsAgreeStageByStage() {
        val base = Recipe(name = "stage", channelMix = ChannelMix.Neutral)
        listOf(
            "tone" to base.copy(lift = 0.1f, gamma = 1.4f, gain = 1.2f, contrast = 0.6f),
            "clarity" to base.copy(clarity = 1.2f, clarityRadius = 0.05f),
            "halation" to base.copy(halation = 0.9f, halationThreshold = 0.5f),
            "grain" to base.copy(grain = 0.5f, grainSize = 3f),
            "toning" to base.copy(toning = -0.9f),
            "vignette" to base.copy(vignette = 0.9f),
        ).forEach { (stage, recipe) ->
            assertPathsAgree(recipe.copy(name = stage))
        }
    }

    /**
     * Grain is measured against the frame, not against the pixel grid.
     *
     * This is what makes the grain in the viewfinder the grain in the file. The
     * preview renders at roughly a thousand pixels and the export at three or four
     * thousand; if the grain cell were a fixed number of *rendered* pixels, the same
     * recipe would lay down three times as many grains in the export as the preview
     * showed, and the one stage of the look you cannot judge from a slider would be
     * the one stage the viewfinder lied about.
     *
     * Rendering the same recipe at 256 and at 768 and sampling matched points is an
     * exact comparison rather than a resampled one: pixel k of the small frame and
     * pixel 3k+1 of the large frame have identical texture coordinates, so the shader
     * is being asked for the same point of the same picture twice.
     */
    @Test
    fun grainIsTheSameSizeAtEveryResolution() {
        val recipe = Recipe(name = "grain scale", grain = 0.5f, grainSize = 40f)
        val small = flatGrey(256)
        val large = flatGrey(768)
        var rendered1: Bitmap? = null
        var rendered3: Bitmap? = null
        try {
            rendered1 = offscreen.render(small, rotationDegrees = 0, recipe = recipe)
            rendered3 = offscreen.render(large, rotationDegrees = 0, recipe = recipe)

            var total = 0L
            var samples = 0
            for (y in 0 until rendered1.height) {
                for (x in 0 until rendered1.width) {
                    val a = Color.red(rendered1.getPixel(x, y))
                    val b = Color.red(rendered3.getPixel(3 * x + 1, 3 * y + 1))
                    total += Math.abs(a - b).toLong()
                    samples++
                }
            }
            val difference = total.toDouble() / samples
            Log.i(TAG, "grain scale difference across resolutions: $difference")
            assertTrue(
                "Grain moved with the pixel grid rather than the frame: $difference levels " +
                    "between a 256px and a 768px render of the same recipe",
                difference <= MAX_MEAN_DIFFERENCE,
            )
        } finally {
            rendered1?.recycle()
            rendered3?.recycle()
            small.recycle()
            large.recycle()
        }
    }

    /**
     * The shipped grain presets have to be visible at preview resolution.
     *
     * A grain cell finer than a preview pixel averages away to nothing on screen while
     * still being there in the file — which is the same WYSIWYG break as the test
     * above, arrived at from the other direction. Asserting a floor on the spread of a
     * flat grey frame at a realistic preview size is what stops a future "subtler
     * default" from quietly making grain a setting you cannot see yourself change.
     */
    @Test
    fun shippedGrainPresetsAreVisibleAtPreviewResolution() {
        listOf(GrainPreset.Fine, GrainPreset.Medium, GrainPreset.Coarse, GrainPreset.Push)
            .forEach { preset ->
                val source = flatGrey(1080)
                var graded: Bitmap? = null
                try {
                    graded = offscreen.render(
                        source = source,
                        rotationDegrees = 0,
                        recipe = Recipe(name = preset.label).withGrain(preset),
                    )
                    val spread = standardDeviation(graded)
                    Log.i(TAG, "'${preset.label}' grain spread at 1080: $spread levels")
                    assertTrue(
                        "Grain preset '${preset.label}' is invisible at preview " +
                            "resolution: spread of $spread levels over flat grey",
                        spread >= MIN_VISIBLE_GRAIN_SPREAD,
                    )
                } finally {
                    graded?.recycle()
                    source.recycle()
                }
            }
    }

    @Test
    fun outputIsMonochrome() {
        val frame = TestFrame.create()
        try {
            val graded = offscreen.render(frame, rotationDegrees = 0, recipe = Recipe.Default)
            try {
                var maxSpread = 0
                for (y in 0 until graded.height step 7) {
                    for (x in 0 until graded.width step 7) {
                        val pixel = graded.getPixel(x, y)
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val b = Color.blue(pixel)
                        maxSpread = maxOf(maxSpread, maxOf(r, g, b) - minOf(r, g, b))
                    }
                }
                assertEquals("Graded output is not neutral", 0, maxSpread)
            } finally {
                graded.recycle()
            }
        } finally {
            frame.recycle()
        }
    }

    private fun assertPathsAgree(recipe: Recipe) {
        val frame = TestFrame.create()
        var fromPreview: Bitmap? = null
        var fromCapture: Bitmap? = null
        try {
            fromPreview = probe.render(frame, recipe)
            // rotationDegrees 0 and a square source make the crop matrix an identity,
            // so this compares the look and nothing else. SquareCropTest covers the
            // geometry separately.
            fromCapture = offscreen.render(frame, rotationDegrees = 0, recipe = recipe)

            assertEquals(
                "Paths produced different sizes",
                fromPreview.width to fromPreview.height,
                fromCapture.width to fromCapture.height,
            )

            val difference = meanChannelDifference(fromPreview, fromCapture)
            Log.i(TAG, "'${recipe.name}' mean channel difference: $difference")
            assertTrue(
                "Preview and capture paths diverged by $difference levels " +
                    "(threshold $MAX_MEAN_DIFFERENCE) for recipe '${recipe.name}'",
                difference <= MAX_MEAN_DIFFERENCE,
            )
        } finally {
            fromPreview?.recycle()
            fromCapture?.recycle()
            frame.recycle()
        }
    }

    /** Flat mid-grey: grain is weighted to the midtones, so this is where it is loudest. */
    private fun flatGrey(size: Int): Bitmap {
        val pixels = IntArray(size * size) { Color.rgb(128, 128, 128) }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /** Spread of the red channel in levels. The output is neutral, so one channel is all of them. */
    private fun standardDeviation(bitmap: Bitmap): Double {
        val row = IntArray(bitmap.width)
        var sum = 0.0
        var sumSquares = 0.0
        var count = 0L
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (pixel in row) {
                val value = Color.red(pixel).toDouble()
                sum += value
                sumSquares += value * value
                count++
            }
        }
        val mean = sum / count
        return kotlin.math.sqrt((sumSquares / count) - mean * mean)
    }

    private companion object {
        const val TAG = "GoldenImageTest"

        /**
         * Levels of spread over a flat grey frame at preview resolution. Two levels is
         * about where grain stops being a texture and starts being a rumour — below
         * this it is dithering, not film.
         */
        const val MIN_VISIBLE_GRAIN_SPREAD = 2.0

        /**
         * Levels out of 255, averaged over every channel of every pixel. The two
         * paths run identical arithmetic, so the only expected difference is
         * sampling: the preview reaches the shader through a SurfaceTexture whose
         * transform can sit half a texel off, which shows only along hard edges.
         * A flipped or rotated frame scores in the tens and fails loudly.
         */
        const val MAX_MEAN_DIFFERENCE = 2.0
    }
}
