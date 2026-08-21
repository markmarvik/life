# Life

A simple Android timeline for **habits**, **notes**, and **money**.

Local-first: no accounts, no ads, no cloud. Log what happened with one `+` button.

This is a fresh app. It is not a fork of [Steady](https://github.com/markmarvik/steady). Steady’s planner, streaks, wearables, and scoring stayed behind on purpose.

## What you do in Life

1. Open the timeline.
2. Tap **+**.
3. Pick **Habit**, **Note**, or **Finance**.

- **Habit** — tap a suggestion, fuzzy-search what you already log, or type a new name. One tap writes it on the timeline *now*. Hold a habit to edit the name/icon: **Save changes** updates every log of that habit; **Save as new** keeps the original and creates a separate one. No due list, no skip, no schedule.
- **Note** — one field for the thought, tags to find it later. Add **Want** plus a date chip (Today / Tomorrow / pick a day) to make it a commitment: it sits in **Upcoming** above the timeline, reminds the evening before and the morning of, and **Didn’t** requires a written reason (10 words, more if you keep skipping). **Done** is one tap.
- **Finance** — **+** income / **−** expense, amount, payee, category. Header shows current balance. Suggestions learn from what you actually log.

Filter chips: All · Habit · Note · Finance. Gear: opening balance, currency, theme, JSON backup.

## Build (CLI)

```bash
# One-time SDK (if you don't already have ~/android-sdk from Steady)
./scripts/setup-android-sdk.sh

./build.sh              # clean assembleDebug
./build.sh test
./build.sh installDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Layout

```
app/src/main/java/com/life/app/
  data/     models, DataStore repo, fuzzy search, habit/finance rankers, wants
  ui/       timeline + capture sheets + settings
  util/     haptics
```

Agent notes for this repo: `AGENTS.md`.
