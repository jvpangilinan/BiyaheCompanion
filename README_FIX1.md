# Fix 1 (from your screenshots)

Paste the CONTENTS of this folder into the project root and replace when asked.

ui/theme/PosterStyle.kt        skewX() was drawing nothing: that is why the BIYAHE logo and every
                               heading (Recent trips, History, Hazards, Trip summary) were missing
ui/summary/SummaryScreen.kt    map no longer spills over the result card and details
ui/trip/TripScreen.kt          same clip for the live map
ui/history/HistoryScreen.kt    shorter "Walking steps" label so both stat cards match in height
