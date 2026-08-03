package com.latent.camera.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * File naming carries more weight than it looks like it does.
 *
 * The DNG save path cannot tell its two results apart any other way. CameraX writes
 * the RAW and the JPEG itself and calls back once per file with nothing but a URI, so
 * [CaptureNaming.isOriginalJpeg] applied to that URI's display name is the only thing
 * deciding which of the two gets graded. Get it wrong and the app feeds a DNG to the
 * JPEG decoder — silently, and only on devices that support RAW.
 */
class CaptureNamingTest {

    @Test
    fun originalJpegIsRecognised() {
        val stem = "LATENT_20260801_143000_123"
        assertTrue(CaptureNaming.isOriginalJpeg(CaptureNaming.originalFileName(stem)))
    }

    @Test
    fun dngIsNotMistakenForTheOriginalJpeg() {
        val stem = "LATENT_20260801_143000_123"
        assertFalse(CaptureNaming.isOriginalJpeg(CaptureNaming.dngFileName(stem)))
    }

    @Test
    fun gradedOutputIsNotMistakenForTheOriginalJpeg() {
        val stem = "LATENT_20260801_143000_123"
        assertFalse(CaptureNaming.isOriginalJpeg(CaptureNaming.outputFileName(stem)))
    }

    /**
     * MediaStore is free to hand back a display name in whatever case the filesystem
     * stored it, and the routing must not depend on that.
     */
    @Test
    fun recognitionIgnoresCase() {
        assertTrue(CaptureNaming.isOriginalJpeg("LATENT_20260801_143000_123_SRC.JPG"))
    }

    @Test
    fun theThreeFilesOfOneShotShareAStem() {
        val stem = "LATENT_20260801_143000_123"
        val names = listOf(
            CaptureNaming.outputFileName(stem),
            CaptureNaming.originalFileName(stem),
            CaptureNaming.dngFileName(stem),
        )
        assertTrue("A shot's files must be legible as a pair on disk", names.all { it.startsWith(stem) })
        assertEquals("and must not collide", 3, names.toSet().size)
    }

    /**
     * Millisecond precision is what keeps a re-grade — which stems from the moment it
     * was rendered, not the moment it was shot — from landing on an existing file.
     */
    @Test
    fun stemsAreDistinctWithinTheSameSecond() {
        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            set(2026, Calendar.AUGUST, 1, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val first = CaptureNaming.stem(calendar.time)
        val second = CaptureNaming.stem(Date(calendar.timeInMillis + 1))
        assertTrue(first != second)
    }
}
