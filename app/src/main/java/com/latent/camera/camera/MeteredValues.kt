package com.latent.camera.camera

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the camera actually settled on for the frame you are looking at.
 *
 * Auto exposure is a black box until you ask it what it decided. Two things need the
 * answer: the readout under the viewfinder, and manual mode — which has to start from
 * the metered values rather than from an arbitrary default, or every switch to manual
 * throws the exposure away and asks you to find it again.
 */
data class MeteredValues(
    val iso: Int? = null,
    val exposureTimeNs: Long? = null,
    val focusDistanceDioptres: Float? = null,
    val afLocked: Boolean = false,
) {
    /** "1/125", "2\"" — shutter speed as it is written on a dial. */
    val shutterLabel: String? get() = exposureTimeNs?.let(::formatShutter)

    companion object {
        fun formatShutter(ns: Long): String {
            val seconds = ns / 1_000_000_000.0
            if (seconds >= 1.0) {
                val rounded = (seconds * 10).toLong() / 10.0
                return if (rounded % 1.0 == 0.0) "${rounded.toInt()}\"" else "$rounded\""
            }
            val denominator = (1.0 / seconds).toInt()
            // Snap to the nearest stop people actually read, so the display does not
            // flicker between 1/124 and 1/126 while the meter breathes.
            val nearest = STANDARD_DENOMINATORS.minByOrNull {
                kotlin.math.abs(kotlin.math.ln(it.toDouble()) - kotlin.math.ln(denominator.toDouble()))
            } ?: denominator
            return "1/$nearest"
        }

        private val STANDARD_DENOMINATORS = intArrayOf(
            2, 3, 4, 6, 8, 10, 15, 20, 30, 45, 60, 90, 125, 180, 250, 350,
            500, 750, 1000, 1500, 2000, 3000, 4000, 6000, 8000, 16000, 32000,
        ).toList()
    }
}

/**
 * Reads the metadata CameraX does not surface.
 *
 * Attached to the preview request through `Camera2Interop`, so it sees every frame the
 * viewfinder does. Results arrive at frame rate; publishing all of them would recompose
 * the readout sixty times a second to show a number that has not changed, so they are
 * throttled.
 */
class CaptureResultMonitor : CameraCaptureSession.CaptureCallback() {

    private val _metered = MutableStateFlow(MeteredValues())
    val metered: StateFlow<MeteredValues> = _metered.asStateFlow()

    @Volatile
    private var lastPublishedAt = 0L

    override fun onCaptureCompleted(
        session: CameraCaptureSession,
        request: CaptureRequest,
        result: TotalCaptureResult,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPublishedAt < PUBLISH_INTERVAL_MS) return
        lastPublishedAt = now

        val afState = result.get(CaptureResult.CONTROL_AF_STATE)
        _metered.value = MeteredValues(
            iso = result.get(CaptureResult.SENSOR_SENSITIVITY),
            exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
            focusDistanceDioptres = result.get(CaptureResult.LENS_FOCUS_DISTANCE),
            afLocked = afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED,
        )
    }

    private companion object {
        /** Five updates a second reads as live without costing a recomposition a frame. */
        const val PUBLISH_INTERVAL_MS = 200L
    }
}
