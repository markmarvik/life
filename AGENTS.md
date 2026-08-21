# Life

Native Android app. A timeline of past events: Habits, Notes, Finances.
Local-first (DataStore JSON). No accounts, cloud, ads, or Steady import.

This is not a Steady fork. Do not add planner, due lists, skip, streaks as
scores, groups, widgets, wearables, Deep Work, Path, or LAN web.

## Product rules

- Habits are log-when-you-do-it. No schedule, skip, or due list.
- Long-press a habit (picker or timeline row) to edit name/icon.
  - **Save changes** updates `HabitDef`. Logs keep the same `habitId`, so
    the whole timeline for that habit shows the new name/icon. Renaming onto
    an existing habit merges the logs and drops the old def (`HabitDomain.edit`).
  - **Save as new** inserts a separate habit. Existing logs stay. If the
    long-press was a timeline row, only that one log is reassigned
    (`HabitDomain.saveAsNew`).
  - Catalog suggestions that have never been logged are not editable.
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
`applicationId com.life.app`. Repo: https://github.com/markmarvik/life (`main`).

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
