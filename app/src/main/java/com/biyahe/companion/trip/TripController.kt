package com.biyahe.companion.trip

import com.biyahe.companion.safety.TripMode
import android.content.Context
import com.biyahe.companion.scoring.ComfortScoreCalculator
import com.biyahe.companion.scoring.WeatherWatcher
import com.biyahe.companion.sensors.StepLog
import com.biyahe.companion.service.TripService
import com.biyahe.companion.tracking.LocationTracker
import com.biyahe.companion.tracking.TripRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StartOutcome { STARTED, ALREADY_RUNNING, NEED_LOCATION_PERMISSION, GPS_OFF }

sealed interface EndOutcome {
    data class Saved(val result: TripResult) : EndOutcome
    /** Under [TripController.MIN_TRIP_MS]: treated as an accidental tap and thrown away. */
    object TooShort : EndOutcome
    object NothingToEnd : EndOutcome
}

/**
 * The Start Trip / End Trip logic from the doc's "System process":
 *
 *   Start: check permission + GPS, remember steps walked just before, start the service.
 *   End:   stop recording, compute the Comfort Score and rain warning, save the trip,
 *          then a few minutes later add the steps walked AFTER the ride.
 */
class TripController(
    private val context: Context,
    private val recorder: TripRecorder,
    private val location: LocationTracker,
    private val stepLog: StepLog,
    private val sink: TripSink,
    private val scope: CoroutineScope
) {
    private var stepsBefore = 0

    private val _lastResult = MutableStateFlow<TripResult?>(null)
    val lastResult: StateFlow<TripResult?> = _lastResult.asStateFlow()

    fun start(mode: TripMode = TripMode.DRIVING): StartOutcome {
        if (recorder.isRecording()) return StartOutcome.ALREADY_RUNNING
        if (!location.hasPermission()) return StartOutcome.NEED_LOCATION_PERMISSION
        if (!location.isGpsEnabled()) return StartOutcome.GPS_OFF

        val now = System.currentTimeMillis()
        stepsBefore = stepLog.stepsBetween(now - STEP_WINDOW_MS, now)
        _lastResult.value = null
        recorder.mode = mode
        TripService.start(context)
        return StartOutcome.STARTED
    }

    suspend fun end(): EndOutcome {
        // finished.value covers a trip the service already stopped (e.g. Android shut it down).
        val recorded = recorder.stop() ?: recorder.finished.value ?: return EndOutcome.NothingToEnd
        TripService.stop(context)

        if (recorded.durationMs < MIN_TRIP_MS) {
            recorder.consumeFinished()
            return EndOutcome.TooShort
        }

        val comfort = ComfortScoreCalculator.calculate(recorded.motion, recorded.noise, recorded.durationMs)
        val weather = WeatherWatcher.assess(recorded.pressure)
        val completed = buildCompletedTrip(recorded, comfort, weather, stepsBefore)

        val id = sink.save(completed)
        recorder.consumeFinished()

        val result = TripResult(id, completed, comfort, weather)
        _lastResult.value = result
        scheduleStepsAfter(id, recorded.endedAtMs)
        return EndOutcome.Saved(result)
    }

    /** Walking to the stop/station after the ride. Lost if the app is killed during the wait. */
    private fun scheduleStepsAfter(tripId: Long, endedAtMs: Long) {
        scope.launch {
            delay(STEP_WINDOW_MS)
            val steps = stepLog.stepsBetween(endedAtMs, endedAtMs + STEP_WINDOW_MS)
            sink.updateStepsAfter(tripId, steps)
            _lastResult.update { current ->
                if (current != null && current.tripId == tripId) {
                    current.copy(trip = current.trip.copy(stepsAfter = steps))
                } else current
            }
        }
    }

    companion object {
        const val MIN_TRIP_MS = 15_000L
        /** Minutes of walking counted before Start and after End. */
        const val STEP_WINDOW_MS = 10 * 60 * 1000L
    }
}
