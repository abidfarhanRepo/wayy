package com.wayy.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

class DeviceOrientationManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _currentBearing = MutableStateFlow(0f)
    val currentBearing: StateFlow<Float> = _currentBearing

    private var gravityReading = FloatArray(3)
    private var geomagneticReading = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    fun start() {
        if (accelerometer == null || magnetometer == null) {
            Log.w("WayyOrientation", "Sensors unavailable for orientation")
            return
        }
        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )
        sensorManager.registerListener(
            this,
            magnetometer,
            SensorManager.SENSOR_DELAY_UI
        )
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                gravityReading = lowPass(event.values.clone(), gravityReading)
                hasGravity = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                geomagneticReading = lowPass(event.values.clone(), geomagneticReading)
                hasGeomagnetic = true
            }
        }

        if (!hasGravity || !hasGeomagnetic) return

        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            inclinationMatrix,
            gravityReading,
            geomagneticReading
        )
        if (!success) return

        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val azimuthRadians = orientation[0]
        val azimuthDegrees = Math.toDegrees(azimuthRadians.toDouble()).toFloat()
        val normalized = ((azimuthDegrees + 360) % 360)
        _currentBearing.value = normalized
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun lowPass(input: FloatArray, output: FloatArray): FloatArray {
        val alpha = 0.2f
        if (output.isEmpty()) return input
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
        return output
    }
}
