package com.latent.camera.gl

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLUtils
import com.latent.camera.look.Recipe
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The capture half of the pipeline: renders a full-resolution frame through the same
 * [LookPipeline] the preview uses, into an EGL pbuffer, and reads the result back as
 * a square bitmap.
 *
 * This exists because the effect is attached to PREVIEW only. `ImageCapture` hands
 * back an unprocessed original — which is the point, it is what gets saved untouched
 * and what a re-grade later reads from — so the processed output has to be rendered
 * here instead.
 *
 * Thread confined. Create it on the thread that will call [render] and call [release]
 * from that same thread. Setup is lazy and then reused: one EGL context for the life
 * of the renderer, not one per shot.
 */
internal class OffscreenRenderer(private val assets: AssetManager) {

    private var eglCore: EglCore? = null
    private var pipeline: LookPipeline? = null
    private var pbuffer: EGLSurface? = null
    private var output = FrameBuffer()
    private var maxTextureSize = 0

    /** Largest square this device's GL driver can render. Forces EGL setup. */
    fun maxOutputSide(): Int {
        ensureInitialised()
        return maxTextureSize
    }

    /**
     * Grade [source] and return the square 1:1 output.
     *
     * [rotationDegrees] is the clockwise rotation needed to bring the frame upright;
     * it is baked into the pixels, so the caller should write EXIF orientation as
     * normal rather than passing the rotation on.
     *
     * The returned bitmap is caller-owned. [source] is not recycled.
     */
    fun render(source: Bitmap, rotationDegrees: Int, recipe: Recipe): Bitmap {
        ensureInitialised()
        val chain = requireNotNull(pipeline)
        // Re-assert the context: a thread can host more than one, and the golden
        // test drives both render paths from a single thread.
        requireNotNull(eglCore).makeCurrent(requireNotNull(pbuffer))

        val side = SquareCrop.side(source.width, source.height).coerceAtMost(maxTextureSize)
        if (side <= 0) throw GlException("Source frame is empty")

        val texture = Gl.createTexture(GLES30.GL_TEXTURE_2D)
        val target = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)

        try {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, source, 0)
            Gl.checkError("texImage2D")
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

            // Render into a texture-backed FBO rather than the pbuffer: the pbuffer
            // is only 1x1 and exists to keep the context current, and resizing it per
            // shot would mean tearing down EGL surfaces on every frame.
            output.resize(side)
            chain.render(
                sourceTexture = texture,
                texTransform = SquareCrop.texTransform(
                    source.width,
                    source.height,
                    rotationDegrees,
                ),
                side = side,
                recipe = recipe,
                targetFramebuffer = output.framebufferName,
                presentation = Presentation.Readback,
            )

            readInto(target, side)
        } catch (e: Throwable) {
            target.recycle()
            throw e
        } finally {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            Gl.deleteTexture(texture)
        }

        return target
    }

    /**
     * `glReadPixels` returns rows bottom-up while a Bitmap stores them top-down, and
     * the quad already draws the source upside down (see [Quad]). The two cancel, so
     * the buffer can be copied straight in.
     */
    private fun readInto(target: Bitmap, side: Int) {
        val pixels = ByteBuffer
            .allocateDirect(side * side * 4)
            .order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, side, side, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
        Gl.checkError("glReadPixels")
        pixels.rewind()
        target.copyPixelsFromBuffer(pixels)
    }

    private fun ensureInitialised() {
        if (eglCore != null) return
        val core = EglCore()
        try {
            val surface = core.createPbufferSurface(1, 1)
            core.makeCurrent(surface)
            pbuffer = surface
            pipeline = LookPipeline(assets, TextureSource.Texture2d)
            maxTextureSize = Gl.maxTextureSize()
            eglCore = core
        } catch (e: Throwable) {
            pipeline?.release()
            pipeline = null
            pbuffer?.let(core::releaseSurface)
            pbuffer = null
            core.release()
            throw e
        }
    }

    fun release() {
        output.release()
        pipeline?.release()
        pipeline = null
        val core = eglCore ?: return
        pbuffer?.let(core::releaseSurface)
        pbuffer = null
        core.release()
        eglCore = null
    }
}
