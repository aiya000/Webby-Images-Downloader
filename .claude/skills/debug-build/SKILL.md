---
name: debug-build
description: Build the debug APK of Webby Images Downloader with gradle. Use when the user asks for a debug build, or before installing a debug build with the `debug-install` skill.
---

# debug-build

Build the debug APK.

## Environment

- gradle needs a JDK. There is no `java` on `PATH`, use the Android Studio JBR:
    - `JAVA_HOME=~/bin/android-studio/jbr`
- gradle writes to `~/.gradle` (wrapper, caches) and may download dependencies, which the Bash sandbox forbids.
  Run the gradle command with `dangerouslyDisableSandbox: true`
- Outside the sandbox `$TMPDIR` is empty. Always give log files an **absolute** path inside the session scratchpad directory

## Behavior

1. Run the build, logging to the scratchpad (in the background when a cold cache is expected):

    ```bash
    export JAVA_HOME=$HOME/bin/android-studio/jbr
    ./gradlew :app:assembleDebug -q > <scratchpad>/debug-build.log 2>&1; echo "EXIT=$?" >> <scratchpad>/debug-build.log
    ```

    - A build with a cold cache takes a few minutes; an incremental one is much faster
    - Use `:app:compileDebugKotlin` instead when only a compile check is needed

2. When it finishes, check the log for `^e: `, `error:`, `FAILED` and the `EXIT=` line
3. Report the APK path:

    ```
    app/build/outputs/apk/debug/app-debug.apk
    ```

## Notes

- The debug build uses the application id `io.github.aiya000.webbyimagesdownloader.debug`, so it coexists with a release build
- Do not install automatically. Installing is the `debug-install` skill, run it only when the user asks
- If the user's machine was restarted or the session was resumed, a background build may have been killed silently:
  check the log and the APK timestamp before trusting an earlier build
