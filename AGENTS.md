# AGENTS.md

Project-specific rules for AI agents working in this repository.
Read this before touching any file. It overrides default behaviour and global habits.

## The User's Device Is Not a Test Bench

The phone connected via adb is the user's daily-use device.

- **Never use the device for debugging without explicit permission for that occasion.**
  "Using the device" means anything that changes what the user sees or touches, for example:
    - installing an APK (`adb install`)
    - launching or stopping the app (`am start`, `am force-stop`)
    - taking screenshots (`screencap`)
    - injecting input (`input tap`, `input keyevent`, `input text`)
- Before doing any of that, **stop and ask**, so it does not collide with what the user is doing on the phone
- The user says explicitly when the device may be used freely (e.g. "勝手に使っていいよ"). That permission applies
  to the current task only, not to the rest of the session
- Building (`gradle`) and `adb connect` / `adb devices` are fine without asking

### Device facts

- Galaxy Z Fold8. It has **two displays**, so `screencap` needs `-d <display-id>`, otherwise it may capture the
  inactive inner screen (all black). List ids with `adb shell dumpsys SurfaceFlinger --display-id`; the cover
  (outer) display was the second one listed
- Wireless adb: the `<ip>:<port>` changes between sessions and is not stored in the repository. Ask for it

## Licensing: No Code From Other Projects

- **Never copy code, build scripts, or configuration from other repositories into this one**, in particular from
  GPL-licensed projects such as Fossify Gallery. Other projects may be used as a *reference* for ideas only;
  everything here is written from scratch
- The Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) is generated with `./gradlew wrapper` and is
  Gradle's own Apache-2.0 distribution content, not copied from another project

## Development

- Android Studio is not used. Everything runs from the command line inside WSL
- There is no `java` on `PATH`; gradle needs `JAVA_HOME=~/bin/android-studio/jbr` (JDK 17)
- gradle writes to `~/.gradle`, which the Bash sandbox forbids: run gradle with `dangerouslyDisableSandbox: true`
- Use the `debug-build`, `debug-install`, `release-build`, and `release-install` skills for building and installing
- Product strings (UI labels, messages) are neutral Japanese

## Git

- Commit with the `git-add` / `git-commit` skills. Stage explicit paths only
- Do not push unless asked
