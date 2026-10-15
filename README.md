# Automation Hub for Android

A small native Android app for creating routines that launch Android system actions when the user says a phrase or taps a routine. The UI follows **Material 3** styling with a fixed dark palette and custom colors. It does **not** use Material You dynamic color extraction.

## What it does

- Ships with Good Morning, Study Mode, Bedtime, and Leave Home starter routines.
- Lets you add, edit, and delete routines. Each routine has a name, an optional voice phrase, and a task.
- Supports Wi-Fi settings, Do Not Disturb settings, the Android alarm setup screen (pre-filled for 7:00 AM), location settings, Bluetooth settings, and display settings.
- Accepts speech through Android's built-in `SpeechRecognizer` API. Recognition starts only after the user taps **Speak a trigger** or a routine with a voice phrase. No background listening or cloud-specific speech SDK is included.
- Stores routines locally in app preferences.

Android restricts direct changes to several system settings. For these tasks, the app opens the relevant Android settings screen; any required user choice or confirmation remains under Android's control. The alarm routine opens the system alarm flow with a 7:00 AM time prefilled; the clock app may show its own confirmation UI.

## Build

Requirements: JDK 17 or later, Android SDK Platform 35, and Android Build Tools 35.0.0.

```bash
./gradlew assembleDebug
```

The installable debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`. It is signed with the standard debug key and is suitable for testing/sideloading, not Play Store release. For a production release, configure a private signing key and build a release variant.

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On first voice use, grant the microphone permission. Speech recognition availability and supported languages depend on the Android device and its installed speech service.

## Project layout

- `app/src/main/java/com/esj/automationhub/MainActivity.java` — UI, routine editor/persistence, voice matching, and Android intents.
- `app/src/main/res/values/styles.xml` — fixed dark Material 3 theme.
- `app/src/main/AndroidManifest.xml` — launcher activity and runtime speech permission declaration.
