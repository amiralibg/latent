package com.latent.camera.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Continuous device tilt for the electronic horizon. Separate from
 * [OrientationEventListener], which only buckets to 90° for EXIF.
 */
class DeviceLevel(context: Context) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _rollDegrees = MutableStateFlow(0f)
    val rollDegrees: StateFlow<Float> = _rollDegrees.asStateFlow()

    private var active = false

    fun start() {
        val sensor = sensor ?: return
        if (active) return
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        active = true
    }

    fun stop() {
        if (!active) return
        manager.unregisterListener(this)
        active = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        // Portrait: roll is the left/right tip that levels a horizon line.
        val roll = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
        // Quantise lightly so the horizon doesn't shimmer on a steady hand.
        val rounded = (roll * 5f).roundToInt() / 5f
        if (kotlin.math.abs(rounded - _rollDegrees.value) >= 0.2f) {
            _rollDegrees.value = rounded
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
