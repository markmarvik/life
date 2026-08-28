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

## Capture (Android) — Needle first

The bottom composer is the main input. Typed text and STT both go through
`NeedleEngine.interpret` → `NeedleDispatcher`. Do not reintroduce a parallel
parser as the primary dispatcher.

- Tools (keep 3–5, ~256-token window): `log_habit`, `add_note`,
  `log_finance`, `complete_want`, `query_balance`. `miss_want` is deferred
  (needs the 10-word reflection sheet).
- Confidence ≥ 0.75 and required args present → write immediately.
  Below that → preview chips. Empty Needle `function_calls` → note of the
  raw text. `query_balance` never mutates.
- Native engine: JNI to Needle 2 (`liblife_needle.so` ← `libneedle.a`,
  arm64, `--whole-archive`). Global C API, one mutex. Fetch the `.a` with
  `./scripts/fetch-needle.sh`; do not commit it.
- If the `.so` is missing (unit tests, x86, first build): `NeedleFallback`
  emits the same tool names.
- Voice: hold mic, release to commit. `SpeechRecognizer` with
  `EXTRA_PREFER_OFFLINE` / on-device recognizer when present. STT is a feed
  into Needle, not a cloud LLM. sherpa-onnx Whisper is later.
- Habit / Note / Money chips keep the old sheets. Empty send opens the type
  picker.

## Stack

Kotlin 2.0, Jetpack Compose Material3, minSdk 26, compileSdk 35,
`applicationId com.life.app`. NDK 27 + CMake 3.22 for Needle (optional).

iPhone: SwiftUI, iOS 17, bundle id `com.life.app`, sources in `ios/`.
Same product rules. JSON backups are interchangeable. Build only on a Mac
with Xcode 15+ (`./ios/build.sh`). Install steps: `ios/INSTALL.md`.
Needle is Android-only until we say otherwise.

Repo: https://github.com/markmarvik/life (`main`).

## Commands

```bash
./build.sh              # fetch Needle + clean assembleDebug
./build.sh test
./build.sh assembleDebug
LIFE_SKIP_NEEDLE=1 ./build.sh   # keyword fallback only
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
