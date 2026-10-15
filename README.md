# Automation Hub for Android

A native Android routine app inspired by the supplied dark-screen reference. It uses **Material 3** with an intentionally fixed dark palette; **Material You dynamic color extraction is not enabled**.

## Trigger → task automations

Each routine combines one trigger with an Android system task:

- **Tap** a routine card.
- **Voice phrase**, captured only after the user taps to start Android's built-in `SpeechRecognizer` API. The app does not continuously listen and does not include a cloud speech SDK.
- **Geofence arrival**, using Google Play services' Geofencing API. Enter a center latitude/longitude and radius (50–10,000 m). Background location (“Allow all the time”) and notifications are required for reliable background alerts. Geofences are restored after device boot.
- **Paired Bluetooth device connection**, matching a paired device by name or MAC address. The app listens for classic Bluetooth ACL-connected broadcasts. Nearby devices permission is needed on Android 12+ to inspect/select a paired device.
- **Shake** or **double-tap the header**. These sensors/gestures are active only while Automation Hub is open; they are not system-wide gestures.

Geofence and Bluetooth events create a notification. **Tap the notification to run the task**; Android restricts apps from silently launching system screens from the background. The app uses a fixed set of starter routines (Good Morning, Study Mode, Bedtime, Leave Home) and lets you add, edit, and delete routines. Routines are stored on-device.

## Supported tasks

- Open Wi-Fi, Bluetooth, location, display, or Do Not Disturb settings.
- Open the Android alarm flow with 7:00 AM prefilled.

Android retains control over protected settings. For example, Do Not Disturb access or alarm creation may require the user to confirm or grant access in an Android-owned screen. This app opens those screens rather than bypassing system restrictions.

## Permissions and platform behavior

- Microphone: requested when the user first starts speech recognition.
- Location: required for geofences. Android 11+ requires enabling **Allow all the time** from the app's system permission settings.
- Notifications: required to surface background geofence/Bluetooth matches on Android 13+.
- Nearby devices: requested on Android 12+ when selecting or matching Bluetooth devices.
- Geofence delivery depends on location services, device settings, and Google Play services availability. The app does not continuously poll location.

## Build

Requirements: JDK 17+, Android SDK Platform 35, and Android Build Tools 35.0.0.

```bash
./gradlew assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`. A curated copy is checked in at `artifacts/automation-hub-debug.apk`. It is signed with the standard debug key for testing/sideloading, not Play Store release. Configure a private signing key for a production release.

Install with:

```bash
adb install -r artifacts/automation-hub-debug.apk
```
