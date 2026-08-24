# Life

A simple Android timeline for **habits**, **notes**, and **money**.

Local-first: no accounts, no ads, no cloud. Type what happened in the composer at the bottom — one send can log several events.

This is a fresh app. It is not a fork of [Steady](https://github.com/markmarvik/steady). Steady’s planner, streaks, wearables, and scoring stayed behind on purpose.

## What you do in Life

1. Open the timeline.
2. Type in the composer (chat/write) at the bottom.
3. Send. An on-device parser splits on `;` / newlines and writes 1+ events.

Examples:

- `trening; platio lidl 2300` → habit + expense
- `uradio zube` → habit (or completes an open want)
- `plata 80000` → income
- `rata 12k do 2027` → monthly recurring
- `hoću laptop 80k` → planned purchase
- `sutra zubar` → want for tomorrow

High-confidence matches log immediately. Mid-confidence ones show preview chips (tap to confirm, long-press to drop). Anything else becomes a note.

The Habit / Note / Money chips (and a long-press on send) still open the old pickers. Sheets are for editing.

- **Habit** — log when you do it. Hold a habit — including the premade list — to edit it. **Save changes** updates that habit; **Save as new** creates a separate one. No due list, no skip, no schedule.
- **Note** — one field for the thought, tags to find it later. Add **Want** plus a date chip (or type `sutra` / `want`) to make it a commitment: it sits in **Upcoming** above the timeline, reminds the evening before and the morning of, and **Didn’t** requires a written reason (10 words, more if you keep skipping). **Done** is one tap — or type `uradio X`.
- **Finance** — income / expense, amount, payee, category. Header is a monthly funnel: **income → savings → recurring → planned → leftover**. Tap it to edit recurring/planned. Suggestions learn from what you actually log.

Filter chips: All · Habit · Note · Finance. Gear: opening balance, monthly savings, currency, theme, JSON backup.

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
  data/     models, DataStore repo, CaptureParser, fuzzy search, habit/finance rankers, wants
  ui/       timeline + composer + edit sheets + settings
  util/     haptics
```

Agent notes for this repo: `AGENTS.md`.
