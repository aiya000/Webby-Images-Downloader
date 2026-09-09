---
name: debug-install
description: Install the built debug APK of Webby Images Downloader on the connected device with adb. Use when the user asks to install or deploy the debug build; build it first with the `debug-build` skill if needed.
---

# debug-install

Install the debug APK on the device connected via adb.

## Environment

- The device is usually connected with **wireless adb**. The address (`<ip>:<port>`) changes between sessions, it is
  not stored anywhere in the repository
- Inside the Bash sandbox adb cannot reach the device (it starts its own daemon and sees no devices).
  Run the adb commands with `dangerouslyDisableSandbox: true`

## Behavior

1. Make sure the APK exists and is fresh (see the `debug-build` skill):

    ```
    app/build/outputs/apk/debug/app-debug.apk
    ```

    If it is missing or older than the latest source change, run the `debug-build` skill first

2. Check the device:

    ```bash
    adb devices
    ```

    - If no device is listed, run `adb connect <ip>:<port>` when the address is known from the conversation,
      otherwise ask the user to connect wireless adb and tell you the address
3. Install:

    ```bash
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    ```

4. Report `Success` or the adb error verbatim

## Notes

- The debug build is `io.github.aiya000.webbyimagesdownloader.debug`, a separate app from the release build, with its own settings
- Never install while the user has asked to wait ("インストールは待って") — build only
