package com.latent.camera.gl

import android.content.res.AssetManager
import android.opengl.GLES30
import com.latent.camera.look.Recipe

/**
 * Where a rendered frame is going, which decides which way up it has to be written.
 *
 * This is the one genuine asymmetry between the two paths, and it is about
 * presentation, not about the look: a window surface shows its first row at the top,
 * while `glReadPixels` reads the bottom row first.
 *
 * It has to be handled explicitly rather than left to cancel out. Left implicit, the
 * two paths build their intermediate texture vertically mirrored from each other,
 * the final images still match — and every stage that is a function of position
 * silently disagrees. Grain lands mirrored; vignette gets away with it only because
 * it happens to be symmetric.
 */
internal enum class Presentation {
    /** A window surface, consumed top-down. */
    Screen,

    /** An offscreen buffer about to be read bottom-up. */
    Readback,
}

/**
 * The whole processing chain, in one place, used by both render paths.
 *
 * This class exists so that "preview and capture do the same thing" is enforced by
 * there being one implementation rather than two that are supposed to match. The
 * preview processor and the offscreen renderer differ in where their frames come
 * from and where the result goes; everything in between is here.
 *
 *   pass 1  source -> square 2D texture, cropped and rotated  (blit.frag)
 *   mipmaps of that texture, which is where stages 3 and 4 get their blur
 *   pass 2  graded frame -> caller's framebuffer               (latent.frag)
 *
 * Confined to the thread whose EGL context it was created on.
 */
internal class LookPipeline(assets: AssetManager, source: TextureSource) {

    private val blit = BlitProgram(assets, source)
    private val look = LookProgram(assets)
    private val intermediate = FrameBuffer()

    /**
     * Grade [sourceTexture] into the framebuffer named by [targetFramebuffer] (0 for
     * the default framebuffer, which is what a window surface renders into).
     *
     * [texTransform] maps the quad's texture coordinates onto the source: the
     * SurfaceTexture matrix on the preview path, a centre-crop-and-rotate matrix on
     * the capture path. [side] is the square output size.
     *
     * A window surface carries the sensor's aspect ratio rather than the square
     * frame, so [viewportX]/[viewportY] centre the square render inside it with the
     * letterbox cleared by the caller. They are ignored for FBO targets, which are
     * already side×side.
     */
    fun render(
        sourceTexture: Int,
        texTransform: FloatArray,
        side: Int,
        recipe: Recipe,
        targetFramebuffer: Int,
        presentation: Presentation,
        viewportX: Int = 0,
        viewportY: Int = 0,
        grainDetail: Float = 1f,
    ) {
        // Normalise: whichever way the source arrives, the intermediate always ends
        // up with texture coordinate t=0 on the top of the image. Every stage that
        // reads vTexCoord therefore means the same thing on both paths.
        val topDown = presentation == Presentation.Screen

        intermediate.resize(side)
        intermediate.bind()
        blit.draw(sourceTexture, texTransform, flipY = topDown)
        intermediate.generateMipmaps()

        // ...and flip back on the way out, so the frame still reaches the screen the
        // right way up. Geometry only — the look shader is untouched by this.
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, targetFramebuffer)
        if (targetFramebuffer == 0) {
            GLES30.glViewport(viewportX, viewportY, side, side)
        } else {
            GLES30.glViewport(0, 0, side, side)
        }
        look.draw(intermediate.texture, side, recipe, flipY = topDown, grainDetail = grainDetail)
    }

    fun release() {
        intermediate.release()
        look.release()
        blit.release()
    }
}
