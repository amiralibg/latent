package com.latent.camera.gl

import android.opengl.Matrix

/**
 * The texture matrix that turns a full sensor frame into the 1:1 output frame:
 * centre-crop to a square and bake in the sensor rotation, both while sampling, so
 * no full-resolution bitmap is ever copied on the CPU to do it.
 */
internal object SquareCrop {

    /** Side of the output square for a [width] x [height] source frame. */
    fun side(width: Int, height: Int): Int = minOf(width, height)

    /**
     * Matrix mapping the quad's texture coordinates onto the centre square of a
     * [width] x [height] texture, rotated by [rotationDegrees] clockwise (the sense
     * CameraX and EXIF both use — how far the frame must turn to be upright).
     *
     * Matrix ops post-multiply, so the transform applied to a coordinate runs in
     * reverse of the order written: recentre, rotate, restore, scale into the crop,
     * offset to the crop's corner.
     */
    fun texTransform(width: Int, height: Int, rotationDegrees: Int): FloatArray {
        val matrix = FloatArray(16)
        texTransformInto(matrix, width, height, rotationDegrees)
        return matrix
    }

    /** [texTransform] written into a caller-owned [out] array, to avoid per-frame churn. */
    fun texTransformInto(out: FloatArray, width: Int, height: Int, rotationDegrees: Int) {
        val side = side(width, height).toFloat()
        val scaleS = side / width
        val scaleT = side / height
        val offsetS = (1f - scaleS) / 2f
        val offsetT = (1f - scaleT) / 2f

        Matrix.setIdentityM(out, 0)
        Matrix.translateM(out, 0, offsetS, offsetT, 0f)
        Matrix.scaleM(out, 0, scaleS, scaleT, 1f)
        Matrix.translateM(out, 0, 0.5f, 0.5f, 0f)
        // Negated: rotating the *image* clockwise means sampling counter-clockwise.
        Matrix.rotateM(out, 0, -rotationDegrees.toFloat(), 0f, 0f, 1f)
        Matrix.translateM(out, 0, -0.5f, -0.5f, 0f)
    }
}
