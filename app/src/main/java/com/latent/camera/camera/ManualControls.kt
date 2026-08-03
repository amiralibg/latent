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
) {

    val isExposureManual: Boolean get() = iso != null || exposureTimeNs != null
    val isFocusManual: Boolean get() = focusDioptres != null
    val isAnyManual: Boolean get() = isExposureManual || isFocusManual

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

        return builder.build()
    }

    private companion object {
        const val DEFAULT_ISO = 100
        /** 1/60s — the fallback when the device reports nothing to start from. */
        const val DEFAULT_EXPOSURE_NS = 16_666_666L
    }
}
