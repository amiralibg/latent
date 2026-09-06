package com.latent.camera.camera

import androidx.camera.core.ImageCapture
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Stabilized shots earn quality mode; everything handheld stays on latency.
 * The mode is fixed at bind time, so only the timer's zero/non-zero boundary
 * rebinds — cycling 3s↔10s must not cost a viewfinder blackout.
 */
class CaptureModeTest {

    @Test
    fun noTimerMeansLatency() {
        assertEquals(
            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY,
            captureModeFor(0),
        )
    }

    @Test
    fun anyTimerMeansQuality() {
        assertEquals(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY, captureModeFor(3))
        assertEquals(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY, captureModeFor(10))
    }

    @Test
    fun negativeTimerIsTreatedAsOff() {
        assertEquals(
            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY,
            captureModeFor(-1),
        )
    }
}
