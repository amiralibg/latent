package com.latent.camera.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture

/**
 * What this particular camera will actually let us do.
 *
 * Everything in Phase 3 is optional hardware. A LEGACY device has no manual sensor
 * control at all, a fixed-focus lens reports a minimum focus distance of zero, and
 * plenty of phones lie about one thing or another. Controls are built from this
 * rather than assumed, so an unsupported one is absent instead of present and inert.
 */
data class CameraCapabilities(
    val hardwareLevel: Int,
    val supportsManualSensor: Boolean,
    val isoRange: IntRange?,
    val exposureTimeRangeNs: LongRange?,
    /**
     * Nearest focus, in dioptres (1/metres). Zero means a fixed-focus lens, which is
     * also how a device says "no manual focus".
     */
    val minFocusDistanceDioptres: Float,
    val focusDistanceCalibrated: Boolean,
    val evRange: IntRange,
    val evStepEv: Float,
    val minZoomRatio: Float,
    val maxZoomRatio: Float,
    val focalLengthMm: Float,
    val equivalent35mm: Float,
    val apertureF: Float?,
    /**
     * True where this camera can deliver a DNG alongside the JPEG. Phase 5's DNG save
     * mode is hidden entirely where this is false — an option that silently does
     * nothing is worse than one that is not there.
     */
    val supportsRawJpeg: Boolean,
) {

    val supportsManualIso: Boolean get() = supportsManualSensor && isoRange != null
    val supportsManualShutter: Boolean get() = supportsManualSensor && exposureTimeRangeNs != null
    val supportsManualFocus: Boolean get() = minFocusDistanceDioptres > 0f
    val supportsEv: Boolean get() = evRange.first != 0 || evRange.last != 0

    /** True where the device claims no more than the baseline Camera2 guarantees. */
    val isLegacy: Boolean
        get() = hardwareLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY

    val hardwareLevelName: String
        get() = when (hardwareLevel) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN($hardwareLevel)"
        }

    /**
     * Slowest shutter that can be hand-held at this focal length, by the old
     * one-over-focal-length rule. Below this the frame is a gamble, and the app says
     * so rather than letting you find out on the computer.
     */
    val handheldLimitNs: Long
        get() = (1_000_000_000.0 / equivalent35mm.coerceAtLeast(1f)).toLong()

    companion object {

        /** 35mm frame diagonal, for working out the crop factor. */
        private const val FULL_FRAME_DIAGONAL_MM = 43.267f

        fun probe(cameraInfo: CameraInfo): CameraCapabilities {
            val camera2 = Camera2CameraInfo.from(cameraInfo)

            val hardwareLevel = camera2
                .getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY

            val capabilities = camera2
                .getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?.toList()
                .orEmpty()
            val manualSensor = capabilities.contains(
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR,
            )

            val focalLength = camera2
                .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.firstOrNull()
                ?: 0f

            val exposureState = cameraInfo.exposureState
            val evStep = exposureState.exposureCompensationStep
            val evRange = exposureState.exposureCompensationRange

            val zoomState = cameraInfo.zoomState.value

            return CameraCapabilities(
                hardwareLevel = hardwareLevel,
                supportsManualSensor = manualSensor,
                isoRange = camera2
                    .getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                    ?.toIntRange(),
                exposureTimeRangeNs = camera2
                    .getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                    ?.toLongRange(),
                minFocusDistanceDioptres = camera2
                    .getCameraCharacteristic(
                        CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE,
                    ) ?: 0f,
                focusDistanceCalibrated = camera2
                    .getCameraCharacteristic(
                        CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION,
                    )?.let {
                        it != CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED
                    } ?: false,
                evRange = evRange.lower..evRange.upper,
                evStepEv = if (evStep.denominator == 0) 0f else evStep.toFloat(),
                minZoomRatio = zoomState?.minZoomRatio ?: 1f,
                maxZoomRatio = zoomState?.maxZoomRatio ?: 1f,
                focalLengthMm = focalLength,
                equivalent35mm = equivalent35mm(camera2, focalLength),
                apertureF = camera2
                    .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                    ?.firstOrNull(),
                supportsRawJpeg = supportsRawJpeg(cameraInfo),
            )
        }

        /**
         * Ask CameraX rather than reading `REQUEST_AVAILABLE_CAPABILITIES` for RAW: a
         * camera can advertise RAW at the Camera2 level and still not support the
         * simultaneous RAW+JPEG stream combination this app needs, and CameraX is the
         * layer that knows which of the two it can actually configure.
         */
        private fun supportsRawJpeg(cameraInfo: CameraInfo): Boolean = runCatching {
            ImageCapture.getImageCaptureCapabilities(cameraInfo)
                .supportedOutputFormats
                .contains(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
        }.getOrDefault(false)

        /**
         * Focal length as a 35mm photographer reads it. The sensor's physical size
         * gives the crop factor; where the device does not report one, the focal
         * length is returned unscaled rather than guessed at.
         */
        private fun equivalent35mm(camera2: Camera2CameraInfo, focalLengthMm: Float): Float {
            val size = camera2
                .getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                ?: return focalLengthMm
            val diagonal = kotlin.math.hypot(size.width, size.height)
            if (diagonal <= 0f) return focalLengthMm
            return focalLengthMm * (FULL_FRAME_DIAGONAL_MM / diagonal)
        }

        private fun Range<Int>.toIntRange(): IntRange = lower..upper

        private fun Range<Long>.toLongRange(): LongRange = lower..upper
    }
}
