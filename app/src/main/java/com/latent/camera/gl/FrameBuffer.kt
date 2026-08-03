package com.latent.camera.gl

import android.opengl.GLES30

/**
 * A texture-backed framebuffer, reallocated only when the size it is asked for
 * changes. The look pipeline renders its intermediate frame here.
 *
 * The colour texture carries a full mip chain, because that is where clarity and
 * halation get their blurred copy of the image from.
 */
internal class FrameBuffer {

    var texture = 0
        private set
    var size = 0
        private set

    /** GL name, for a caller that needs to render into this as a target. */
    val framebufferName: Int get() = framebuffer

    private var framebuffer = 0
    private var mipmaps = true

    /**
     * Allocate for a [side]x[side] frame, reusing the existing one where possible.
     * [mipmaps] is required for the look intermediate (clarity / halation blur);
     * graded preview targets used by peaking leave it off.
     */
    fun resize(side: Int, mipmaps: Boolean = true) {
        if (side == size && framebuffer != 0 && this.mipmaps == mipmaps) return
        release()

        texture = Gl.createTexture(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, side, side, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null,
        )
        // Trilinear when mips exist, so a non-integer blur radius interpolates
        // between levels instead of stepping as the slider moves.
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MIN_FILTER,
            if (mipmaps) GLES30.GL_LINEAR_MIPMAP_LINEAR else GLES30.GL_LINEAR,
        )
        Gl.checkError("allocate intermediate (${side}x$side)")
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        this.mipmaps = mipmaps

        val ids = IntArray(1)
        GLES30.glGenFramebuffers(1, ids, 0)
        framebuffer = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, texture, 0,
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            release()
            throw GlException("Framebuffer incomplete: 0x${Integer.toHexString(status)}")
        }

        size = side
    }

    fun bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glViewport(0, 0, size, size)
    }

    fun generateMipmaps() {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    fun release() {
        if (framebuffer != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            framebuffer = 0
        }
        Gl.deleteTexture(texture)
        texture = 0
        size = 0
        mipmaps = true
    }
}
