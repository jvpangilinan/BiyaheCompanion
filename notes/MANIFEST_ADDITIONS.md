# AndroidManifest.xml additions for Section 5

Section 1's manifest already has location, microphone and notification permissions.
Add any of the four lines below that are MISSING (duplicates are harmless but untidy),
then add the `<service>` block inside `<application>`.

## Permissions (next to the other <uses-permission> lines)

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

## Service (inside <application>, next to <activity>)

```xml
<service
    android:name=".service.TripService"
    android:exported="false"
    android:foregroundServiceType="location|microphone" />
```

Without the <service> block the Start button will crash the app; without the
FOREGROUND_SERVICE_* permissions it fails on Android 14+.
