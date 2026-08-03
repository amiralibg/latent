package com.latent.camera.gl

import android.content.res.AssetManager
import android.opengl.GLES30
import com.latent.camera.settings.AidsState

/**
 * Preview-only post pass. Composites peaking and zebra onto the graded frame.
 * Capture never constructs this program.
 */
internal class AidsProgram(assets: AssetManager) {

    private val program: Int = Gl.buildProgram(
        vertexSource = ShaderSource.vertex(assets),
        fragmentSource = ShaderSource.aids(assets),
    )

    private val aPosition = Gl.attribLocation(program, "aPosition")
    private val aTexCoord = Gl.attribLocation(program, "aTexCoord")
    private val uTexTransform = Gl.uniformLocation(program, "texTransform")
    private val uTexture = Gl.uniformLocation(program, "uTexture")
    private val uTexelSize = Gl.uniformLocation(program, "texelSize")
    private val uPeaking = Gl.uniformLocation(program, "peaking")
    private val uPeakingThreshold = Gl.uniformLocation(program, "peakingThreshold")
    private val uZebra = Gl.uniformLocation(program, "zebra")
    private val uZebraThreshold = Gl.uniformLocation(program, "zebraThreshold")
    private val uZebraPhase = Gl.uniformLocation(program, "zebraPhase")

    fun draw(textureId: Int, side: Int, aids: AidsState, phase: Float) {
        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(uTexture, 0)
        GLES30.glUniformMatrix4fv(uTexTransform, 1, false, Quad.IDENTITY, 0)
        GLES30.glUniform2f(uTexelSize, 1f / side, 1f / side)
        GLES30.glUniform1f(uPeaking, if (aids.peaking) 1f else 0f)
        GLES30.glUniform1f(uPeakingThreshold, PEAKING_THRESHOLD)
        GLES30.glUniform1f(uZebra, if (aids.zebra) 1f else 0f)
        GLES30.glUniform1f(uZebraThreshold, ZEBRA_THRESHOLD)
        GLES30.glUniform1f(uZebraPhase, phase)

        // Graded FBO was written with Screen presentation (top-down). Draw without
        // an extra flip so the window sees the same orientation as the direct path.
        Quad.draw(aPosition, aTexCoord, flipY = false)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        Gl.checkError("aids")
    }

    /** Passthrough copy when histogram needs the graded FBO but aids are off. */
    fun blit(textureId: Int, side: Int) {
        draw(textureId, side, AidsState(peaking = false, zebra = false), phase = 0f)
    }

    fun release() {
        if (program != 0) GLES30.glDeleteProgram(program)
    }

    private companion object {
        const val PEAKING_THRESHOLD = 0.18f
        const val ZEBRA_THRESHOLD = 0.97f
    }
}
