# Section 4: the screens

Paste the CONTENTS of this folder into the project root and replace when asked.
Needs Sections 1 to 3 already in place (it uses their theme, PosterButton etc.).

New:      ui/components/PosterUi.kt   PosterButton, PosterHeading, PosterLogo, PosterTagline, PosterHero
Replaced: ui/lock/LockScreen.kt       (fingerprint logic unchanged, new layout)
          ui/home/HomeScreen.kt
          ui/summary/SummaryScreen.kt (light card + red shadow, result card, Fast Run tag)
          ui/history/HistoryScreen.kt
          ui/hazards/HazardsScreen.kt
          ui/trip/TripScreen.kt
          navigation/BiyaheNavHost.kt (bottom bar only)
