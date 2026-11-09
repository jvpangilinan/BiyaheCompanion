package com.biyahe.companion.ui.sensorcheck

import com.biyahe.companion.sensors.SensorKind

/**
 * How each sensor is introduced on stage: a plain-language title, the group it belongs to, a
 * one-line pitch, and its place in the demo order (1 to 11). Change the words here and the
 * list and every test screen follow.
 */
data class SensorStory(val order: Int, val group: String, val stageTitle: String, val pitch: String)

fun SensorKind.story(): SensorStory = when (this) {
    SensorKind.GPS -> SensorStory(1, "THE RIDE", "Sensor for Driving", "Tracks your route, your speed and how far you went.")
    SensorKind.ACCELEROMETER -> SensorStory(2, "THE RIDE", "Sensor for Bumps", "Feels every pothole and rough patch. The biggest part of the Comfort Score.")
    SensorKind.GYROSCOPE -> SensorStory(3, "THE RIDE", "Sensor for Sharp Turns", "Catches swerving and hard turns.")
    SensorKind.MAGNETOMETER -> SensorStory(4, "THE RIDE", "Sensor for Direction", "The compass: which way you are heading.")
    SensorKind.BAROMETER -> SensorStory(5, "THE SURROUNDINGS", "Sensor for Weather", "Falling air pressure is an early sign of rain.")
    SensorKind.MICROPHONE -> SensorStory(6, "THE SURROUNDINGS", "Sensor for Noise", "Measures how loud the ride is. Sound is never recorded.")
    SensorKind.STEP_COUNTER -> SensorStory(7, "THE WALK", "Sensor for Walking", "Counts the steps to and from the stop.")
    SensorKind.CAMERA -> SensorStory(8, "REPORTING", "Sensor for Hazards", "Photograph a pothole or a flood and pin it on the map.")
    SensorKind.AMBIENT_LIGHT -> SensorStory(9, "THE SCREEN", "Sensor for Night and Tunnels", "Dims the screen in the dark, brightens it in the sun.")
    SensorKind.PROXIMITY -> SensorStory(10, "THE SCREEN", "Sensor for Pockets", "Knows when the phone is in a pocket and rests the screen.")
    SensorKind.FINGERPRINT -> SensorStory(11, "SECURITY", "Sensor for Privacy", "Only you can open your trip history.")
}

/** The demo order, 1 to 11. */
val storyOrder: List<SensorKind> = SensorKind.entries.sortedBy { it.story().order }

/** The sensor to demo after this one, or null after the last. */
fun SensorKind.nextInStory(): SensorKind? = storyOrder.getOrNull(storyOrder.indexOf(this) + 1)
