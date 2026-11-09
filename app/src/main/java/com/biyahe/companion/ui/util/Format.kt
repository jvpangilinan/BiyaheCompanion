package com.biyahe.companion.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

fun formatKm(km: Double): String = String.format(Locale.US, "%.1f km", km)

fun formatDate(ms: Long): String = SimpleDateFormat("EEE, d MMM yyyy · h:mm a", Locale.getDefault()).format(Date(ms))

fun formatShortDate(ms: Long): String = SimpleDateFormat("d MMM · h:mm a", Locale.getDefault()).format(Date(ms))
