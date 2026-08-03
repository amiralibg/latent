package com.latent.camera.gl

import android.content.res.AssetManager
import android.opengl.GLES30
import android.opengl.Matrix

/**
 * Pass one: resolve the source frame into a square 2D texture, applying the crop and
 * rotation on the way. No grading — see `blit.frag`.
 *
 * This is the only program that varies between the two render paths, and it varies
 * only in the type of sampler it declares.
 */
internal class BlitProgram(assets: AssetManager, private val source: TextureSource) {

    private val program: Int
    private val aPosition: Int
    private val aTexCoord: Int
    private val uTexTransform: Int
    private val uTexture: Int

    init {
        program = Gl.buildProgram(
            vertexSource = ShaderSource.vertex(assets),
            fragmentSource = ShaderSource.blit(assets, source),
        )
        aPosition = Gl.attribLocation(program, "aPosition")
        aTexCoord = Gl.attribLocation(program, "aTexCoord")
        uTexTransform = Gl.uniformLocation(program, "texTransform")
        uTexture = Gl.uniformLocation(program, "uTexture")
    }

    fun draw(textureId: Int, texTransform: FloatArray, flipY: Boolean) {
        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(source.glTarget, textureId)
        GLES30.glUniform1i(uTexture, 0)
        GLES30.glUniformMatrix4fv(
            uTexTransform, 1, false,
            if (flipY) flipped(texTransform) else texTransform, 0,
        )

        Quad.draw(aPosition, aTexCoord)

        GLES30.glBindTexture(source.glTarget, 0)
        Gl.checkError("blit")
    }

    fun release() {
        if (program != 0) GLES30.glDeleteProgram(program)
    }

    /** [texTransform] with the t axis reversed: sample 1-t instead of t. */
    private fun flipped(texTransform: FloatArray): FloatArray {
        val m = scratch
        System.arraycopy(texTransform, 0, m, 0, 16)
        Matrix.translateM(m, 0, 0f, 1f, 0f)
        Matrix.scaleM(m, 0, 1f, -1f, 1f)
        return m
    }

    private val scratch = FloatArray(16)
}
