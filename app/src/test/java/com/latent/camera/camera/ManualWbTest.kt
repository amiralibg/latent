package com.latent.camera.camera

import android.hardware.camera2.CameraMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * White balance as a manual parameter. The request keys themselves can only be
 * exercised on a device, but the state machine around them — what counts as
 * engaged, which preset names what, and that "auto" builds an empty request —
 * is pure logic and pinned here.
 */
class ManualWbTest {

    @Test
    fun wbIsAutoByDefault() {
        val manual = ManualControls()
        assertFalse(manual.isWbManual)
        assertFalse(manual.isAnyManual)
    }

    @Test
    fun aPresetEngagesWb() {
        val manual = ManualControls(wbPreset = CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT)
        assertTrue(manual.isWbManual)
        assertTrue(manual.isAnyManual)
    }

    @Test
    fun aLockEngagesWbWithoutAPreset() {
        val manual = ManualControls(awbLocked = true)
        assertTrue(manual.isWbManual)
        assertTrue(manual.isAnyManual)
    }

    @Test
    fun wbDoesNotDragExposureOrFocusWithIt() {
        val manual = ManualControls(awbLocked = true)
        assertFalse(manual.isExposureManual)
        assertFalse(manual.isFocusManual)
    }

    @Test
    fun presetsNameThemselvesInDaylightOrder() {
        val labels = ManualControls.WbPresets.map { it.second }
        assertEquals(
            listOf("DAYLIGHT", "CLOUDY", "SHADE", "DUSK", "FLUORO", "TUNGSTEN"),
            labels,
        )
    }

    @Test
    fun wbLabelFallsBackToWbForAnUnknownMode() {
        assertEquals("DAYLIGHT", ManualControls.wbLabel(CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT))
        assertEquals("WB", ManualControls.wbLabel(-999))
    }

    @Test
    fun autoBuildsAnEmptyRequest() {
        // Nothing engaged means no keys touched — the camera stays on its defaults,
        // which is also what releases a lock set by an earlier request.
        val options = ManualControls().toCaptureRequestOptions(MeteredValues(), capabilities())
        assertTrue(options.config.listOptions().isEmpty())
    }

    @Test
    fun wbIsUnsupportedByDefault() {
        // No device probed, no dial stops: an unsupported control is absent, not inert.
        val caps = capabilities()
        assertFalse(caps.supportsManualWb)
        assertFalse(caps.supportsAwbLock)
    }

    private fun capabilities() = CameraCapabilities(
        hardwareLevel = 1,
        supportsManualSensor = true,
        isoRange = 50..3200,
        exposureTimeRangeNs = 1_000L..1_000_000_000L,
        minFocusDistanceDioptres = 10f,
        focusDistanceCalibrated = true,
        evRange = -12..12,
        evStepEv = 1f / 3f,
        minZoomRatio = 0.6f,
        maxZoomRatio = 10f,
        focalLengthMm = 6.8f,
        equivalent35mm = 24f,
        apertureF = 1.8f,
        supportsRawJpeg = true,
    )
}
