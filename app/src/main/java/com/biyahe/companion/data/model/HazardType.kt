package com.biyahe.companion.data.model

/** Categories a commuter can pick when photographing a road problem. */
enum class HazardType(val label: String) {
    POTHOLE("Pothole"),
    FLOODING("Flooding"),
    ROAD_DAMAGE("Road damage"),
    OBSTRUCTION("Obstruction"),
    OTHER("Other");

    companion object {
        fun fromName(name: String): HazardType = entries.firstOrNull { it.name == name } ?: OTHER
    }
}
