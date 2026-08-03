package com.latent.camera.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Downsamples the graded preview frame and bins luminance. Preview thread only —
 * never runs on the capture path.
 */
internal class HistogramSampler {

    private val scratch = FrameBuffer()
    private val pixels = ByteBuffer.allocateDirect(SAMPLE_SIDE * SAMPLE_SIDE * 4)
        .order(ByteOrder.nativeOrder())

    /**
     * Returns 64 normalised bins in 0..1. [sourceTexture] is the graded square frame.
     */
    fun sample(sourceTexture: Int, side: Int, blit: AidsProgram): FloatArray {
        scratch.resize(SAMPLE_SIDE, mipmaps = false)
        scratch.bind()
        // Reuse the aids program as a passthrough blit into the tiny FBO.
        blit.blit(sourceTexture, SAMPLE_SIDE)
        // The viewport was set to SAMPLE_SIDE by bind(); read the whole thing.
        pixels.clear()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, scratch.framebufferName)
        GLES30.glReadPixels(
            0, 0, SAMPLE_SIDE, SAMPLE_SIDE,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels,
        )
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        val bins = FloatArray(BIN_COUNT)
        val total = (SAMPLE_SIDE * SAMPLE_SIDE).toFloat()
        pixels.rewind()
        repeat(SAMPLE_SIDE * SAMPLE_SIDE) {
            val r = pixels.get().toInt() and 0xFF
            val g = pixels.get().toInt() and 0xFF
            val b = pixels.get().toInt() and 0xFF
            pixels.get() // a
            val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            val index = (luma * (BIN_COUNT - 1)).toInt().coerceIn(0, BIN_COUNT - 1)
            bins[index] += 1f
        }
        var peak = 0f
        for (i in bins.indices) {
            bins[i] /= total
            if (bins[i] > peak) peak = bins[i]
        }
        if (peak > 0f) {
            for (i in bins.indices) bins[i] /= peak
        }
        return bins
    }

    fun release() {
        scratch.release()
    }

    private companion object {
        const val SAMPLE_SIDE = 64
        const val BIN_COUNT = 64
    }
}
