# Section 6 notes

## Files that REPLACE Section 5 versions
AppContainer.kt, AppViewModelProvider.kt, BiyaheNavHost.kt, tracking/TripRecorder.kt

## New files
scoring/ComfortScoreCalculator.kt, scoring/WeatherWatcher.kt,
trip/CompletedTrip.kt, trip/TripSink.kt, trip/TripController.kt,
trip/RoomTripSink.kt.template (NOT compiled until you rename it, see below),
ui/trip/TripViewModel.kt, ui/trip/TripScreen.kt

## Room hookup (one-time, 3 steps)
1. Open trip/RoomTripSink.kt.template, rename to RoomTripSink.kt
2. Fix the Trip(...) arguments and repository calls to match your Section 2 Trip entity / TripRepository
3. In AppContainer.kt: InMemoryTripSink()  ->  RoomTripSink(tripRepository)
Until then trips live only in memory (gone when the app closes), everything else works.

## Score cheat-sheet (all numbers editable at the top of ComfortScoreCalculator)
Smooth 80-100, Fair 60-79, Rough 0-59.
