# Life

A simple timeline for **habits**, **notes**, and **money**. Native Android
and a sibling native iPhone app in `ios/`.

Local-first: no accounts, no ads, no cloud. Log what happened with one `+` button.

This is a fresh app. It is not a fork of [Steady](https://github.com/markmarvik/steady). Steady’s planner, streaks, wearables, and scoring stayed behind on purpose.

## What you do in Life

1. Open the timeline.
2. Tap **+**.
3. Type or hold the mic: `popio kreatin`, `spent 450 lidl`, `want call dentist tomorrow`.
4. **Use a form** if you want the old Habit / Note / Finance sheets.

- **Habit** — tap a suggestion, fuzzy-search what you already log, or type a new name. One tap writes it on the timeline *now*. Hold any habit — including the premade list — to edit it. **Save changes** updates that habit (and keeps its gray subtitle in Log a habit); **Save as new** creates a separate one. No due list, no skip, no schedule.
- **Note** — one field for the thought, tags to find it later. Add **Want** plus a date chip (Today / Tomorrow / pick a day) to make it a commitment: it sits in **Upcoming** above the timeline, reminds the evening before and the morning of, and **Didn’t** requires a written reason (10 words, more if you keep skipping). **Done** is one tap.
- **Finance** — **+** income / **−** expense, amount, payee, category. Header shows current balance. Suggestions learn from what you actually log.

Typed capture is fully offline. Voice: Android can install Needle + Whisper once (Settings → On-device AI), then airplane-mode; iPhone uses on-device Speech. Pattern matching still logs if the voice pack is missing.

Filter chips: All · Habit · Note · Finance. Gear: opening balance, currency, theme, JSON backup.

## Build (CLI)

### Android

```bash
# One-time SDK (if you don't already have ~/android-sdk from Steady)
./scripts/setup-android-sdk.sh

./build.sh              # clean assembleDebug
./build.sh test
./build.sh installDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

### iPhone

Native SwiftUI app in `ios/`. **Must be built on a Mac with Xcode 15+.**
This Linux tree cannot produce an IPA.

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
  needle/   PatternNeedle (always) + optional Cactus Needle
  stt/      16 kHz PCM16 mic
  ui/       timeline + composer + capture sheets + settings
  util/     haptics

ios/Life/
  Data/     same domain, JSON on disk
  Needle/   PatternNeedle + Apple on-device speech
  UI/       SwiftUI timeline + composer + sheets
  Reminders/ Want notifications

tools/needle/  shared tool schema + desktop eval (not on phones)
```

Agent notes for this repo: `AGENTS.md`.
