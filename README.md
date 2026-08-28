# Life

A simple timeline for **habits**, **notes**, and **money**. Native Android
and a sibling native iPhone app in `ios/`.

Local-first: no accounts, no ads, no cloud. Type or hold the mic — Needle
maps the sentence to a habit, note, want, or money event.

This is a fresh app. It is not a fork of [Steady](https://github.com/markmarvik/steady). Steady’s planner, streaks, wearables, and scoring stayed behind on purpose.

## What you do in Life

1. Open the timeline.
2. Type what happened, or **hold the mic** and speak.
3. Needle writes it. Ambiguous rows show as chips to tap.

Habit / Note / Money chips (and an empty send) still open the old pickers.

- **Habit** — “popio kreatin”, “trening”, “logged creatine”. One capture writes it on the timeline *now*. Hold any habit — including the premade list — to edit it. **Save changes** updates that habit (and keeps its gray subtitle in Log a habit); **Save as new** creates a separate one. No due list, no skip, no schedule.
- **Note** — a thought, or a Want with a date (“sutra zubar”). Upcoming sits above the timeline, reminds the evening before and the morning of, and **Didn’t** requires a written reason (10 words, more if you keep skipping). **Done** is one tap (or “uradio zubar”).
- **Finance** — “spent 450 dinara lidl food”, “plata 80000”. Header shows current balance. Suggestions learn from what you actually log.

Filter chips: All · Habit · Note · Finance. Gear: opening balance, currency, theme, JSON backup.

## Needle (Android)

On-device [Needle 2](https://cactuscompute.com/needle) (45M, ~14MB, baked into `libneedle.a`) is the dispatcher: text in → tool call out. Voice is a separate STT layer (`SpeechRecognizer`, offline-first) that feeds that text. No cloud LLM.

`./build.sh` fetches the arm64 library from Hugging Face. Unit tests and builds without NDK use a keyword/fuzzy fallback that emits the same tool names.

sherpa-onnx Whisper is not in this drop.

## Build (CLI)

### Android

```bash
# One-time SDK (if you don't already have ~/android-sdk from Steady)
./scripts/setup-android-sdk.sh

./build.sh              # fetch Needle + clean assembleDebug
./build.sh test
./build.sh installDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

Native Needle needs NDK 27 + CMake 3.22 (installed by the setup script) and
`libneedle.a` (~20MB, gitignored). `LIFE_SKIP_NEEDLE=1 ./build.sh` builds
the keyword fallback only.

### iPhone

Native SwiftUI app in `ios/`. **Must be built on a Mac with Xcode 15+.**
This Linux tree cannot produce an IPA. Needle is Android-only for now.

```bash
./ios/build.sh            # compile (Mac)
./ios/build.sh install    # compile + install on a plugged-in iPhone
./ios/build.sh open       # open ios/Life.xcodeproj
```

After a successful build the script prints install steps. They are also in
[`ios/INSTALL.md`](ios/INSTALL.md): sign with your Apple ID in Xcode, plug
the phone in, press Run, then trust the developer on the iPhone
(Settings → General → VPN & Device Management). JSON backups are the same
schema as Android.

## Layout

```
app/src/main/java/com/life/app/
  data/     models, DataStore repo, fuzzy search, habit/finance rankers, wants
  needle/   Needle 2 JNI + tools + fallback dispatcher
  ui/       timeline + capture composer + sheets + settings
  util/     haptics

app/src/main/cpp/
  needle.h / needle_jni.cpp / CMakeLists.txt
  prebuilt/arm64-v8a/libneedle.a   # fetched, not committed

ios/Life/
  Data/     same domain, JSON on disk
  UI/       SwiftUI timeline + sheets
  Reminders/ Want notifications
```

Agent notes for this repo: `AGENTS.md`.
