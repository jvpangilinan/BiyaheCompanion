# Sensor tests, batch 1 (accelerometer, gyroscope, magnetometer, ambient light, proximity)

Paste the CONTENTS of this folder into the project root and replace when asked.
Needs Sections 1 to 5 (and Fix 1) already in place.

New:      ui/sensorcheck/SensorTests.kt            the test screens + shared template
Replaced: ui/sensorcheck/SensorCheckScreen.kt      the list; rows marked TEST open a test
Replaced: ui/sensorcheck/SensorCheckViewModel.kt   raw per-sensor flows; list sensors stop at once on leaving the list

No manifest, Gradle or navigation changes.
