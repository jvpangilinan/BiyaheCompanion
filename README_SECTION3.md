# Section 3: shared poster components

Paste the CONTENTS of this folder into the project root and replace when asked.

New:      ui/components/HalftoneImage.kt   grayscale image + paper fade + halftone dots
Replaced: ui/components/TripCards.kt       same function names as before, new poster look, plus:
            FastRunTag, PosterRow, RideKind / rideKindFor / RideKindCard (Smooth, Rough, Fast Run cards)
Replaced: ui/summary/SummaryScreen.kt      one line only: ScoreRing(..., onDark = true), so the score
                                           stays readable on the old dark card until Section 4

Home, History and Summary pick up the new rows, stat cards, badges and ring right away.
