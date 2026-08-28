# Life for iPhone

Native SwiftUI port of Life. Same product rules as the Android app:
a local-first timeline of habits, notes, and money. JSON on device.
No accounts, cloud, or ads.

## Build

Must run on a Mac with Xcode 15+:

```bash
./ios/build.sh            # compile
./ios/build.sh test       # domain tests (simulator)
./ios/build.sh install    # compile + install on a plugged-in iPhone
./ios/build.sh open       # open in Xcode
```

After a successful build the script prints install steps. They also live
in [INSTALL.md](INSTALL.md).

This Linux workspace cannot produce an IPA. Copy the repo to a Mac.

## Layout

```
ios/Life/
  Data/        models, JSON store, fuzzy search, rankers, wants
  UI/          timeline + capture sheets + settings
  Reminders/   Want notifications (20:00 day before, 08:00 morning of)
ios/LifeTests/ same rule tests as Android (catalog edit, wants, money)
```
