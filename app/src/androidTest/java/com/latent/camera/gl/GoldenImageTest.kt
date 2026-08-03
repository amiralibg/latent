package com.latent.camera.gl

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.latent.camera.look.ChannelMix
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

    private companion object {
        const val TAG = "GoldenImageTest"

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
