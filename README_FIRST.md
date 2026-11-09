# Biyahe Companion: complete project (Sections 1-8 merged)

Paste the CONTENTS of this folder into your Android Studio project root (the folder that holds `app` and `gradle`),
replacing files when asked. Delete the generated MainActivity.kt and ui/theme folder BEFORE pasting.

Already done for you in this zip:
- All 8 sections merged in the right order (newest version of every file wins)
- AppContainer.kt: RoomTripSink + cloudSync wired in
- AndroidManifest.xml: TripService foreground service added (Start Trip crashes without it)
- app/build.gradle.kts: CameraX + exifinterface added
- gradle-wrapper.properties: Gradle 8.9 (matches the Android Gradle Plugin 8.5.2 in this project)
- gradle.properties: more build memory (4 GB)
- RoomTripSink.kt.template removed

Still yours to do later (Firebase): add google-services.json to app/ and uncomment the google-services plugin lines
in build.gradle.kts and app/build.gradle.kts. See notes/SECTION8_NOTES.md. The app runs fine without it.

Cloud = Firestore only (free Spark plan). Photos are stored inside each hazard document; Firebase Storage is not used.
