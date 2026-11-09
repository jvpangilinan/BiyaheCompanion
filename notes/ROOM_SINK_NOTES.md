# Room wiring (Section 7 fix)

## Files in this zip
New:      trip/RoomTripSink.kt
Changed:  data/local/entity/Entities.kt  (Trip gained bumpCount, sharpTurnCount)
          data/local/BiyaheDatabase.kt   (version 1 -> 2)
          data/DemoData.kt               (demo trips now have bump/turn counts)
All other Section 2 files are included unchanged, EXCEPT AppContainer.kt and BiyaheApp.kt, which are left out on purpose: your later sections changed them and the Section 2 copies would overwrite that work.

## Do these steps
1. Unzip over your project (keep folders).
2. DELETE trip/RoomTripSink.kt.template (the new RoomTripSink.kt replaces it).
3. In AppContainer.kt, change  InMemoryTripSink(...)  to  RoomTripSink(tripRepository)
   (the TripSink property keeps its name; only the constructor call changes).
4. Uninstall the app from the phone/emulator once, or just run: the DB version bump wipes
   old data (fallbackToDestructiveMigration) and DemoData re-seeds the 2 sample trips.

## Known limits
- Peak noise is stored as the average (CompletedTrip has no max-noise field).
- Rain is stored as yes/no, so it shows "Possible" or "None".
