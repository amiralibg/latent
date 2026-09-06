package com.latent.camera.gl

import android.content.res.AssetManager
import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import com.latent.camera.look.Recipe
import com.latent.camera.settings.AidsState
import java.util.concurrent.Executor

/**
 * The preview half of the pipeline. Camera frames arrive on a SurfaceTexture, get
 * drawn through the shared look shader, optionally through a preview-only aids pass,
 * and go out to the PreviewView.
 *
 * Capture never touches [aidsProgram] or [histogram]. That split is load-bearing.
 */
internal class PreviewProcessor(private val assets: AssetManager) : SurfaceProcessor {

    private val thread = HandlerThread("latent-gl").apply { start() }
    private val handler = Handler(thread.looper)

    /** CameraX callbacks are routed here so they serialise with rendering. */
    val executor: Executor = Executor { command -> handler.post(command) }

    /** Swapped from the UI thread when the active recipe changes. */
    @Volatile
    var recipe: Recipe = Recipe.Default

    /** Preview-only. Never read by the capture path. */
    @Volatile
    var aids: AidsState = AidsState()

    /** Latest luminance bins (0..1), or null when the histogram aid is off. */
    @Volatile
    var histogramBins: FloatArray? = null
        private set

    var onHistogram: ((FloatArray) -> Unit)? = null

    private var eglCore: EglCore? = null
    private var pipeline: LookPipeline? = null
    private var aidsProgram: AidsProgram? = null
    private var graded: FrameBuffer? = null
    private var histogram: HistogramSampler? = null
    private var placeholder: EGLSurface? = null
    private var oesTexture = 0

    private var inputTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null

    private val outputs = LinkedHashMap<SurfaceOutput, EGLSurface>()

    private val inputMatrix = FloatArray(16)
    private val outputMatrix = FloatArray(16)

    /** [outputMatrix] composed with the centre-square crop, sampled by the look pass. */
    private val previewTransform = FloatArray(16)
    private val cropTransform = FloatArray(16)

    private var released = false
    private var lastHistogramAt = 0L
    private var zebraPhase = 0f

    /**
     * Sheds the outer grain octaves when the GL thread sustainably misses vsync.
     * Capture never reads this — files always render full detail.
     */
    private val governor = PreviewQualityGovernor()

