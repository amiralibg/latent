package com.latent.camera.gl

import android.opengl.Matrix
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The geometry half of the capture path. The golden test renders a square frame at
 * rotation zero so it compares the look alone; crop and rotation are asserted here,
 * on the matrix, where a failure says which corner went where.
 */
@RunWith(AndroidJUnit4::class)
class SquareCropTest {

    @Test
    fun cropsTheCentreSquareOfALandscapeFrame() {
        val matrix = SquareCrop.texTransform(width = 4000, height = 3000, rotationDegrees = 0)

        // 3000 of 4000 columns, centred: 12.5% trimmed from each side.
        assertPoint(matrix, 0f, 0f, expectedS = 0.125f, expectedT = 0f)
        assertPoint(matrix, 1f, 1f, expectedS = 0.875f, expectedT = 1f)
        assertPoint(matrix, 0.5f, 0.5f, expectedS = 0.5f, expectedT = 0.5f)
    }

    @Test
    fun cropsTheCentreSquareOfAPortraitFrame() {
        val matrix = SquareCrop.texTransform(width = 3000, height = 4000, rotationDegrees = 0)

        assertPoint(matrix, 0f, 0f, expectedS = 0f, expectedT = 0.125f)
        assertPoint(matrix, 1f, 1f, expectedS = 1f, expectedT = 0.875f)
    }

    @Test
    fun leavesASquareFrameAlone() {
        val matrix = SquareCrop.texTransform(width = 2048, height = 2048, rotationDegrees = 0)

        assertPoint(matrix, 0f, 0f, expectedS = 0f, expectedT = 0f)
        assertPoint(matrix, 1f, 0f, expectedS = 1f, expectedT = 0f)
        assertPoint(matrix, 0f, 1f, expectedS = 0f, expectedT = 1f)
    }

    /**
     * A quarter turn clockwise. Output top-left must come from source bottom-left,
     * which is the corner that ends up top-left once the frame is turned upright.
     */
    @Test
    fun rotatesNinetyDegreesClockwise() {
        val matrix = SquareCrop.texTransform(width = 1000, height = 1000, rotationDegrees = 90)

        assertPoint(matrix, 0f, 0f, expectedS = 0f, expectedT = 1f)
        assertPoint(matrix, 1f, 0f, expectedS = 0f, expectedT = 0f)
        assertPoint(matrix, 1f, 1f, expectedS = 1f, expectedT = 0f)
        assertPoint(matrix, 0f, 1f, expectedS = 1f, expectedT = 1f)
    }

    @Test
    fun rotatesOneHundredAndEightyDegrees() {
        val matrix = SquareCrop.texTransform(width = 1000, height = 1000, rotationDegrees = 180)

        assertPoint(matrix, 0f, 0f, expectedS = 1f, expectedT = 1f)
        assertPoint(matrix, 1f, 1f, expectedS = 0f, expectedT = 0f)
    }

    @Test
    fun rotationStaysInsideTheCropOfANonSquareFrame() {
        val matrix = SquareCrop.texTransform(width = 4000, height = 3000, rotationDegrees = 90)

        // Every corner of the output must land on a corner of the centre crop, not
        // outside it — an aspect-aware rotation applied in the wrong space would
        // sample past the frame edge here.
        assertPoint(matrix, 0f, 0f, expectedS = 0.125f, expectedT = 1f)
        assertPoint(matrix, 1f, 0f, expectedS = 0.125f, expectedT = 0f)
        assertPoint(matrix, 1f, 1f, expectedS = 0.875f, expectedT = 0f)
        assertPoint(matrix, 0f, 1f, expectedS = 0.875f, expectedT = 1f)
    }

    @Test
    fun sideIsTheShorterEdge() {
        assertEquals(3000, SquareCrop.side(4000, 3000))
        assertEquals(3000, SquareCrop.side(3000, 4000))
        assertEquals(2048, SquareCrop.side(2048, 2048))
    }

    private fun assertPoint(
        matrix: FloatArray,
        s: Float,
        t: Float,
        expectedS: Float,
        expectedT: Float,
    ) {
        val result = FloatArray(4)
        Matrix.multiplyMV(result, 0, matrix, 0, floatArrayOf(s, t, 0f, 1f), 0)
        assertEquals("s for ($s, $t)", expectedS, result[0], TOLERANCE)
        assertEquals("t for ($s, $t)", expectedT, result[1], TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
