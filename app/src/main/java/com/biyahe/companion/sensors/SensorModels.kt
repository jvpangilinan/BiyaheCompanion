package com.biyahe.companion.sensors

import kotlin.math.sqrt

/** One 3-axis reading (accelerometer m/s², gyroscope rad/s, magnetometer µT). */
data class Vec3(val x: Float, val y: Float, val z: Float, val timestampMs: Long) {
    val magnitude: Double get() = sqrt((x * x + y * y + z * z).toDouble())
}

/** The 11 components from the Biyahe Companion architecture table. */
enum class SensorKind(val title: String, val role: String) {
    ACCELEROMETER("Accelerometer", "Detects bumps and jolts (rough roads)"),
    GYROSCOPE("Gyroscope", "Detects sharp turns and swerving"),
    MAGNETOMETER("Magnetometer", "Gives travel heading / compass direction"),
    GPS("GPS", "Records route, speed, and distance"),
    BAROMETER("Barometer", "Detects pressure drops hinting at rain or storms"),
    MICROPHONE("Microphone", "Measures noise level of the ride"),
    CAMERA("Camera", "Captures photos of potholes and floods"),
    AMBIENT_LIGHT("Ambient Light", "Auto-adjusts brightness; detects night or tunnels"),
    PROXIMITY("Proximity", "Detects phone in pocket, then pauses the screen"),
    STEP_COUNTER("Step Counter", "Counts walking to and from stops or stations"),
    FINGERPRINT("Fingerprint", "Secures access to trip history")
}

private val COMPASS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

/** 0..360 degrees -> "N", "NE", ... */
fun compassLabel(degrees: Float): String = COMPASS[(((degrees % 360f) + 360f) % 360f + 22.5f).div(45f).toInt() % 8]