    override fun onInputSurface(request: SurfaceRequest) {
        handler.post {
            if (released) {
                request.willNotProvideSurface()
                return@post
            }
            try {
                initialiseGl()
                releaseInput()

                val texture = SurfaceTexture(oesTexture).apply {
                    setDefaultBufferSize(request.resolution.width, request.resolution.height)
                }
                val surface = Surface(texture)
                inputTexture = texture
                inputSurface = surface

                texture.setOnFrameAvailableListener({ onFrameAvailable(it) }, handler)
                request.provideSurface(surface, executor) {
                    if (inputSurface === surface) releaseInput()
                    else {
                        texture.release()
                        surface.release()
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Could not provide the preview input surface", e)
                request.willNotProvideSurface()
            }
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        handler.post {
            if (released) {
                surfaceOutput.close()
                return@post
            }
            try {
                initialiseGl()
                val core = requireNotNull(eglCore)
                val surface = surfaceOutput.getSurface(executor) {
                    outputs.remove(surfaceOutput)?.let(core::releaseSurface)
                    surfaceOutput.close()
                }
                outputs[surfaceOutput] = core.createWindowSurface(surface)
            } catch (e: Throwable) {
                Log.e(TAG, "Could not attach a preview output surface", e)
                surfaceOutput.close()
            }
        }
    }

    private fun onFrameAvailable(texture: SurfaceTexture) {
        if (released || texture !== inputTexture) return
        val core = eglCore ?: return
        val chain = pipeline ?: return
        val currentAids = aids

        try {
            texture.updateTexImage()
            texture.getTransformMatrix(inputMatrix)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Dropped a preview frame", e)
            return
        }

        zebraPhase = (zebraPhase + 0.03f) % 1f

        // Decided from the last frame's cost, applied to this one: sampling at the
        // end keeps the measurement of this frame out of its own verdict.
        val grainDetail = if (governor.reduced) 0f else 1f
        val frameStartNs = System.nanoTime()

        for ((output, eglSurface) in outputs) {
            try {
                output.updateTransformMatrix(outputMatrix, inputMatrix)
                core.makeCurrent(eglSurface)

                // The effect's output surface carries the sensor's aspect ratio,
                // but the render target is the square frame. Sample only the
                // central square of the sensor frame (the capture path crops the
                // same way), then centre that square in the surface with the
                // letterbox cleared around it — PreviewView then scales a square
                // to the square viewport and nothing is squashed.
                val outW = output.size.width
                val outH = output.size.height
                val side = minOf(outW, outH)
                val viewportX = if (outW > outH) (outW - side) / 2 else 0
                val viewportY = if (outH > outW) (outH - side) / 2 else 0

                SquareCrop.texTransformInto(cropTransform, outW, outH, 0)
                Matrix.multiplyMM(previewTransform, 0, outputMatrix, 0, cropTransform, 0)

                GLES30.glViewport(0, 0, outW, outH)
                GLES30.glClearColor(0f, 0f, 0f, 1f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

                if (currentAids.needsGradedFrame) {
                    renderWithAids(chain, side, currentAids, viewportX, viewportY, grainDetail)
                } else {
                    histogramBins = null
                    chain.render(
                        sourceTexture = oesTexture,
                        texTransform = previewTransform,
                        side = side,
                        recipe = recipe,
                        targetFramebuffer = 0,
                        presentation = Presentation.Screen,
                        viewportX = viewportX,
                        viewportY = viewportY,
                        grainDetail = grainDetail,
                    )
                }

                core.setPresentationTime(eglSurface, texture.timestamp)
                core.swapBuffers(eglSurface)
            } catch (e: Throwable) {
                Log.w(TAG, "Dropped a preview frame on one output", e)
            }
        }

        val wasReduced = governor.reduced
        governor.sample(System.nanoTime() - frameStartNs)
        if (governor.reduced != wasReduced) {
            Log.i(
                TAG,
                if (governor.reduced) "Preview grain reduced under sustained load"
                else "Preview grain restored after a healthy run",
            )
        }
    }

    private fun renderWithAids(
        chain: LookPipeline,
        side: Int,
        currentAids: AidsState,
        viewportX: Int,
        viewportY: Int,
        grainDetail: Float,
    ) {
        val gradedFb = graded ?: return
        val overlay = aidsProgram ?: return
        gradedFb.resize(side, mipmaps = false)

        chain.render(
            sourceTexture = oesTexture,
            texTransform = previewTransform,
            side = side,
            recipe = recipe,
            targetFramebuffer = gradedFb.framebufferName,
            presentation = Presentation.Screen,
            grainDetail = grainDetail,
        )

        if (currentAids.histogram) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastHistogramAt >= HISTOGRAM_INTERVAL_MS) {
                lastHistogramAt = now
                val sampler = histogram ?: HistogramSampler().also { histogram = it }
                val bins = sampler.sample(gradedFb.texture, side, overlay)
                histogramBins = bins
                onHistogram?.invoke(bins)
            }
        } else {
            histogramBins = null
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(viewportX, viewportY, side, side)
        if (currentAids.needsGlOverlay) {
            overlay.draw(gradedFb.texture, side, currentAids, zebraPhase)
        } else {
            overlay.blit(gradedFb.texture, side)
        }
    }

    private fun initialiseGl() {
        if (eglCore != null) return
        val core = EglCore()
        try {
            val surface = core.createPbufferSurface(1, 1)
            core.makeCurrent(surface)
            placeholder = surface
            oesTexture = Gl.createTexture(Gl.TEXTURE_EXTERNAL_OES)
            pipeline = LookPipeline(assets, TextureSource.ExternalOes)
            aidsProgram = AidsProgram(assets)
            graded = FrameBuffer()
            eglCore = core
        } catch (e: Throwable) {
            pipeline?.release()
            pipeline = null
            aidsProgram?.release()
            aidsProgram = null
            graded?.release()
            graded = null
            placeholder?.let(core::releaseSurface)
            placeholder = null
            core.release()
            throw e
        }
    }

    private fun releaseInput() {
        inputTexture?.setOnFrameAvailableListener(null)
        inputTexture?.release()
        inputTexture = null
        inputSurface?.release()
        inputSurface = null
    }

    fun release() {
        handler.post {
            released = true
            val core = eglCore
            outputs.forEach { (output, eglSurface) ->
                core?.releaseSurface(eglSurface)
                output.close()
            }
            outputs.clear()
            releaseInput()
            Gl.deleteTexture(oesTexture)
            oesTexture = 0
            histogram?.release()
            histogram = null
            graded?.release()
            graded = null
            aidsProgram?.release()
            aidsProgram = null
            pipeline?.release()
            pipeline = null
            core?.let {
                placeholder?.let(it::releaseSurface)
                it.release()
            }
            placeholder = null
            eglCore = null
            thread.quitSafely()
        }
    }

    private companion object {
        const val TAG = "PreviewProcessor"
        /**
         * Histogram refresh. 80ms reads back 64x64px on the GL thread every ~5 frames;
         * 150ms is still fluid for an exposure aid and halves the readback stalls and
         * the StateFlow recompositions downstream.
         */
        const val HISTOGRAM_INTERVAL_MS = 150L
    }
}
