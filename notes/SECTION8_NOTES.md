# Section 8 (last): hazard camera, Hazards tab, Firebase sync

## Files
New:      sync/CloudSync.kt
          ui/hazards/HazardsScreen.kt, ReportHazardScreen.kt, HazardViewModel.kt, HazardPhoto.kt, HazardLocation.kt
Replaces: navigation/BiyaheNavHost.kt, ui/AppViewModelProvider.kt,
          trip/RoomTripSink.kt (only change: a steps-after update marks the trip "not uploaded" so it syncs again)

## 1. AppContainer.kt (I can't see your current copy, so add this by hand)
    import com.biyahe.companion.sync.CloudSync
    ...
    val cloudSync: CloudSync by lazy {
        CloudSync(appContext, settingsRepository, tripRepository, hazardRepository, appScope)
    }
Also make sure tripSink is  RoomTripSink(tripRepository)  (from the 7b zip). Cloud sync reads trips from Room;
with InMemoryTripSink there is nothing to upload.

## 2. AndroidManifest.xml (check these exist)
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />

## 3. app/build.gradle.kts dependencies (add what is missing; keep your existing Firebase BoM version)
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation(platform("com.google.firebase:firebase-bom:<your version>"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
Then uncomment the google-services plugin lines from Section 1 (root and app build files) and put
google-services.json in the app/ folder.

## 4. Firebase console (once)
- Authentication -> Sign-in method -> enable "Anonymous".
- Firestore Database -> Create database (production mode). Storage is NOT used.
  Photos are saved inside the Firestore hazard document (shrunk to a small JPEG), so the free Spark plan is enough.
- Rules (so each phone can only touch its own data):
    Firestore:  match /users/{uid}/{document=**} { allow read, write: if request.auth != null && request.auth.uid == uid; }

## How it behaves
- Report flow: Hazards tab -> Report a hazard -> camera -> photo -> pick type, note -> GPS tag + date/time -> Save.
  It is written to SQLite first, so a report is never lost if the upload fails.
- Sync runs after you save a report, after a trip is saved, each time the app is unlocked, and on "Sync now".
  Only rows with cloudSynced = false are uploaded, and they are marked synced only after Firebase accepts them.
- SQLite keeps the newest 50 synced trips (CloudSync.KEEP_RECENT_TRIPS); Firebase keeps the full history.
- The camera runs inside the app, so the app never goes to the background and the fingerprint is not asked again.
  Because of that I did not call suppressNextRelock().
- Offline: messages say so, nothing is lost, it uploads later.

## Test checklist
1. Hazards tab shows the demo pothole ("No photo" box, since the demo has no image file).
2. Report a hazard -> allow camera -> take a photo -> the form shows the GPS tag (set a location on the emulator:
   Extended controls -> Location).
3. Save -> card appears with "Waiting to upload", then "Uploaded" after sync (check Firestore users/<id>/hazards and Storage).
4. Record or open a trip -> Firestore users/<id>/trips gets a document.

## Known limits
- Deleting a hazard removes it from the phone only; the cloud copy stays.
- Hazards are not linked to a specific trip (tripId stays empty).
- Sync is upload-only: a new phone or reinstall does not download the old cloud history yet.
- Not compiled or run on a device.
