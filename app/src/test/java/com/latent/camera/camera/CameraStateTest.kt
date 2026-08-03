package com.latent.camera.camera

import com.latent.camera.settings.SaveMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The derived properties the viewfinder reads every frame. None of them touch
 * hardware — they are arithmetic over what the hardware last said — which is exactly
 * why they are worth pinning here rather than discovering on a device.
 */
class CameraStateTest {

    @Test
    fun exposureEvIsIndexTimesTheDeviceStep() {
        // A third-stop device, two clicks up.
        val state = CameraState(capabilities = capabilities(evStepEv = 1f / 3f), exposureIndex = 2)
        assertEquals(0.667f, state.exposureEv, 0.001f)
    }

    @Test
    fun exposureEvIsZeroBeforeTheCameraIsOpen() {
        assertEquals(0f, CameraState(exposureIndex = 3).exposureEv, 0f)
    }

    // ------------------------------------------------------------------ lens matching

    /**
     * The tolerance is absolute rather than proportional on purpose: an ultra-wide at
     * 0.6× needs a window that does not shrink towards nothing just because its number
     * is small.
     */
    @Test
    fun theUltraWideStillMatchesAtItsOwnRatio() {
        val state = CameraState(lenses = threeLenses(), zoomRatio = 0.62f)
        assertEquals(0.6f, state.activeLens?.zoomRatio)
    }

    @Test
    fun aZoomBetweenLensesMatchesNeither() {
        val state = CameraState(lenses = threeLenses(), zoomRatio = 1.7f)
        assertNull("1.7× is not sitting on any lens", state.activeLens)
    }

    @Test
    fun aZoomOnALensMatchesThatLensAndNotItsNeighbour() {
        val state = CameraState(lenses = threeLenses(), zoomRatio = 3f)
        assertEquals(3f, state.activeLens?.zoomRatio)
    }

    @Test
    fun noLensesMeansNoMatchRatherThanACrash() {
        assertNull(CameraState(lenses = emptyList(), zoomRatio = 1f).activeLens)
    }

    // ------------------------------------------------------------------ handheld warning

    /**
     * One over the 35mm-equivalent focal length. At 24mm that is 1/24s, so 1/30 is
     * fine and 1/15 is a gamble the app should say out loud.
     */
    @Test
    fun aShutterBelowOneOverFocalLengthWarns() {
        val state = CameraState(
            capabilities = capabilities(equivalent35mm = 24f),
            manual = ManualControls(exposureTimeNs = 1_000_000_000L / 15),
        )
        assertTrue(state.handheldWarning)
    }

    @Test
    fun aShutterAboveOneOverFocalLengthDoesNotWarn() {
        val state = CameraState(
            capabilities = capabilities(equivalent35mm = 24f),
            manual = ManualControls(exposureTimeNs = 1_000_000_000L / 60),
        )
        assertFalse(state.handheldWarning)
    }

    /**
     * The warning is about a shutter speed *you* chose. On auto there is nothing to
     * warn about — the camera picked it and the rule does not apply the same way.
     */
    @Test
    fun autoExposureNeverWarns() {
        val state = CameraState(capabilities = capabilities(equivalent35mm = 24f))
        assertFalse(state.handheldWarning)
    }

    // ------------------------------------------------------------------ save mode

    @Test
    fun onlyBwOnlyDiscardsTheOriginal() {
        assertFalse(SaveMode.BwOnly.keepsOriginal)
        assertTrue(SaveMode.BwAndOriginal.keepsOriginal)
        assertTrue(SaveMode.BwOriginalAndDng.keepsOriginal)
    }

    @Test
    fun onlyTheDngModeAsksForRaw() {
        assertFalse(SaveMode.BwOnly.wantsDng)
        assertFalse(SaveMode.BwAndOriginal.wantsDng)
        assertTrue(SaveMode.BwOriginalAndDng.wantsDng)
    }

    @Test
    fun theDefaultKeepsTheOriginal() {
        // Invariant 3: grading stays re-derivable unless the user opts out.
        assertTrue(CameraState().saveMode.keepsOriginal)
    }

    private fun threeLenses(): List<Lens> = listOf(
        Lens(physicalId = "2", focalLengthMm = 2.2f, equivalent35mm = 13f, zoomRatio = 0.6f),
        Lens(physicalId = "0", focalLengthMm = 6.8f, equivalent35mm = 24f, zoomRatio = 1f),
        Lens(physicalId = "3", focalLengthMm = 20f, equivalent35mm = 72f, zoomRatio = 3f),
    )

    private fun capabilities(
        evStepEv: Float = 1f / 3f,
        equivalent35mm: Float = 24f,
    ) = CameraCapabilities(
        hardwareLevel = 1,
        supportsManualSensor = true,
        isoRange = 50..3200,
        exposureTimeRangeNs = 1_000L..1_000_000_000L,
        minFocusDistanceDioptres = 10f,
        focusDistanceCalibrated = true,
        evRange = -12..12,
        evStepEv = evStepEv,
        minZoomRatio = 0.6f,
        maxZoomRatio = 10f,
        focalLengthMm = 6.8f,
        equivalent35mm = equivalent35mm,
        apertureF = 1.8f,
        supportsRawJpeg = true,
    )
}
