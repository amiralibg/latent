package com.latent.camera.look

import com.latent.camera.data.CaptureRecord
import com.latent.camera.settings.AidsState
import com.latent.camera.settings.GridMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeTest {

    /**
     * A recipe is a look plus an identity, and the two must not leak into each other.
     * Reset copies a look onto an existing recipe, so if identity travelled with it
     * the recipe would silently become a duplicate of whatever it was reset from.
     */
    @Test
    fun applyingAnotherLookKeepsIdentity() {
        val mine = Recipe(id = "mine", name = "My Look", position = 4, contrast = 0.5f)
        val theirs = Recipe(id = "theirs", name = "Theirs", position = 0, contrast = 0.1f, grain = 0.3f)

        val result = mine.withLookOf(theirs)

        assertEquals("mine", result.id)
        assertEquals("My Look", result.name)
        assertEquals(4, result.position)
        assertEquals(0.1f, result.contrast)
        assertEquals(0.3f, result.grain)
    }

    @Test
    fun theSeededPresetsAreDistinctAndOrdered() {
        val seed = RecipePresets.seed()
        assertEquals(seed.size, seed.map { it.id }.toSet().size)
        assertEquals(seed.indices.toList(), seed.map { it.position })
    }

    /**
     * The channel mix is the contrast-filter emulation and the one stage whose numbers
     * are load bearing rather than to taste. Red has to darken sky, which means most
     * of the weight on red and very little on blue.
     */
    @Test
    fun theRedFilterWeightsRedFarAboveBlue() {
        val red = ChannelMix.Red
        assertTrue(red.r > 0.7f)
        assertTrue(red.b < 0.1f)
    }

    @Test
    fun neutralIsRec709Luminance() {
        assertEquals(0.2126f, ChannelMix.Neutral.r, 0.0001f)
        assertEquals(0.7152f, ChannelMix.Neutral.g, 0.0001f)
        assertEquals(0.0722f, ChannelMix.Neutral.b, 0.0001f)
    }
}

class AidsStateTest {

    /** These two gate real GL work per frame, so a wrong answer costs frame rate. */
    @Test
    fun onlyPeakingAndZebraNeedThePostLookPass() {
        assertFalse(AidsState(histogram = true, level = true).needsGlOverlay)
        assertTrue(AidsState(peaking = true).needsGlOverlay)
        assertTrue(AidsState(zebra = true).needsGlOverlay)
    }

    @Test
    fun theHistogramNeedsTheGradedFrameWithoutNeedingTheOverlay() {
        val aids = AidsState(histogram = true)
        assertTrue(aids.needsGradedFrame)
        assertFalse(aids.needsGlOverlay)
    }

    @Test
    fun everythingOffNeedsNothing() {
        val off = AidsState(peaking = false, zebra = false, histogram = false, level = false, grid = GridMode.Off)
        assertFalse(off.needsGradedFrame)
        assertFalse(off.anyOn)
    }

    @Test
    fun gridCyclingReturnsToOffAfterEveryMode() {
        var mode = GridMode.Off
        repeat(GridMode.entries.size) { mode = mode.next() }
        assertEquals(GridMode.Off, mode)
    }

    @Test
    fun gridCyclingVisitsEveryModeExactlyOnce() {
        val seen = mutableListOf(GridMode.Off)
        repeat(GridMode.entries.size - 1) { seen += seen.last().next() }
        assertEquals(GridMode.entries.size, seen.toSet().size)
    }
}

class CaptureRecordTest {

    /**
     * Re-grade renders from the stored original. No original, no re-grade — and the
     * gallery hides the control rather than offering one that fails.
     */
    @Test
    fun aShotWithoutAnOriginalCannotBeRegraded() {
        assertFalse(record(sourceUri = null).canRegrade)
        assertTrue(record(sourceUri = "content://src/1").canRegrade)
    }

    /**
     * A re-grade points at the same original as the shot it came from. That sharing is
     * what makes deletion delicate, and what the gallery has to check before removing
     * the file underneath both rows.
     */
    @Test
    fun aRegradeSharesItsSourceWithItsParent() {
        val parent = record(stem = "LATENT_A", sourceUri = "content://src/1")
        val child = record(
            stem = "LATENT_B",
            sourceUri = "content://src/1",
            regradedFrom = parent.stem,
        )

        assertNotEquals(parent.stem, child.stem)
        assertEquals(parent.sourceUri, child.sourceUri)
        assertEquals(parent.stem, child.regradedFrom)
        assertTrue(child.canRegrade)
    }

    private fun record(
        stem: String = "LATENT_20260801_143000_123",
        sourceUri: String? = "content://src/1",
        regradedFrom: String? = null,
    ) = CaptureRecord(
        stem = stem,
        outputUri = "content://out/$stem",
        sourceUri = sourceUri,
        recipeName = "Neutral",
        capturedAt = 1_785_000_000_000L,
        rotationDegrees = 90,
        sourceWidth = 4080,
        sourceHeight = 3060,
        regradedFrom = regradedFrom,
    )
}
