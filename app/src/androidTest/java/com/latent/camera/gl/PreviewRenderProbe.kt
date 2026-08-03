package com.latent.camera.gl

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.media.ImageReader
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.latent.camera.look.Recipe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs a bitmap through the preview render path so it can be compared with the
 * capture path.
 *
 * This is deliberately not a mock of the preview: it is the real [LookProgram] with
 * the real `samplerExternalOES` fragment shader, sampling a real SurfaceTexture,
 * drawing into a real EGL window surface. The only substitutions are the frame
 * source — a canvas instead of the camera — and the consumer, an ImageReader
 * instead of the PreviewView, so the result can be read back.
 *
 * Confined to the thread that constructs it.
 */
internal class PreviewRenderProbe(assets: AssetManager, private val size: Int) {

    private val callbackThread = HandlerThread("probe-frames").apply { start() }
    private val callbackHandler = Handler(callbackThread.looper)

    private val eglCore = EglCore()
    private val reader: ImageReader =
        ImageReader.newInstance(size, size, PixelFormat.RGBA_8888, 2)
    private val eglSurface = eglCore.createWindowSurface(reader.surface)
    private val pipeline: LookPipeline
    private val oesTexture: Int
    private val surfaceTexture: SurfaceTexture
    private val inputSurface: Surface

    private val texMatrix = FloatArray(16)

    init {
        eglCore.makeCurrent(eglSurface)
        oesTexture = Gl.createTexture(Gl.TEXTURE_EXTERNAL_OES)
        pipeline = LookPipeline(assets, TextureSource.ExternalOes)
        // Attaches to whichever context is current, hence the makeCurrent above.
        surfaceTexture = SurfaceTexture(oesTexture).apply { setDefaultBufferSize(size, size) }
        inputSurface = Surface(surfaceTexture)
    }

    fun render(source: Bitmap, recipe: Recipe): Bitmap {
        require(source.width == size && source.height == size) {
            "Probe is ${size}x$size but the frame is ${source.width}x${source.height}"
        }

        val available = CountDownLatch(1)
        surfaceTexture.setOnFrameAvailableListener({ available.countDown() }, callbackHandler)

        val canvas = inputSurface.lockCanvas(null)
        try {
            canvas.drawBitmap(source, 0f, 0f, null)
        } finally {
            inputSurface.unlockCanvasAndPost(canvas)
        }
        check(available.await(FRAME_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "The SurfaceTexture never received the test frame"
        }

        eglCore.makeCurrent(eglSurface)
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(texMatrix)

        // Framebuffer 0 is the window surface, exactly as the real preview renders.
        pipeline.render(
            sourceTexture = oesTexture,
            texTransform = texMatrix,
            side = size,
            recipe = recipe,
            targetFramebuffer = 0,
            presentation = Presentation.Screen,
        )
        eglCore.swapBuffers(eglSurface)

        return readBack()
    }

    /**
     * ImageReader hands back rows in screen order, top first, so this needs no flip.
     * The capture path reads with `glReadPixels` and compensates there instead —
     * see [OffscreenRenderer].
     */
    private fun readBack(): Bitmap {
        var image = reader.acquireNextImage()
        var waited = 0L
        while (image == null && waited < FRAME_TIMEOUT_SECONDS * 1000) {
            Thread.sleep(POLL_MILLIS)
            waited += POLL_MILLIS
            image = reader.acquireNextImage()
        }
        checkNotNull(image) { "The ImageReader never produced the rendered frame" }

        try {
            val plane = image.planes[0]
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val padding = rowStride - pixelStride * size

            // A padded row stride is normal; decoding it into a tight bitmap needs
            // either a wider bitmap or a per-row copy, and the wider bitmap is cheaper.
            val padded = Bitmap.createBitmap(
                rowStride / pixelStride,
                size,
                Bitmap.Config.ARGB_8888,
            )
            padded.copyPixelsFromBuffer(plane.buffer)
            return if (padding == 0) {
                padded
            } else {
                Bitmap.createBitmap(padded, 0, 0, size, size).also { padded.recycle() }
            }
        } finally {
            image.close()
        }
    }

    fun release() {
        surfaceTexture.setOnFrameAvailableListener(null)
        inputSurface.release()
        surfaceTexture.release()
        pipeline.release()
        Gl.deleteTexture(oesTexture)
        eglCore.releaseSurface(eglSurface)
        eglCore.release()
        reader.close()
        callbackThread.quitSafely()
    }

    private companion object {
        const val FRAME_TIMEOUT_SECONDS = 5L
        const val POLL_MILLIS = 20L
    }
}
