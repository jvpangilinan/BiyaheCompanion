# Sensor tests, batch 2 + stage-friendly Sensor Check

Paste the CONTENTS of this folder into the project root and replace when asked.
Needs batch 1 (Sensor_Tests_Batch1.zip) already installed.

New:      ui/sensorcheck/SensorStory.kt        stage titles ("Sensor for Bumps"), groups, demo order, one-line pitches
New:      ui/sensorcheck/SensorTests2.kt       GPS, barometer (+ rain demo), microphone, step counter, camera, fingerprint
Replaced: ui/sensorcheck/SensorTests.kt        all 11 wired in, stage title header, Next button
Replaced: ui/sensorcheck/SensorCheckScreen.kt  list grouped in demo order, numbered, stage titles
Replaced: ui/sensorcheck/SensorCheckViewModel.kt  GPS / pressure / mic / steps / fingerprint access

To change the wording on stage, edit only SensorStory.kt.
No manifest, Gradle or navigation changes.
