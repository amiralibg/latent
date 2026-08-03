package com.latent.camera.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface

/**
 * An EGL display, config and ES 3.0 context, plus the two kinds of surface the app
 * draws into: window surfaces for the preview, and a pbuffer for the offscreen
 * capture render. One config serves both, which is deliberate — the two paths must
 * not be able to drift onto different pixel formats.
 *
 * Not thread safe. Every instance belongs to exactly one thread for its whole life.
 */
class EglCore {

    private val display: EGLDisplay
    private val config: EGLConfig
    private val context: EGLContext

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) throw GlException("eglGetDisplay failed")

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw GlException("eglInitialize failed")
        }

        config = chooseConfig()

        val attributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, attributes, 0)
        if (context == EGL14.EGL_NO_CONTEXT) throw GlException("eglCreateContext failed")
    }

    private fun chooseConfig(): EGLConfig {
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            // Both bits on one config: the preview draws to a window surface and the
            // capture path to a pbuffer, and they have to agree pixel for pixel.
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val ok = EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0)
        if (!ok || count[0] == 0) throw GlException("No RGBA8888 ES3 EGL config available")
        return configs[0] ?: throw GlException("eglChooseConfig returned null")
    }

    fun createWindowSurface(surface: Surface): EGLSurface {
        val attributes = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, attributes, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) throw GlException("eglCreateWindowSurface failed")
        return eglSurface
    }

    fun createPbufferSurface(width: Int, height: Int): EGLSurface {
        val attributes = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE,
        )
        val eglSurface = EGL14.eglCreatePbufferSurface(display, config, attributes, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) throw GlException("eglCreatePbufferSurface failed")
        return eglSurface
    }

    fun makeCurrent(surface: EGLSurface) {
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            throw GlException("eglMakeCurrent failed")
        }
    }

    fun makeNothingCurrent() {
        EGL14.eglMakeCurrent(
            display,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT,
        )
    }

    /**
     * Without this the consumer timestamps frames on arrival, and preview playback
     * jitters under load.
     */
    fun setPresentationTime(surface: EGLSurface, nanoseconds: Long) {
        EGLExt.eglPresentationTimeANDROID(display, surface, nanoseconds)
    }

    fun swapBuffers(surface: EGLSurface): Boolean = EGL14.eglSwapBuffers(display, surface)

    fun releaseSurface(surface: EGLSurface) {
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
    }

    fun release() {
        if (display == EGL14.EGL_NO_DISPLAY) return
        makeNothingCurrent()
        EGL14.eglDestroyContext(display, context)
        EGL14.eglReleaseThread()
        EGL14.eglTerminate(display)
    }
}
