package com.gojungparkjo.routetracker

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class StepCounter(context: Context):SensorEventListener {

    private val sensorManager: SensorManager = context
        .getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    var onNewStepCount :((Int)->Unit)? = null

    var currentSteps = 0

    fun start(){
        sensorManager.registerListener(
            this, stepDetector,
            SensorManager.SENSOR_DELAY_FASTEST
        )
    }
    fun stop(){
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            if (event.values[0] == 1.0f) {
                currentSteps++
                onNewStepCount?.invoke(currentSteps)
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
}