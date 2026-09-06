package com.latent.camera.gl

import android.content.res.AssetManager
import android.opengl.GLES30
import com.latent.camera.look.Recipe

/**
 * Pass two: the look. Compiled from source that carries no per-consumer variation at
 * all, so the preview and the saved file are running the same program.
 *
 * Every uniform below is looked up by the exact name of the [Recipe] field that feeds
 * it. Adding a stage means adding the field, the uniform and one line here — and if
 * you misspell any of them, [Gl.uniformLocation] throws at startup rather than
 * silently grading with a zero.
 */
internal class LookProgram(assets: AssetManager) {

    private val program: Int = Gl.buildProgram(
        vertexSource = ShaderSource.vertex(assets),
        fragmentSource = ShaderSource.look(assets),
    )

    private val aPosition = Gl.attribLocation(program, "aPosition")
    private val aTexCoord = Gl.attribLocation(program, "aTexCoord")
    private val uTexTransform = Gl.uniformLocation(program, "texTransform")
    private val uTexture = Gl.uniformLocation(program, "uTexture")
    private val uFrameSize = Gl.uniformLocation(program, "frameSize")

    private val uChannelMix = Gl.uniformLocation(program, "channelMix")
    private val uLift = Gl.uniformLocation(program, "lift")
    private val uGamma = Gl.uniformLocation(program, "gamma")
    private val uGain = Gl.uniformLocation(program, "gain")
    private val uContrast = Gl.uniformLocation(program, "contrast")
    private val uClarity = Gl.uniformLocation(program, "clarity")
    private val uClarityRadius = Gl.uniformLocation(program, "clarityRadius")
    private val uHalation = Gl.uniformLocation(program, "halation")
    private val uHalationThreshold = Gl.uniformLocation(program, "halationThreshold")
    private val uHalationRadius = Gl.uniformLocation(program, "halationRadius")
    private val uGrain = Gl.uniformLocation(program, "grain")
    private val uGrainSize = Gl.uniformLocation(program, "grainSize")
    private val uGrainDetail = Gl.uniformLocation(program, "grainDetail")
    private val uToning = Gl.uniformLocation(program, "toning")
    private val uVignette = Gl.uniformLocation(program, "vignette")

    /**
     * [frameSize] is the side of the frame being graded. Blur radii are fractions of
     * the frame, so without it the preview and a full-size render would disagree on
     * how much clarity and halation to apply.
     *
     * [grainDetail] is preview load control, not look: 1.0 renders the full field,
     * 0.0 the reduced one. It defaults to full, which is what the capture path and
     * the golden test always use.
     */
    fun draw(textureId: Int, frameSize: Int, recipe: Recipe, flipY: Boolean, grainDetail: Float = 1f) {
        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(uTexture, 0)
        GLES30.glUniformMatrix4fv(uTexTransform, 1, false, Quad.IDENTITY, 0)
        GLES30.glUniform1f(uFrameSize, frameSize.toFloat())

        GLES30.glUniform3f(
            uChannelMix,
            recipe.channelMix.r,
            recipe.channelMix.g,
            recipe.channelMix.b,
        )
        GLES30.glUniform1f(uLift, recipe.lift)
        GLES30.glUniform1f(uGamma, recipe.gamma)
        GLES30.glUniform1f(uGain, recipe.gain)
        GLES30.glUniform1f(uContrast, recipe.contrast)
        GLES30.glUniform1f(uClarity, recipe.clarity)
        GLES30.glUniform1f(uClarityRadius, recipe.clarityRadius)
        GLES30.glUniform1f(uHalation, recipe.halation)
        GLES30.glUniform1f(uHalationThreshold, recipe.halationThreshold)
        GLES30.glUniform1f(uHalationRadius, recipe.halationRadius)
        GLES30.glUniform1f(uGrain, recipe.grain)
        GLES30.glUniform1f(uGrainSize, recipe.grainSize)
        GLES30.glUniform1f(uGrainDetail, grainDetail)
        GLES30.glUniform1f(uToning, recipe.toning)
        GLES30.glUniform1f(uVignette, recipe.vignette)

        Quad.draw(aPosition, aTexCoord, flipY = flipY)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        Gl.checkError("look")
    }

    fun release() {
        if (program != 0) GLES30.glDeleteProgram(program)
    }
}
