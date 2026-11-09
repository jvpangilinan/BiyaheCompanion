package com.biyahe.companion.safety

/**
 * Who is using the app on this trip.
 *
 *  DRIVING  all guards speak (speed, weaving, mount, breaks...), and the Trip screen becomes the
 *           big glanceable driving view.
 *  RIDING   a passenger (jeepney, bus, car): comfort scoring and hazards as usual; if the VEHICLE
 *           speeds it is logged quietly for reports instead of nagging the passenger.
 */
enum class TripMode(val label: String) {
    DRIVING("Driving"), RIDING("Riding");

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: DRIVING
    }
}
