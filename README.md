# Webby-Images-Downloader

An Android app to list the images on a web page, pick the ones you want by tapping, and download them all at once.

## Development

Built without Android Studio, from the command line (WSL works fine).

### Requirements

- JDK 17
- Android SDK with platform 36
    - `ANDROID_HOME` set, or `sdk.dir` in `local.properties`

### Build

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
