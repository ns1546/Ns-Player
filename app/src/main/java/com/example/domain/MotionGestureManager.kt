package com.example.domain

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class MotionGestureManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val prefs = context.getSharedPreferences("gesture_prefs", Context.MODE_PRIVATE)

    private val _isShakeEnabled = MutableStateFlow(prefs.getBoolean("shake_to_skip", true))
    val isShakeEnabled: StateFlow<Boolean> = _isShakeEnabled.asStateFlow()

    private val _isAirWaveEnabled = MutableStateFlow(prefs.getBoolean("air_wave_skip", true))
    val isAirWaveEnabled: StateFlow<Boolean> = _isAirWaveEnabled.asStateFlow()

    private val _hasProximitySensor = MutableStateFlow(proximitySensor != null)
    val hasProximitySensor: StateFlow<Boolean> = _hasProximitySensor.asStateFlow()

    var onShakeTriggered: (() -> Unit)? = null
    var onAirWaveTriggered: (() -> Unit)? = null

    // Shake detection thresholds
    private var lastShakeTime = 0L
    private val shakeThreshold = 14.5f // m/s^2 above gravity

    // Proximity wave detection
    private var proximityNearTime = 0L
    private var isCurrentlyNear = false

    fun startListening() {
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        proximitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    fun setShakeEnabled(enabled: Boolean) {
        _isShakeEnabled.value = enabled
        prefs.edit().putBoolean("shake_to_skip", enabled).apply()
    }

    fun setAirWaveEnabled(enabled: Boolean) {
        _isAirWaveEnabled.value = enabled
        prefs.edit().putBoolean("air_wave_skip", enabled).apply()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                if (!_isShakeEnabled.value) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat() - SensorManager.GRAVITY_EARTH
                val now = System.currentTimeMillis()

                if (gForce > shakeThreshold && (now - lastShakeTime > 1200L)) {
                    lastShakeTime = now
                    triggerHaptic(longArrayOf(0, 40, 60, 40))
                    onShakeTriggered?.invoke()
                }
            }
            Sensor.TYPE_PROXIMITY -> {
                if (!_isAirWaveEnabled.value) return
                val distance = event.values[0]
                val maxRange = event.sensor.maximumRange
                val isNear = distance < maxRange && distance < 4.0f
                val now = System.currentTimeMillis()

                if (isNear) {
                    if (!isCurrentlyNear) {
                        isCurrentlyNear = true
                        proximityNearTime = now
                    }
                } else {
                    if (isCurrentlyNear) {
                        isCurrentlyNear = false
                        val waveDuration = now - proximityNearTime
                        // A wave is typically between 80ms and 800ms
                        if (waveDuration in 70..850) {
                            triggerHaptic(longArrayOf(0, 70))
                            onAirWaveTriggered?.invoke()
                        }
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerHaptic(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
