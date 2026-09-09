---
name: release-build
description: Build a signed release APK of Webby Images Downloader for personal use (gradle release build, zipalign, sign with the Android debug keystore). Use when the user asks for a production or release build, or before the `release-install` skill.
---

# release-build

Build the release APK and sign it so it can be installed on the user's own device.

## Environment

- gradle and `apksigner` both need a JDK. There is no `java` on `PATH`; `JAVA_HOME` alone is not enough for
  `apksigner`, it also needs `java` on `PATH`:

    ```bash
    export JAVA_HOME=$HOME/bin/android-studio/jbr
    export PATH="$JAVA_HOME/bin:$PATH"
    ```

- Build tools live in `~/Android/Sdk/build-tools/<version>/` (`zipalign`, `apksigner`). Use the newest installed version
- gradle writes to `~/.gradle`, which the Bash sandbox forbids. Run with `dangerouslyDisableSandbox: true`
- Outside the sandbox `$TMPDIR` is empty. Give log files an **absolute** path inside the session scratchpad directory

## Signing

- `app/build.gradle.kts` has no release signing config, so gradle produces an **unsigned** APK
  (`app-release-unsigned.apk`). The user chose to sign with the Android debug keystore instead of creating a personal key:
    - keystore: `~/.android/debug.keystore`
    - alias: `androiddebugkey`, store and key password: `android`
- Future updates must be signed with the **same** key, otherwise `adb install -r` fails with
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

## Behavior

Run everything with a scratchpad log (R8 minification makes this slower than a debug build):

```bash
export JAVA_HOME=$HOME/bin/android-studio/jbr
export PATH="$JAVA_HOME/bin:$PATH"
OUT=app/build/outputs/apk/release
BT=$HOME/Android/Sdk/build-tools/36.0.0
./gradlew :app:assembleRelease -q && echo BUILD_OK \
  && "$BT/zipalign" -f -p 4 "$OUT/app-release-unsigned.apk" "$OUT/app-release-aligned.apk" && echo ALIGN_OK \
  && "$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android \
       --out "$OUT/app-release-signed.apk" "$OUT/app-release-aligned.apk" && echo SIGN_OK
```

- Afterwards verify the signature and report the path:

    ```bash
    "$BT/apksigner" verify --print-certs "$OUT/app-release-signed.apk"
    ```

    ```
    app/build/outputs/apk/release/app-release-signed.apk
    ```

## Notes

- The release build has R8 minification and resource shrinking enabled. If a reflection-based library (JSON, etc.) is
  added later, keep rules go into `app/proguard-rules.pro`
- Do not install automatically; that is the `release-install` skill
