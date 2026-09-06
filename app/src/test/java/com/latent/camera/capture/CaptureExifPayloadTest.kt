package com.latent.camera.capture

import com.latent.camera.look.ChannelMix
import com.latent.camera.look.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The file must describe the look that made it, not just name it. A recipe can be
 * renamed or deleted afterwards; the payload in EXIF is what keeps the frame
 * re-derivable.
 */
class CaptureExifPayloadTest {

    @Test
    fun payloadCarriesEveryUniform() {
        val recipe = Recipe(
            name = "Red Sky",
            channelMix = ChannelMix.Red,
            lift = 0.06f,
            gamma = 1.25f,
            gain = 1.1f,
            contrast = 0.45f,
            clarity = 0.8f,
            halation = 0.6f,
            grain = 0.35f,
            grainSize = 2.5f,
            toning = 0.7f,
            vignette = 0.5f,
        )
        val payload = CaptureExif.recipePayload(recipe)
        assertTrue(payload.startsWith("latent/v1 "))
        for (key in listOf(
            "name=", "mix=", "lift=", "gamma=", "gain=", "contrast=",
            "clarity=", "clarityRadius=", "halation=", "halationThreshold=",
            "halationRadius=", "grain=", "grainSize=", "toning=", "vignette=",
        )) {
            assertTrue("payload is missing $key: $payload", payload.contains(key))
        }
        assertTrue(payload.contains("mix=0.8,0.15,0.05"))
        assertTrue(payload.contains("name=Red_Sky"))
    }

    @Test
    fun defaultRecipeRoundTripsExactly() {
        assertEquals(
            "latent/v1 name=Neutral mix=0.2126,0.7152,0.0722 lift=0.0 gamma=1.0 " +
                "gain=1.0 contrast=0.0 clarity=0.0 clarityRadius=0.02 halation=0.0 " +
                "halationThreshold=0.75 halationRadius=0.05 grain=0.0 grainSize=3.4 " +
                "toning=0.0 vignette=0.0",
            CaptureExif.recipePayload(Recipe(name = "Neutral")),
        )
    }

    @Test
    fun namesCannotBreakTheFormat() {
        val payload = CaptureExif.recipePayload(Recipe(name = "a=b;c d\ne"))
        assertTrue(payload.contains("name=a_b_c_d_e"))
    }
}
