package com.latent.camera.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * The full-screen triangle strip every pass draws on, and the attribute plumbing
 * that goes with it.
 *
 * Texture coordinate (0,0) sits on the bottom-left vertex. That pairing is what lets
 * the capture path skip an explicit vertical flip: an uploaded bitmap has its top row
 * at t=0, so it lands at the bottom of the framebuffer, and `glReadPixels` then reads
 * bottom-up and undoes it exactly. Change one and you must change the other.
 */
internal object Quad {

    private val POSITIONS: FloatBuffer = floatBufferOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f,
    )

    /** The same quad upside down, for a pass whose consumer reads rows top-down. */
    private val POSITIONS_FLIPPED: FloatBuffer = floatBufferOf(
        -1f, 1f,
        1f, 1f,
        -1f, -1f,
        1f, -1f,
    )

    private val TEX_COORDS: FloatBuffer = floatBufferOf(
        0f, 0f,
        1f, 0f,
        0f, 1f,
        1f, 1f,
    )

    fun draw(positionAttribute: Int, texCoordAttribute: Int, flipY: Boolean = false) {
        val positions = if (flipY) POSITIONS_FLIPPED else POSITIONS
        GLES30.glEnableVertexAttribArray(positionAttribute)
        GLES30.glVertexAttribPointer(positionAttribute, 2, GLES30.GL_FLOAT, false, 0, positions)
        GLES30.glEnableVertexAttribArray(texCoordAttribute)
        GLES30.glVertexAttribPointer(texCoordAttribute, 2, GLES30.GL_FLOAT, false, 0, TEX_COORDS)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(positionAttribute)
        GLES30.glDisableVertexAttribArray(texCoordAttribute)
    }

    /** Identity texture transform, for a pass that samples one-to-one. */
    val IDENTITY = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )

    private fun floatBufferOf(vararg values: Float): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }
}
