# IronLog

Android app for tracking gym performance: sets, reps, weight, and which workout belongs to which day.
Data is stored only on the phone.

## What it does

- **Cycle**: an ordered list of workouts (A → B → C → A …). The app shows which one is next,
  regardless of weekday. Starting a different workout continues the cycle from that one. Each
  workout in the cycle is summed up by the muscles it trains: the main categories of its exercises
  in green, then sub categories that are no exercise's main one in orange, each most planned sets
  first. The "Next up" card on Home lists the exercises themselves.
- **Workouts**: each has its exercises in order, with a planned number of sets and an optional rep
  target (a fixed number or a range).
- **Exercises**: about 190 common exercises are pre-loaded, each with its categories. You can add,
  rename and delete exercises.
- **Categories**: groups of exercises that train the same thing. The pre-loaded ones are muscle
  groups (Chest, Back, Legs, …); you can add, rename and delete categories. An exercise has one
  main category and any number of sub categories, taken from the same list: bench press has Chest
  as its main category and Triceps and Shoulders as sub categories. Under each exercise its main
  category is shown in green and its sub categories in orange. The list, and the picker for
  adding exercises to a workout, are grouped by main category, each group closed until its heading
  is tapped; a search or a category filter shows all it finds. Filtering by a category also shows
  the exercises that have it as a sub category.
  Long-press exercises in the list to select several and set their main category or their sub
  categories at once.
- **Logging**: weight in kg and reps per set, pre-filled from the last time the exercise was done.
  Tick a set to count it. Exercises can be added, removed or replaced mid-workout; replacing offers
  exercises with the same main category first, with that group already open. Leave the weight
  empty for bodyweight sets.
- **History**: finished workouts, and per exercise its best set and every logged session.
- **Supplements**: name a supplement and give it one or more times of day. A silent notification (no
  sound, no vibration, no pop-up banner) appears at each time, with a "Taken" button. Today's doses
  can also be ticked off in the app, and each supplement keeps a record of the doses taken.

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
  data/                  repositories and the pure rules: cycle and pre-fill (Planning.kt),
                         supplement schedule (SupplementSchedule.kt)
  reminders/             alarms, the notification channel and broadcast receivers for supplements
  ui/home/               next workout, workout in progress
  ui/plan/               the cycle and the workout editor
  ui/exercises/          exercise list, categories, exercise detail, exercise picker
  ui/session/            the workout in progress (logging)
  ui/history/            finished workouts
  ui/supplements/        today's doses, the supplement list and the add/edit screen
  ui/common/             formatting/parsing and shared composables
  ui/theme/              colors, typography, theme
app/src/test/            JVM unit tests for the pure logic
app/schemas/             Room schema JSON, one file per database version; keep under version control
```

Each screen file holds its ViewModel next to its composables. ViewModels are scoped to their
navigation entry and get the repositories through `appViewModel { }`.

## Data model

`category` ← `exercise` ← `workout_exercise` → `workout` describe the plan. `exercise.categoryId`
is the main category; `exercise_sub_category` links an exercise to its sub categories and never
repeats the main one.
`session` → `session_exercise` → `set_entry` hold what was actually done. A session with
`finishedAt = NULL` is the workout in progress; finishing it keeps only the sets marked done.
Deleting an exercise that has logged sets only archives it, so history stays intact.

`supplement` → `supplement_time` is the reminder schedule; `supplement_intake` records each dose
taken, identified by supplement, day and time of day.

The database is at version 3. Version 2 added the supplement tables and version 3 the sub
categories, both as automatic migrations. Any change to the entities needs a version bump and a
migration, otherwise existing data on the phone is lost.

### Pre-loaded exercises

[SeedExercises.kt](app/src/main/kotlin/com/ironlog/app/data/db/SeedExercises.kt) lists the
pre-loaded exercises with their main category, their sub categories and the database version that
first had them (`since`). A new database gets the whole list. An existing one gets it merged in by
the migration, following `planPreload` in
[Preload.kt](app/src/main/kotlin/com/ironlog/app/data/Preload.kt), which never overrides the user:

- Only exercises that the list gained since the stored version are inserted, so a pre-loaded
  exercise that was deleted does not come back. Names are compared ignoring case.
- The upgrade to version 3, the first with categories in the list, also fills them in for the
  exercises that are already there: one without a main category gets the list's, and all get the
  list's sub categories. A main category chosen by the user stays; if it differs from the list's,
  the list's becomes a sub category.

To add exercises later: append them with the new version as `since`, bump the database version and
call `applyPreloadedList` from that version's migration.

## Supplement reminders

- Every row of `supplement_time` has one alarm, set for its next occurrence. When it goes off,
  `ReminderReceiver` shows the notification (unless that dose is already ticked off) and sets the
  alarm for the following day.
- The alarms are inexact (`AlarmManager.setWindow`, 10 minutes), which needs no special permission.
  A reminder can be up to about ten minutes late and waits while the phone is in Doze.
- Alarms do not survive a reboot or an app update, and point at the wrong moment after a clock or
  time zone change; `RescheduleReceiver` sets them again in those cases. Starting the app also
  restores any that are missing.
- Silence comes from the notification channel (`supplement_reminders`: no sound, vibration off).
  The system ignores later changes to an existing channel, so different behaviour needs a new
  channel id.
- Android 13 and later ask for the notification permission; the app requests it once the first
  supplement exists and shows a warning on the Supplements tab while it is missing.

Observed on a Xiaomi 15T Pro (HyperOS, Android 16):

- An alarm survives the app being swiped away, and the system starts the app for it again.
- While the screen is off, HyperOS holds the app's alarms back. An overdue reminder is delivered
  within a couple of seconds of the screen waking.
- Reminders tend to arrive near the end of the 10-minute window rather than at its start.
- Without the HyperOS "Autostart" permission the app was not started for the "package replaced"
  broadcast after an update, so its alarms came back only when the app was next opened. The same
  is to be expected after a reboot.

## Notes

- Buttons built with `ExtendedFloatingActionButton` expose only their icon's `contentDescription`
  to accessibility services, so the icon must carry the label.

- `app/src/main/res/mipmap-anydpi-v26` must keep its `-v26` suffix, see [app/lint.xml](app/lint.xml).
- KSP is pinned to 2.3.12 because 2.3.13 shipped with a known regression.
- The build prints a `Configuration.setVisible` deprecation notice that comes from the KSP plugin, and an
  "unable to strip libraries" notice because no NDK is installed. Both are harmless for debug builds.
