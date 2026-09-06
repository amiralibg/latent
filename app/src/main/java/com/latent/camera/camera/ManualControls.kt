package com.latent.camera.camera

import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.CaptureRequestOptions

/**
 * The manual overrides currently in force. A null field is that parameter on auto,
 * which is why the toggle is per-parameter rather than one global switch: you can pin
 * the shutter and leave the ISO to the meter, or lock focus and leave exposure alone.
 *
 * One honest limitation lives here, and the UI has to tell the truth about it. Camera2
 * has no half-manual auto-exposure: `CONTROL_AE_MODE_OFF` turns off metering for ISO
 * *and* shutter together. So when you take one of the two, the other is not still being
 * metered — it is **held** at whatever the meter last decided. Focus is genuinely
 * independent; exposure is not, and pretending otherwise would make the readout lie.
 */
data class ManualControls(
    val iso: Int? = null,
    val exposureTimeNs: Long? = null,
    val focusDioptres: Float? = null,
    /**
     * White-balance preset, one of `CameraMetadata.CONTROL_AWB_MODE_*`. Null is auto.
     * A preset also releases any [awbLocked] freeze — the two are different answers to
     * "which white balance" and holding both would be a lie about which one is on.
     */
    val wbPreset: Int? = null,
    /**
     * Freeze the auto white balance where it is. Only meaningful while [wbPreset] is
     * null. For a B&W camera this is the control that matters most: the channel mix
     * grades whatever AWB decided, so a drifting WB silently re-grades the sky
     * between frames of one series.
     */
    val awbLocked: Boolean = false,
) {

    val isExposureManual: Boolean get() = iso != null || exposureTimeNs != null
    val isFocusManual: Boolean get() = focusDioptres != null
    val isWbManual: Boolean get() = wbPreset != null || awbLocked
    val isAnyManual: Boolean get() = isExposureManual || isFocusManual || isWbManual

    /**
     * Build the request overrides.
     *
     * [held] supplies the value for whichever exposure parameter is still on auto —
     * see the class note. Passing the live metered values means switching to manual
     * picks up exactly the exposure you were already looking at.
     */
    fun toCaptureRequestOptions(
        held: MeteredValues,
        capabilities: CameraCapabilities,
    ): CaptureRequestOptions {
        val builder = CaptureRequestOptions.Builder()

        if (isExposureManual && capabilities.supportsManualSensor) {
            val sensitivity = iso
                ?: held.iso
                ?: capabilities.isoRange?.first
                ?: DEFAULT_ISO
            val exposure = exposureTimeNs
                ?: held.exposureTimeNs
                ?: DEFAULT_EXPOSURE_NS

            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CameraMetadata.CONTROL_AE_MODE_OFF,
            )
            builder.setCaptureRequestOption(
                CaptureRequest.SENSOR_SENSITIVITY,
                capabilities.isoRange?.let { sensitivity.coerceIn(it.first, it.last) }
                    ?: sensitivity,
            )
            builder.setCaptureRequestOption(
                CaptureRequest.SENSOR_EXPOSURE_TIME,
                capabilities.exposureTimeRangeNs
                    ?.let { exposure.coerceIn(it.first, it.last) }
                    ?: exposure,
            )
            // Without this the sensor caps the exposure at the current frame duration
            // and a one-second hand-held night frame silently comes back at 1/30.
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, exposure)
        }

        val dioptres = focusDioptres
        if (dioptres != null && capabilities.supportsManualFocus) {
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_OFF,
            )
            builder.setCaptureRequestOption(
                CaptureRequest.LENS_FOCUS_DISTANCE,
                dioptres.coerceIn(0f, capabilities.minFocusDistanceDioptres),
            )
        }

        // White balance is independent of the exposure block above: AWB keeps metering
        // while locked or preset, and neither state touches ISO, shutter or focus.
        // Keys are emitted only while engaged — back on auto the request says nothing
        // and the camera returns to its defaults, which is also what releases a lock.
        val preset = wbPreset
        if (preset != null && capabilities.supportsManualWb) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, preset)
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, false)
        } else if (awbLocked && wbPreset == null && capabilities.supportsAwbLock) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
        }

        return builder.build()
    }

    companion object {
        /**
         * The presets worth a dial stop, in the order they sit on it — daylight first,
         * tungsten last, the way the light cools through the day. Modes the device did
         * not report are filtered out by the caller, never added as dead stops.
         */
        val WbPresets: List<Pair<Int, String>> = listOf(
            CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT to "DAYLIGHT",
            CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT to "CLOUDY",
            CameraMetadata.CONTROL_AWB_MODE_SHADE to "SHADE",
            CameraMetadata.CONTROL_AWB_MODE_TWILIGHT to "DUSK",
            CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT to "FLUORO",
            CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT to "TUNGSTEN",
        )

        fun wbLabel(preset: Int): String =
            WbPresets.firstOrNull { it.first == preset }?.second ?: "WB"

        private const val DEFAULT_ISO = 100
        /** 1/60s — the fallback when the device reports nothing to start from. */
        private const val DEFAULT_EXPOSURE_NS = 16_666_666L
    }
}
