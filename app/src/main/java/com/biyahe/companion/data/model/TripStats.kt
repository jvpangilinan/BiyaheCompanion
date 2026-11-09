package com.biyahe.companion.data.model

/** Totals shown on the Home dashboard and top of History. */
data class TripStats(
    val tripCount: Int = 0,
    val averageScore: Double = 0.0,
    val totalDistanceMeters: Double = 0.0,
    val totalSteps: Int = 0
)
