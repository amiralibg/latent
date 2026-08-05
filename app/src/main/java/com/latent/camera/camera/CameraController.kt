package com.latent.camera.camera

import android.content.Context
import android.media.MediaActionSound
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.latent.camera.capture.CaptureNaming
import com.latent.camera.capture.CaptureStore
import com.latent.camera.capture.CaptureWriter
import com.latent.camera.data.CaptureRecord
import com.latent.camera.data.LatentDatabase
import com.latent.camera.gl.LatentEffect
import com.latent.camera.gl.PreviewProcessor
import com.latent.camera.look.Recipe
import com.latent.camera.settings.AidsState
import com.latent.camera.settings.SaveMode
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * The single owner of camera hardware state. Compose observes [state] and calls
 * [capture]; nothing in the UI layer touches CameraX directly.
 *
 * The look shader is attached as an effect on PREVIEW only, so `ImageCapture` keeps
 * handing back unprocessed frames. That is deliberate and load bearing — see
 * CLAUDE.md, "Capture architecture".
 */
class CameraController(context: Context) {

    private val appContext: Context = context.applicationContext
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(appContext)

    /** Capture callbacks land here, never on the main thread. Invariant 5. */
    private val captureExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "latent-capture") }

    /**
     * Grading, encoding and the MediaStore write. Single threaded on purpose: it
     * owns an EGL context, and it caps how many full-resolution frames can be in
     * memory at once. Shots queue here; the shutter does not.
     */
    private val processingExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "latent-processing") }
    private val processingScope =
        CoroutineScope(SupervisorJob() + processingExecutor.asCoroutineDispatcher())

    /** Camera commands and the timer. Short-lived work only; nothing blocking. */
    private val controlScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val previewProcessor = PreviewProcessor(appContext.assets)
    private val resultMonitor = CaptureResultMonitor()

    /** Created on the processing thread, because it owns the offscreen renderer. */
    private var writer: CaptureWriter? = null

    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var camera2Control: Camera2CameraControl? = null

    private var timerJob: Job? = null
    private var focusFadeJob: Job? = null

    /**
     * Held so the save mode can rebind. Asking for a DNG changes the `ImageCapture`
     * output format, and output format is fixed at bind time — there is no way to
     * switch it on a live use case.
     */
    private var boundLifecycleOwner: LifecycleOwner? = null
    private var boundSurfaceProvider: Preview.SurfaceProvider? = null

    /** The output format the currently bound `ImageCapture` was built with. */
    private var boundRawOutput = false

    /**
     * Supplied by the viewport at bind time. `PreviewView` builds metering points that
     * already account for its scale type, the square crop and the display rotation —
     * three transforms it would be a mistake to reimplement here.
     */
    private var meteringPointFactory: (() -> MeteringPointFactory)? = null

    /**
     * CameraX does not own a shutter click. When silent mode is off we play the
     * platform sound ourselves; when it is on, nothing — that is the whole feature.
     */
    private val shutterSound = MediaActionSound().also {
        it.load(MediaActionSound.SHUTTER_CLICK)
    }

    private val deviceLevel = DeviceLevel(appContext)

    init {
        processingScope.launch {
            writer = CaptureWriter(appContext, LatentDatabase.get(appContext).captures())
        }
        // The readout under the viewfinder is whatever the sensor last reported, and
        // manual mode seeds itself from the same numbers.
        controlScope.launch {
            resultMonitor.metered.collect { metered ->
                _state.update { it.copy(metered = metered) }
            }
        }
        controlScope.launch {
            deviceLevel.rollDegrees.collect { roll ->
                _state.update { it.copy(rollDegrees = roll) }
            }
        }
        previewProcessor.onHistogram = { bins ->
            _state.update { it.copy(histogramBins = bins) }
        }
    }

    /** The active look. Preview and the next capture always read this one value. */
    val recipe: Recipe get() = _state.value.recipe

    fun setRecipe(recipe: Recipe) {
        previewProcessor.recipe = recipe
        _state.update { it.copy(recipe = recipe) }
    }

    /**
     * Preview-only shooting aids. Capture never reads this — peaking and zebra stay
     * off the saved file by construction.
     */
    fun setAids(aids: AidsState) {
        previewProcessor.aids = aids
        _state.update {
            it.copy(
                aids = aids,
                histogramBins = if (aids.histogram) it.histogramBins else null,
            )
        }
        if (aids.level) deviceLevel.start() else deviceLevel.stop()
    }

    /**
     * The device is portrait-locked, so the sensor rotation has to come from the
     * accelerometer or every landscape frame is saved with the wrong EXIF orientation.
     */
    private val orientationListener = object : OrientationEventListener(appContext) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val rotation = when {
                orientation >= 315 || orientation < 45 -> Surface.ROTATION_0
                orientation < 135 -> Surface.ROTATION_270
                orientation < 225 -> Surface.ROTATION_180
                else -> Surface.ROTATION_90
            }
            imageCapture?.targetRotation = rotation
        }
    }

    /**
     * The save mode, which the shutter reads and — when it asks for a DNG — the
     * binding depends on. Changing between modes that need RAW and modes that do not
     * rebinds the camera; changing between the two JPEG modes does not.
     */
    fun setSaveMode(mode: SaveMode) {
        if (mode == _state.value.saveMode) return
        _state.update { it.copy(saveMode = mode) }
        if (wantsRawOutput(mode) != boundRawOutput) controlScope.launch { rebind() }
    }

    /** True only where the mode asks for a DNG *and* the camera can deliver one. */
    private fun wantsRawOutput(mode: SaveMode): Boolean =
        mode.wantsDng && _state.value.capabilities?.supportsRawJpeg == true

    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        meteringPoints: () -> MeteringPointFactory,
    ) {
        meteringPointFactory = meteringPoints
        boundLifecycleOwner = lifecycleOwner
        boundSurfaceProvider = surfaceProvider

        val provider = try {
            ProcessCameraProvider.getInstance(appContext).await(mainExecutor)
        } catch (e: Exception) {
            Log.e(TAG, "Camera provider unavailable", e)
            _state.update { it.copy(error = "Camera unavailable") }
            return
        }
        cameraProvider = provider

        val hasFront = try {
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        } catch (e: Exception) {
            Log.w(TAG, "Could not query the front camera", e)
            false
        }
        _state.update { it.copy(hasFrontCamera = hasFront) }

        bindUseCases(provider, lifecycleOwner, surfaceProvider)
    }

    private fun selectorFor(front: Boolean): CameraSelector =
        if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

    /**
     * Turn the camera round.
     *
     * Everything dialled in by hand is dropped on the way: ISO, shutter and focus are
     * values for a specific sensor and lens, and carrying a back-camera exposure onto
     * a front camera that has a fixed aperture and a different range would silently
     * clamp to something nobody chose. Zoom goes back to 1x for the same reason. The
     * recipe is the exception and stays — a look is about the picture, not the lens.
     */
    fun toggleFacing() {
        if (!_state.value.hasFrontCamera) return
        val front = !_state.value.frontFacing
        _state.update {
            it.copy(
                frontFacing = front,
                isReady = false,
                manual = ManualControls(),
                zoomRatio = 1f,
                exposureIndex = 0,
                focus = null,
            )
        }
        controlScope.launch { rebind() }
    }

    /**
     * Re-open the camera with the same surface. The save mode and the facing switch
     * call this, and only when they have to: a rebind drops frames, so it is not
     * something to do on any setting that could be applied to a live session instead.
     */
    private suspend fun rebind() {
        val provider = cameraProvider ?: return
        val owner = boundLifecycleOwner ?: return
        val surface = boundSurfaceProvider ?: return
        bindUseCases(provider, owner, surface)
    }

    private suspend fun bindUseCases(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ) {
        // 4:3 is the native sensor field of view on effectively every phone. Request
        // the full frame and crop to square downstream — asking the camera for 1:1
        // would throw away sensor area and, on many devices, silently fall back.
        val fullSensorFrame = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY

        val previewBuilder = Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(fullSensorFrame)
                    .build(),
            )

        // The only way to see what auto exposure decided. CameraX surfaces zoom and EV
        // but not ISO, shutter or focus distance, and all three are on screen in Phase 3.
        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(resultMonitor)

        val preview = previewBuilder.build()
            .apply { setSurfaceProvider(surfaceProvider) }

        // Decided before the use case is built and remembered after, because output
        // format cannot be changed on a bound ImageCapture — see [setSaveMode].
        val rawOutput = wantsRawOutput(_state.value.saveMode)

        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(fullSensorFrame)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build(),
            )
            .apply {
                if (rawOutput) setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
            }
            .build()

        previewProcessor.recipe = _state.value.recipe

        val useCases = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(capture)
            .addEffect(
                LatentEffect(previewProcessor) { throwable ->
                    Log.e(TAG, "Preview effect failed", throwable)
                    _state.update { it.copy(error = "Preview processing failed") }
                },
            )
            .build()

        val bound = try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                selectorFor(_state.value.frontFacing),
                useCases,
            )
        } catch (e: Exception) {
            Log.e(TAG, "bindToLifecycle failed", e)
            // A RAW binding is the one that plausibly fails on a device that claimed
            // to support it. Fall back to JPEG rather than leaving a dead viewfinder.
            if (rawOutput) {
                Log.w(TAG, "RAW binding refused; falling back to JPEG")
                _state.update { it.copy(saveMode = SaveMode.BwAndOriginal) }
                bindUseCases(provider, lifecycleOwner, surfaceProvider)
            } else if (_state.value.frontFacing) {
                // The front camera claimed to exist and then would not open. Going
                // back to the lens we know works beats leaving a dead viewfinder.
                Log.w(TAG, "Front camera refused the binding; returning to the back")
                _state.update { it.copy(frontFacing = false) }
                bindUseCases(provider, lifecycleOwner, surfaceProvider)
                // After the rebind, not before: a successful bind clears the error
                // field, and this is the one message the user needs to keep.
                _state.update { it.copy(error = "Front camera unavailable") }
            } else {
                _state.update { it.copy(error = "Could not open the camera") }
            }
            return
        }

        camera = bound
        camera2Control = Camera2CameraControl.from(bound.cameraControl)
        imageCapture = capture
        boundRawOutput = rawOutput

        val capabilities = CameraCapabilities.probe(bound.cameraInfo)
        val lenses = Lens.enumerate(appContext, bound.cameraInfo)
        Log.i(TAG, "level=${capabilities.hardwareLevelName} lenses=${lenses.map(Lens::label)}")

        if (orientationListener.canDetectOrientation()) orientationListener.enable()
        if (_state.value.aids.level) deviceLevel.start()

        _state.update {
            it.copy(
                isReady = true,
                error = null,
                capabilities = capabilities,
                lenses = lenses,
                zoomRatio = bound.cameraInfo.zoomState.value?.zoomRatio ?: 1f,
                exposureIndex = bound.cameraInfo.exposureState.exposureCompensationIndex,
            )
        }

        // A rebind hands back a camera at its defaults. Anything the user had dialled
        // in belongs to them, not to the session, so it goes back on.
        reapplyControls()
    }

    /**
     * Close the camera. Called when the viewfinder leaves the screen — a preview left
     * streaming into a surface that has been released costs battery and heat for a
     * picture nobody is looking at, and CameraX will complain about it besides.
     *
     * Safe to call with a capture in flight: on the ordinary path the frame's bytes
     * were copied out of CameraX before any of this, and on the DNG path an aborted
     * capture arrives as an error the callback already knows how to settle.
     */
    fun unbind() {
        cameraProvider?.unbindAll()
        camera = null
        camera2Control = null
        imageCapture = null
        _state.update { it.copy(isReady = false, focus = null) }
    }

    /** Push the held manual controls and EV onto a freshly bound camera. */
    private fun reapplyControls() {
        val state = _state.value
        val capabilities = state.capabilities ?: return
        if (state.manual.isAnyManual) {
            camera2Control?.setCaptureRequestOptions(
                state.manual.toCaptureRequestOptions(state.metered, capabilities),
            )
        }
        if (state.exposureIndex != 0 && capabilities.supportsEv) {
            camera?.cameraControl?.setExposureCompensationIndex(state.exposureIndex)
        }
        if (state.zoomRatio != 1f) camera?.cameraControl?.setZoomRatio(state.zoomRatio)
    }

    // ------------------------------------------------------------------ lens and zoom

    /**
     * Select a lens. On a modern phone the ultra-wide and the telephoto are not
     * separately bindable — they sit behind one logical camera that switches lens as
     * the zoom ratio crosses its crossover point. So selecting a lens *is* setting a
     * zoom ratio, which is why there is one code path for both. See [Lens].
     */
    fun selectLens(lens: Lens) = setZoomRatio(lens.zoomRatio)

    fun setZoomRatio(ratio: Float) {
        val control = camera?.cameraControl ?: return
        val capabilities = _state.value.capabilities ?: return
        val clamped = ratio.coerceIn(capabilities.minZoomRatio, capabilities.maxZoomRatio)
        control.setZoomRatio(clamped)
        _state.update { it.copy(zoomRatio = clamped) }
    }

    /** Pinch. [scale] is the gesture's incremental scale factor, not an absolute ratio. */
    fun zoomBy(scale: Float) = setZoomRatio(_state.value.zoomRatio * scale)

    // ------------------------------------------------------------------ focus and exposure

    /**
     * Tap to focus. Meters focus and exposure at the point and hands both back to auto
     * a few seconds later, the way a tap on any camera app behaves.
     *
     * Coordinates are pixels in the viewport, which is the same space `PreviewView`
     * measures in. The point is built by that view's own factory rather than by hand:
     * the viewport is a square centre crop of a 4:3 frame on a rotating display, and
     * every one of those transforms has to be undone to land on the right patch of
     * sensor. `PreviewView` already knows all of them.
     */
    fun focusAt(xPx: Float, yPx: Float, widthPx: Int, heightPx: Int) =
        meterAt(xPx, yPx, widthPx, heightPx, lock = false)

    /**
     * Long press. Focus stays where you put it; exposure keeps metering. That is the
     * decoupling — a locked frame that still survives the light changing.
     */
    fun lockFocusAt(xPx: Float, yPx: Float, widthPx: Int, heightPx: Int) =
        meterAt(xPx, yPx, widthPx, heightPx, lock = true)

    private fun meterAt(xPx: Float, yPx: Float, widthPx: Int, heightPx: Int, lock: Boolean) {
        val control = camera?.cameraControl ?: return
        val factory = meteringPointFactory?.invoke() ?: return
        val point = factory.createPoint(xPx, yPx)

        // Under manual focus a focus request would be ignored anyway, so meter
        // exposure alone rather than appearing to do nothing.
        val flags = if (_state.value.manual.isFocusManual) {
            FocusMeteringAction.FLAG_AE
        } else {
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        }

        val action = FocusMeteringAction.Builder(point, flags)
            .apply { if (lock) disableAutoCancel() }
            .build()

        control.startFocusAndMetering(action)
        // The reticle is drawn in the viewport's own space, so it is stored normalised
        // and survives a rotation or a resize.
        showReticle(
            x = if (widthPx > 0) xPx / widthPx else 0.5f,
            y = if (heightPx > 0) yPx / heightPx else 0.5f,
            locked = lock,
        )
    }

    /** Hand focus and exposure back to the camera. */
    fun clearFocusLock() {
        camera?.cameraControl?.cancelFocusAndMetering()
        focusFadeJob?.cancel()
        _state.update { it.copy(focus = null) }
    }

    private fun showReticle(x: Float, y: Float, locked: Boolean) {
        focusFadeJob?.cancel()
        _state.update { it.copy(focus = FocusPoint(x, y, locked)) }
        // A lock is a state you can see; an ordinary tap is an acknowledgement that
        // should get out of the way of the frame.
        if (!locked) {
            focusFadeJob = controlScope.launch {
                delay(RETICLE_FADE_MS)
                _state.update { it.copy(focus = null) }
            }
        }
    }

    /**
     * EV compensation, in steps of the device's own increment. Has no effect while
     * exposure is manual — auto exposure is off, and there is nothing to compensate.
     */
    fun setExposureIndex(index: Int) {
        val control = camera?.cameraControl ?: return
        val range = _state.value.capabilities?.evRange ?: return
        val clamped = index.coerceIn(range.first, range.last)
        control.setExposureCompensationIndex(clamped)
        _state.update { it.copy(exposureIndex = clamped) }
    }

    /**
     * Move exposure by whole steps from wherever it is. The vertical drag on the
     * viewfinder works in deltas rather than absolutes because the gesture has no
     * fixed track to map a position onto — it can start anywhere on the frame.
     *
     * @return true if the value actually moved, so the caller can tick the haptics
     *   at the edges of the range instead of buzzing against a wall.
     */
    fun nudgeExposure(steps: Int): Boolean {
        if (steps == 0) return false
        val before = _state.value.exposureIndex
        setExposureIndex(before + steps)
        return _state.value.exposureIndex != before
    }

    // ------------------------------------------------------------------ manual mode

    fun setManualIso(iso: Int?) = updateManual { it.copy(iso = iso) }

    fun setManualShutter(exposureTimeNs: Long?) =
        updateManual { it.copy(exposureTimeNs = exposureTimeNs) }

    fun setManualFocus(dioptres: Float?) = updateManual { it.copy(focusDioptres = dioptres) }

    /** Everything back to auto, in one move. */
    fun clearManual() = updateManual { ManualControls() }

    private fun updateManual(transform: (ManualControls) -> ManualControls) {
        val capabilities = _state.value.capabilities ?: return
        val manual = transform(_state.value.manual)
        _state.update { it.copy(manual = manual) }

        val control = camera2Control ?: return
        // The held values are the ones the meter last reported: taking the shutter
        // manually should not also change the ISO out from under you.
        control.setCaptureRequestOptions(
            manual.toCaptureRequestOptions(_state.value.metered, capabilities),
        )

        // Leaving manual focus without clearing the request would strand the lens at
        // its last distance, because AF_MODE is only reset by the request that set it.
        if (!manual.isFocusManual) camera?.cameraControl?.cancelFocusAndMetering()
    }

    // ------------------------------------------------------------------ shutter

    fun setTimerSeconds(seconds: Int) = _state.update { it.copy(timerSeconds = seconds) }

    fun setSilentShutter(silent: Boolean) = _state.update { it.copy(silentShutter = silent) }

    /**
     * Fire the shutter, after the self-timer if one is set. Pressing again during a
     * countdown cancels it — a timer you cannot call off is a timer you stop using.
     */
    fun shutter() {
        if (timerJob?.isActive == true) {
            cancelTimer()
            return
        }
        val seconds = _state.value.timerSeconds
        if (seconds <= 0) {
            capture()
            return
        }
        timerJob = controlScope.launch {
            for (remaining in seconds downTo 1) {
                _state.update { it.copy(countdown = remaining) }
                delay(1000)
            }
            _state.update { it.copy(countdown = 0) }
            capture()
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _state.update { it.copy(countdown = 0) }
    }

    /**
     * Take the frame now. Returns immediately: the frame is copied out of the capture
     * buffer and everything else — grading, encoding, MediaStore, the Room row —
     * happens on the processing thread. The button is never disabled while a shot is
     * in flight.
     */
    fun capture() {
        val capture = imageCapture ?: return
        val recipe = _state.value.recipe
        val capturedAt = System.currentTimeMillis()
        val shutterAt = SystemClock.elapsedRealtime()

        if (!_state.value.silentShutter) {
            shutterSound.play(MediaActionSound.SHUTTER_CLICK)
        }

        _state.update { it.copy(inFlight = it.inFlight + 1) }

        if (boundRawOutput) {
            captureRawJpeg(capture, recipe, capturedAt)
        } else {
            captureInMemory(capture, recipe, capturedAt, shutterAt)
        }
    }

    /**
     * The ordinary path. Frames come back as bytes and never touch disk until the
     * writer puts them there, which is what keeps a shot to a single file write per
     * output rather than a write and a read-back.
     */
    private fun captureInMemory(
        capture: ImageCapture,
        recipe: Recipe,
        capturedAt: Long,
        shutterAt: Long,
    ) {
        val saveMode = _state.value.saveMode

        capture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // Copy and close before anything else: the buffer this holds is
                    // one of a small pool, and the next shot needs it back.
                    val jpeg = try {
                        image.toJpegBytes()
                    } finally {
                        image.close()
                    }
                    Log.d(TAG, "shutter to frame: ${SystemClock.elapsedRealtime() - shutterAt}ms")

                    // The frame exists now. Counting it here rather than after the
                    // grade is the difference between the app acknowledging a shot
                    // in a few hundred milliseconds and appearing to stall for a
                    // second while it encodes.
                    _state.update { it.copy(framesThisSession = it.framesThisSession + 1) }
                    processingScope.launch {
                        finish { writer?.save(jpeg, recipe, capturedAt, saveMode) }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Capture failed", exception)
                    _state.update {
                        it.copy(
                            inFlight = (it.inFlight - 1).coerceAtLeast(0),
                            error = "Capture failed",
                        )
                    }
                }
            },
        )
    }

    /**
     * The DNG path. A DNG can only come out of the two-file `takePicture` overload,
     * which writes both files itself — so here CameraX owns the MediaStore rows and
     * the writer reads the JPEG back to grade it.
     *
     * The callback fires once per file with no ordering guarantee, so the two results
     * are told apart by file name and collected before the grade starts. Both land on
     * the single-threaded capture executor, which is what makes that bookkeeping safe
     * without a lock.
     */
    private fun captureRawJpeg(capture: ImageCapture, recipe: Recipe, capturedAt: Long) {
        val stem = CaptureNaming.stem(java.util.Date(capturedAt))
        val resolver = appContext.contentResolver

        fun options(fileName: String, dng: Boolean) = ImageCapture.OutputFileOptions
            .Builder(resolver, CaptureStore.collection, CaptureStore.pendingValues(fileName, true, dng))
            .build()

        capture.takePicture(
            // Primary is the DNG and secondary the JPEG — that order is the API's,
            // not a preference, and swapping them silently writes each to the other's
            // file name.
            options(CaptureNaming.dngFileName(stem), dng = true),
            options(CaptureNaming.originalFileName(stem), dng = false),
            captureExecutor,
            object : ImageCapture.OnImageSavedCallback {
                private var jpegUri: Uri? = null
                private var dngUri: Uri? = null
                private var remaining = 2
                private var settled = false

                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    val uri = results.savedUri
                    if (uri != null) {
                        val name = CaptureStore.displayName(appContext, uri)
                        if (name != null && CaptureNaming.isOriginalJpeg(name)) {
                            jpegUri = uri
                        } else {
                            dngUri = uri
                        }
                    }
                    remaining--
                    if (remaining <= 0) settle()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "RAW capture failed", exception)
                    // Do not wait for the other half: if one file failed the pair is
                    // already incomplete, and a shot that never resolves would leak
                    // the in-flight count forever.
                    settle()
                }

                private fun settle() {
                    if (settled) return
                    settled = true

                    val source = jpegUri
                    if (source == null) {
                        _state.update {
                            it.copy(
                                inFlight = (it.inFlight - 1).coerceAtLeast(0),
                                error = "Capture failed",
                            )
                        }
                        return
                    }
                    _state.update { it.copy(framesThisSession = it.framesThisSession + 1) }
                    val dng = dngUri
                    processingScope.launch {
                        finish { writer?.saveFromDisk(source, dng, recipe, capturedAt) }
                    }
                }
            },
        )
    }

    /**
     * Re-render a stored capture under a different recipe, from its untouched
     * original. Queued behind live captures on the same processing thread, so a
     * re-grade can never delay the next frame off the shutter.
     */
    fun regrade(record: CaptureRecord, recipe: Recipe) {
        if (!record.canRegrade) return
        _state.update { it.copy(inFlight = it.inFlight + 1) }
        processingScope.launch {
            finish { writer?.regrade(record, recipe, System.currentTimeMillis()) }
        }
    }

    /** Run a write, clear the in-flight count either way, and report what came back. */
    private suspend fun finish(write: suspend () -> Uri?) {
        val uri = try {
            write()
        } catch (e: Exception) {
            Log.e(TAG, "Could not save the capture", e)
            _state.update {
                it.copy(
                    inFlight = (it.inFlight - 1).coerceAtLeast(0),
                    error = "Could not save",
                )
            }
            return
        }

        _state.update {
            it.copy(
                inFlight = (it.inFlight - 1).coerceAtLeast(0),
                lastCaptureUri = uri ?: it.lastCaptureUri,
                error = null,
            )
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun shutdown() {
        orientationListener.disable()
        deviceLevel.stop()
        controlScope.cancel()
        cameraProvider?.unbindAll()
        previewProcessor.release()
        shutterSound.release()
        captureExecutor.shutdown()
        processingScope.launch { writer?.release() }
        processingExecutor.shutdown()
    }

    private companion object {
        const val TAG = "CameraController"
        /** Long enough to see where you tapped, short enough not to sit on the frame. */
        const val RETICLE_FADE_MS = 2500L
    }
}

/** Where the user last asked the camera to focus, normalised to the viewport. */
data class FocusPoint(val x: Float, val y: Float, val locked: Boolean)

/** Everything the viewfinder needs to know about the hardware. */
data class CameraState(
    val isReady: Boolean = false,
    /** Captures still encoding. Display only — it must never gate the shutter. */
    val inFlight: Int = 0,
    val framesThisSession: Int = 0,
    val lastCaptureUri: Uri? = null,
    val recipe: Recipe = Recipe.Default,
    val error: String? = null,

    /** Null until the camera is bound; every Phase 3 control is built from it. */
    val capabilities: CameraCapabilities? = null,
    val lenses: List<Lens> = emptyList(),
    val zoomRatio: Float = 1f,
    val exposureIndex: Int = 0,
    val focus: FocusPoint? = null,
    val manual: ManualControls = ManualControls(),
    val metered: MeteredValues = MeteredValues(),
    val timerSeconds: Int = 0,
    /** Seconds left on the self-timer; zero when it is not running. */
    val countdown: Int = 0,
    val silentShutter: Boolean = true,
    val saveMode: SaveMode = SaveMode.BwAndOriginal,

    /** Which way the bound camera points. Back on launch, always. */
    val frontFacing: Boolean = false,
    /** False on a device with no front camera, which hides the flip control entirely. */
    val hasFrontCamera: Boolean = false,

    val aids: AidsState = AidsState(),
    /** Device roll in degrees; 0 is level. */
    val rollDegrees: Float = 0f,
    /** Normalised luminance bins for the live histogram, preview-only. */
    val histogramBins: FloatArray? = null,
) {

    /** EV compensation as photographers write it: -0.7, +1.3. */
    val exposureEv: Float
        get() = exposureIndex * (capabilities?.evStepEv ?: 0f)

    /** The lens the current zoom ratio is sitting on, if it is sitting on one. */
    val activeLens: Lens?
        get() = lenses.minByOrNull { abs(it.zoomRatio - zoomRatio) }
            ?.takeIf { abs(it.zoomRatio - zoomRatio) < LENS_MATCH_TOLERANCE }

    /**
     * True when the chosen shutter speed is slower than the one-over-focal-length
     * rule allows. Not an error — a warning, because sometimes that is the shot.
     */
    val handheldWarning: Boolean
        get() {
            val capabilities = capabilities ?: return false
            val exposure = manual.exposureTimeNs ?: return false
            return exposure > capabilities.handheldLimitNs
        }

    private companion object {
        /**
         * How close the zoom has to be to a lens's ratio to count as being on it.
         * Absolute, not proportional: lens ratios are far enough apart that 0.05
         * separates them cleanly, and the ultra-wide at 0.6x needs a tolerance that
         * does not shrink to nothing just because the number is small.
         */
        const val LENS_MATCH_TOLERANCE = 0.05f
    }
}

/** JPEG frames arrive as a single plane; this is the whole file, EXIF included. */
private fun ImageProxy.toJpegBytes(): ByteArray {
    val buffer = planes[0].buffer
    return ByteArray(buffer.remaining()).also(buffer::get)
}

private suspend fun <T> ListenableFuture<T>.await(executor: Executor): T =
    suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(get())
            } catch (e: ExecutionException) {
                cont.resumeWithException(e.cause ?: e)
            } catch (e: Throwable) {
                cont.resumeWithException(e)
            }
        }, executor)
        cont.invokeOnCancellation { cancel(false) }
    }
