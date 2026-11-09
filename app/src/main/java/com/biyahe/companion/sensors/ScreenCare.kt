package com.biyahe.companion.sensors

import android.view.WindowManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** What the light + proximity sensors say about the phone's surroundings. */
data class ScreenCareState(
    val lux: Float? = null,
    val isNear: Boolean = false
) {
    /** Night or inside a tunnel. */
    val isDark: Boolean get() = lux != null && lux < DARK_LUX

    /** Direct sunlight. */
    val isSunlight: Boolean get() = lux != null && lux >= SUN_LUX

    /** Covered and dark = in a pocket or bag (if there is no light sensor, proximity alone decides). */
    val inPocket: Boolean get() = isNear && (lux == null || lux < POCKET_LUX)

    companion object {
        const val DARK_LUX = 10f
        const val POCKET_LUX = 15f
        const val SUN_LUX = 2000f
    }
}

object ScreenCare {
    private const val DARK_BRIGHTNESS = 0.2f
    private const val POCKET_BRIGHTNESS = 0.01f

    /** Window brightness 0..1, or -1 (BRIGHTNESS_OVERRIDE_NONE) to leave the system setting alone. */
    fun targetBrightness(state: ScreenCareState, autoBrightness: Boolean, pauseInPocket: Boolean): Float = when {
        pauseInPocket && state.inPocket -> POCKET_BRIGHTNESS
        autoBrightness && state.isDark -> DARK_BRIGHTNESS
        autoBrightness && state.isSunlight -> 1f
        else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
}

class ScreenCareController(private val hub: SensorHub) {
    fun observe(): Flow<ScreenCareState> = combine(
        hub.ambientLux().map<Float, Float?> { it }.onStart { emit(null) },
        hub.proximityNear().onStart { emit(false) }
    ) { lux, near -> ScreenCareState(lux, near) }.distinctUntilChanged()
}
