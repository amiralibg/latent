package com.latent.camera.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * One physical lens on the back of the phone, as something you can actually select.
 *
 * CameraX's default selector only knows front from back, so the ultra-wide and the
 * telephoto are invisible to it. They are reachable, but on a modern phone almost
 * never as separately bindable cameras: the manufacturer exposes one *logical*
 * camera that switches between physical lenses internally as the zoom ratio crosses
 * their crossover points.
 *
 * So a lens here is identified by the zoom ratio that selects it. That is not a
 * workaround — it is how the hardware is actually driven, which is why picking a lens
 * and setting a zoom are the same gesture in this app.
 */
data class Lens(
    val physicalId: String?,
    val focalLengthMm: Float,
    val equivalent35mm: Float,
    /** Zoom ratio on the logical camera that lands on this lens. */
    val zoomRatio: Float,
) {
    /** How the ratio is written on a camera app: "0.6×", "1×", "3×". */
    val label: String
        get() {
            val rounded = (zoomRatio * 10).roundToInt() / 10f
            return if (abs(rounded - rounded.roundToInt()) < 0.05f) {
                "${rounded.roundToInt()}×"
            } else {
                "$rounded×"
            }
        }

    /** "23mm", as a 35mm photographer reads it. */
    val focalLabel: String get() = "${equivalent35mm.roundToInt()}mm"

    companion object {
        private const val TAG = "Lens"
        private const val FULL_FRAME_DIAGONAL_MM = 43.267f

        /**
         * Work out the real lenses behind [cameraInfo].
         *
         * The ratio between two lenses is the ratio of their **35mm equivalents**,
         * not of their focal lengths. Phone lenses sit on sensors of wildly different
         * sizes — on a Galaxy S25 FE the 7.0mm telephoto is on a sensor less than half
         * the width of the 5.4mm main, so comparing focal lengths alone calls it a
         * 1.3x when it is really a 3x. Getting this wrong puts every lens button on
         * the wrong zoom.
         */
        fun enumerate(context: Context, cameraInfo: CameraInfo): List<Lens> {
            val capabilities = CameraCapabilities.probe(cameraInfo)
            val lenses = physicalLenses(context, cameraInfo, capabilities)
                .ifEmpty { fromZoomRange(capabilities) }

            return lenses
                .filter { it.zoomRatio in capabilities.minZoomRatio..capabilities.maxZoomRatio }
                .distinctBy { (it.zoomRatio * 20).roundToInt() }
                .sortedBy { it.zoomRatio }
        }

        private fun physicalLenses(
            context: Context,
            cameraInfo: CameraInfo,
            capabilities: CameraCapabilities,
        ): List<Lens> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()
            if (capabilities.equivalent35mm <= 0f) return emptyList()

            val logicalId = Camera2CameraInfo.from(cameraInfo).cameraId
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return emptyList()

            val physicalIds = try {
                manager.getCameraCharacteristics(logicalId).physicalCameraIds
            } catch (e: Exception) {
                Log.w(TAG, "Could not read physical camera ids for $logicalId", e)
                return emptyList()
            }
            if (physicalIds.isEmpty()) return emptyList()

            return physicalIds.mapNotNull { id ->
                try {
                    val characteristics = manager.getCameraCharacteristics(id)
                    val focal = characteristics
                        .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        ?.firstOrNull()
                        ?: return@mapNotNull null
                    val sensor = characteristics
                        .get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                        ?: return@mapNotNull null

                    val equivalent = equivalent35mm(focal, sensor.width, sensor.height)
                    Lens(
                        physicalId = id,
                        focalLengthMm = focal,
                        equivalent35mm = equivalent,
                        zoomRatio = equivalent / capabilities.equivalent35mm,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping physical camera $id", e)
                    null
                }
            }
        }

        /**
         * Fallback for a device that will not name its physical cameras: offer the
         * wide end and 1x, and nothing else. Anything further would be a digital crop
         * dressed up as a lens.
         */
        private fun fromZoomRange(capabilities: CameraCapabilities): List<Lens> =
            listOf(capabilities.minZoomRatio, 1f).map { ratio ->
                Lens(
                    physicalId = null,
                    focalLengthMm = capabilities.focalLengthMm * ratio,
                    equivalent35mm = capabilities.equivalent35mm * ratio,
                    zoomRatio = ratio,
                )
            }

        fun equivalent35mm(focalLengthMm: Float, sensorWidthMm: Float, sensorHeightMm: Float): Float {
            val diagonal = hypot(sensorWidthMm, sensorHeightMm)
            if (diagonal <= 0f) return focalLengthMm
            return focalLengthMm * (FULL_FRAME_DIAGONAL_MM / diagonal)
        }
    }
}
