# Life

Native Android app, plus a sibling native iPhone app in `ios/`.
A timeline of past events: Habits, Notes, Finances.
Local-first (JSON on device). No accounts, cloud, ads, or Steady import.

This is not a Steady fork. Do not add planner, due lists, skip, streaks as
scores, groups, widgets, wearables, Deep Work, Path, or LAN web.

## Product rules

- Habits are log-when-you-do-it. No schedule, skip, or due list.
- Long-press a habit (picker or timeline row), including premade catalog
  rows, to edit name / icon / gray subtitle.
  - **Save changes** updates or *claims* that slot. Catalog items keep their
    catalog id so the default row does not come back next to the edit
    (`HabitDomain.edit`). Logs keep the same `habitId`. Renaming onto an
    existing habit merges the logs.
  - **Save as new** inserts a separate custom habit. Existing logs stay. If
    the long-press was a timeline row, only that one log is reassigned
    (`HabitDomain.saveAsNew`).
  - After edit, Log a habit still shows the gray `why` line under the name
    (same treatment as “Dental foundation.”). `HabitSuggestion.canEdit` is
    the long-press gate — premade rows included.
  - There is no Evening hygiene catalog item; Brush + floss covers that.
  - Do not regress these. `HabitCatalogRegressionTest` fails if a premade
    row is not editable, a claimed catalog slot resurfaces the default, the
    gray subtitle vanishes, or Evening hygiene comes back.
- Notes: one body field + tags. **Want + a date chip** makes a commitment
  (date alone is just a tag). Upcoming sits above the timeline.
- Complete a Want with one tap. Cancel/miss requires a written reason:
  10 words, +5 per skip streak, cap 40. OPEN wants stay out of the past
  timeline.
- Reminders: day before 20:00 and morning-of 08:00 (AlarmManager).
- Finance: + income / − expense, learned payees/categories/amounts, live
  balance. Amounts in minor units.

## Stack

Kotlin 2.0, Jetpack Compose Material3, minSdk 26, compileSdk 35,
`applicationId com.life.app`.

iPhone: SwiftUI, iOS 17, bundle id `com.life.app`, sources in `ios/`.
Same product rules. JSON backups are interchangeable. Build only on a Mac
with Xcode 15+ (`./ios/build.sh`). Install steps: `ios/INSTALL.md`.

Repo: https://github.com/markmarvik/life (`main`).

## Commands

```bash
./build.sh              # clean assembleDebug
./build.sh test
./build.sh assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

### Send APK over KDE Connect

Do **not** `--refresh` then immediately `-a` (races, 0 devices). List, then
share by id. Pixel 8 Pro is `3c9d0eab879e4a6cbfc9db0b70a071f6`.

```bash
cp -f app/build/outputs/apk/debug/app-debug.apk /tmp/Life.apk
kdeconnect-cli -l
kdeconnect-cli -d 3c9d0eab879e4a6cbfc9db0b70a071f6 --share /tmp/Life.apk
```

If that id is missing, wait a couple of seconds and use
`kdeconnect-cli -a --id-only` (reachable devices only).

### iPhone (Mac only)

```bash
./ios/build.sh            # compile
./ios/build.sh test
./ios/build.sh install    # plugged-in iPhone; needs signing team in Xcode first
./ios/build.sh open
```

Do not try to share an IPA over KDE Connect from Linux. After Xcode Run:
Settings → General → VPN & Device Management → trust the signing Apple ID.
