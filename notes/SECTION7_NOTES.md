# Section 7 notes

## Files that REPLACE earlier versions
BiyaheNavHost.kt, AppViewModelProvider.kt,
trip/TripSink.kt (now also reads trips), trip/RoomTripSink.kt.template (updated for reads),
ui/trip/TripViewModel.kt, ui/trip/TripScreen.kt (now has the live map)

## New files
ui/home/*, ui/history/*, ui/summary/*, ui/components/RouteMap.kt, ui/components/TripCards.kt, ui/util/Format.kt

## Needs from earlier sections
- osmdroid dependency (Section 1) and the INTERNET permission in AndroidManifest.xml
  (<uses-permission android:name="android.permission.INTERNET" />). Without it the map stays grey.
- If you already renamed RoomTripSink.kt.template to RoomTripSink.kt in Section 6, re-do the fixes
  in the NEW template (it gained observeTrips / observeTrip) or the build will say the class is abstract.

## Demo data
InMemoryTripSink starts with 2 demo trips (86 Smooth, 58 Rough) so Home/History/Summary have something to show.
Pass InMemoryTripSink(seedDemo = false) in AppContainer to turn them off. Once RoomTripSink is wired, Room's own demo data takes over.
