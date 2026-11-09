# Section 2: Fast Run logic (back end only)

Paste the CONTENTS of this folder into the project root and replace when asked.

New:      scoring/FastRun.kt            (speed >= 40 km/h and Bumpiness Low, i.e. under 1.2)
Replaced: trip/CompletedTrip.kt         (adds `trip.isFastRun`, computed, not stored)
Replaced: data/DemoData.kt              (adds one demo Fast Run trip)

No database change, no screen change. Nothing looks different yet: the tag is shown in the next
sections. The demo trip only appears on a fresh install or after Clear data (demo data is only
added when the database is empty), so it is optional.
