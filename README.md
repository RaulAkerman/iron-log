# IronLog

Android app for tracking gym performance: sets, reps, weight, and which workout belongs to which day.
Data is stored only on the phone.

## What it does

- **Cycle**: an ordered list of workouts (A → B → C → A …). The app shows which one is next,
  regardless of weekday. Starting a different workout continues the cycle from that one.
- **Workouts**: each has its exercises in order, with a planned number of sets and an optional rep
  target (a fixed number or a range).
- **Exercises**: about 110 common exercises are pre-loaded, all uncategorised. You can add, rename
  and delete exercises.
- **Categories**: your own groups of interchangeable exercises (for example machine shoulder press
  and dumbbell overhead press). Long-press exercises in the list to select several and assign a
  category at once. An exercise has at most one category.
- **Logging**: weight in kg and reps per set, pre-filled from the last time the exercise was done.
  Tick a set to count it. Exercises can be added, removed or replaced mid-workout; replacing offers
  exercises from the same category first. Leave the weight empty for bodyweight sets.
- **History**: finished workouts, and per exercise its best set and every logged session.

Every edit during a workout is written to the database immediately, so a workout in progress
survives the app being closed or killed.

Not there yet: rest timer, progress charts, export/import of the data.

## Stack

- Kotlin + Jetpack Compose (Material 3), Navigation 3
- Room 3 (`androidx.room3`) with the bundled SQLite driver, code generation through KSP
- Android Gradle Plugin 9.5 (Kotlin support is built in, there is no `kotlin-android` plugin), Gradle 9.8
- `minSdk` 26, `compileSdk` / `targetSdk` 37
- All versions live in [gradle/libs.versions.toml](gradle/libs.versions.toml)

## Toolchain on this machine (WSL)

| What | Where |
| --- | --- |
| JDK 21 (Temurin) | `~/.jdks/temurin-21.0.13` |
| Android SDK | `~/Android/Sdk` (Gradle finds it through the untracked `local.properties`) |
| `adb` | `~/Android/Sdk/platform-tools/adb` |
| `android` CLI | `~/Android/Sdk/cmdline-tools/latest/bin/android` |

The system default Java stays untouched. `./gradlew` finds JDK 21 by itself through
[gradle/gradle-daemon-jvm.properties](gradle/gradle-daemon-jvm.properties), and downloads one if none is installed.

`./gradlew` needs no shell setup. To call `adb` and `android` by name, as the commands below do, add this to `~/.zshrc`:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

Extra SDK packages: `android sdk list --all '<pattern>'` and `android sdk install <package>`.

## Common commands

```bash
./gradlew assembleDebug        # build app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # build and install on the connected phone
./gradlew lintDebug            # static checks, report in app/build/reports/
./gradlew testDebugUnitTest    # unit tests (JVM)
android run                    # build, install and launch on the connected phone
android screen capture         # screenshot of the connected phone
```

Render a Compose `@Preview` to a PNG without any device:

```bash
android preview list app
android preview render app --fqn com.ironlog.app.ui.home.HomeScreenKt.HomeScreenPreview -o /tmp/preview
```

## Running on the phone (Xiaomi / HyperOS)

One-time phone setup:

1. Settings → About phone → tap the OS version 7 times to unlock Developer options.
2. Settings → Additional settings → Developer options: enable **USB debugging**, **Install via USB**
   (HyperOS asks for a signed-in Xiaomi account) and **Wireless debugging**.

Over Wi-Fi with the Windows `adb.exe` (phone and PC on the same network). This is the route that
works from WSL: Windows can discover the phone, and a PC that was already allowed for USB debugging
needs no pairing.

```bash
ADB="/mnt/c/path/to/platform-tools/adb.exe"   # a current Windows build of adb
"$ADB" mdns services                          # lists the phone's <ip>:<port> while Wireless debugging is on
"$ADB" connect <ip>:<port>
"$ADB" -s <ip>:<port> install -r "$(wslpath -w app/build/outputs/apk/debug/app-debug.apk)"
```

HyperOS asks on the phone to confirm every install and cancels after about ten seconds; an
unanswered prompt shows up as `INSTALL_FAILED_USER_RESTRICTED`. Run the install again and tap Install.

`./gradlew installDebug` and the `android` CLI use the Linux `adb` inside WSL. It cannot discover the
phone and has its own key, so it must be paired once (`adb pair <ip>:<pairing-port>` with the code
from "Pair device with pairing code") and then connected with `adb connect <ip>:<port>`.

Over USB, WSL cannot see the phone directly: use the Windows `adb.exe` as above without `connect`, or
forward the device into WSL with [usbipd-win](https://github.com/dorssel/usbipd-win).

## Layout

```
app/src/main/kotlin/com/ironlog/app/
  IronLogApp.kt          Application; creates the database and repositories (AppContainer)
  MainActivity.kt        single activity, hosts Compose
  Navigation.kt          bottom bar and navigation graph (Navigation 3)
  NavigationKeys.kt      one serializable key per screen
  data/db/               Room entities, DAOs, database, the pre-loaded exercise list
  data/                  repositories and the pure cycle / pre-fill rules (Planning.kt)
  ui/home/               next workout, workout in progress
  ui/plan/               the cycle and the workout editor
  ui/exercises/          exercise list, categories, exercise detail, exercise picker
  ui/session/            the workout in progress (logging)
  ui/history/            finished workouts
  ui/common/             formatting/parsing and shared composables
  ui/theme/              colors, typography, theme
app/src/test/            JVM unit tests for the pure logic
app/schemas/             Room schema JSON, one file per database version; keep under version control
```

Each screen file holds its ViewModel next to its composables. ViewModels are scoped to their
navigation entry and get the repositories through `appViewModel { }`.

## Data model

`category` ← `exercise` ← `workout_exercise` → `workout` describe the plan.
`session` → `session_exercise` → `set_entry` hold what was actually done. A session with
`finishedAt = NULL` is the workout in progress; finishing it keeps only the sets marked done.
Deleting an exercise that has logged sets only archives it, so history stays intact.

The database is at version 1. Any change to the entities from here on needs a version bump and a
migration, otherwise existing data on the phone is lost.

## Notes

- Buttons built with `ExtendedFloatingActionButton` expose only their icon's `contentDescription`
  to accessibility services, so the icon must carry the label.

- `app/src/main/res/mipmap-anydpi-v26` must keep its `-v26` suffix, see [app/lint.xml](app/lint.xml).
- KSP is pinned to 2.3.12 because 2.3.13 shipped with a known regression.
- The build prints a `Configuration.setVisible` deprecation notice that comes from the KSP plugin, and an
  "unable to strip libraries" notice because no NDK is installed. Both are harmless for debug builds.
