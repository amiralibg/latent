package com.latent.camera.camera

import android.Manifest
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Not an assertion — a report.
 *
 * Phase 3 is the phase where device-specific weirdness lives, and every control in it
 * is conditional on hardware that varies wildly between phones. This prints what this
 * device actually claims, so the controls are built against fact rather than against
 * what the documentation implies.
 */
@RunWith(AndroidJUnit4::class)
class CameraCapabilitiesProbeTest {

    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test
    fun reportBackCameraCapabilities() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = ProcessCameraProvider.getInstance(context).get(10, TimeUnit.SECONDS)

        Log.i(TAG, "device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        Log.i(TAG, "bindable cameras: ${provider.availableCameraInfos.size}")

        provider.availableCameraInfos.forEachIndexed { index, info ->
            val camera2 = Camera2CameraInfo.from(info)
            val facing = camera2.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
            Log.i(TAG, "--- camera[$index] id=${camera2.cameraId} facing=$facing")

            val capabilities = CameraCapabilities.probe(info)
            Log.i(
                TAG,
                "  level=${capabilities.hardwareLevelName} " +
                    "manualSensor=${capabilities.supportsManualSensor}",
            )
            Log.i(
                TAG,
                "  iso=${capabilities.isoRange} " +
                    "exposureNs=${capabilities.exposureTimeRangeNs}",
            )
            Log.i(
                TAG,
                "  minFocusDioptres=${capabilities.minFocusDistanceDioptres} " +
                    "calibrated=${capabilities.focusDistanceCalibrated}",
            )
            Log.i(
                TAG,
                "  ev=${capabilities.evRange} step=${capabilities.evStepEv} " +
                    "zoom=${capabilities.minZoomRatio}..${capabilities.maxZoomRatio}",
            )
            Log.i(
                TAG,
                "  focal=${capabilities.focalLengthMm}mm " +
                    "(${capabilities.equivalent35mm}mm eq) f/${capabilities.apertureF}",
            )

            val focals = camera2.getCameraCharacteristic(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS,
            )
            Log.i(TAG, "  availableFocalLengths=${focals?.joinToString()}")

            if (android.os.Build.VERSION.SDK_INT >= 28) {
                val manager = context.getSystemService(android.content.Context.CAMERA_SERVICE)
                    as android.hardware.camera2.CameraManager
                val physicalIds = runCatching {
                    manager.getCameraCharacteristics(camera2.cameraId).physicalCameraIds
                }.getOrNull()
                Log.i(TAG, "  physicalCameraIds=$physicalIds")

                physicalIds?.forEach { id ->
                    runCatching {
                        val characteristics = manager.getCameraCharacteristics(id)
                        val focal = characteristics
                            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        val sensor = characteristics
                            .get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                        Log.i(
                            TAG,
                            "    physical[$id] focal=${focal?.joinToString()} sensor=$sensor",
                        )
                    }
                }
            }
        }

        val back = provider
            .availableCameraInfos
            .firstOrNull {
                Camera2CameraInfo.from(it)
                    .getCameraCharacteristic(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            }
            ?: CameraSelector.DEFAULT_BACK_CAMERA.filter(provider.availableCameraInfos).first()

        Log.i(TAG, "=== enumerated lenses ===")
        Lens.enumerate(context, back).forEach { lens ->
            Log.i(
                TAG,
                "  ${lens.label} ${lens.focalLabel} " +
                    "(${lens.focalLengthMm}mm, ratio ${lens.zoomRatio})",
            )
        }
    }

    private companion object {
        const val TAG = "CapabilitiesProbe"
    }
}
