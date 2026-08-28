# Install Life on an iPhone

Life for iPhone is a native SwiftUI app (`ios/`). It is not the Android APK.
You need a **Mac with Xcode 15+** and the iPhone on the same desk. A free
Apple ID is enough; you do not need a paid Developer Program membership to
run it on your own phone.

There is no App Store build and no sideload from Linux. The Android
`kdeconnect-cli --share` path does not apply.

## 1. One-time setup

1. Install **Xcode** from the Mac App Store (15 or newer).
2. Open it once, install extra components if it asks, then in Terminal:

   ```bash
   sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
   sudo xcodebuild -license accept
   ```

3. Clone or copy this repo onto the Mac.
4. Plug the iPhone in with a cable. Unlock it. Tap **Trust** if asked.
5. On the iPhone: **Settings → Privacy & Security → Developer Mode** → On
   (iOS 16+). The phone will restart.

## 2. Sign and run (usual path)

```bash
./ios/build.sh open
```

Or open `ios/Life.xcodeproj` in Xcode.

1. In the left sidebar click the blue **Life** project → target **Life** →
   **Signing & Capabilities**.
2. Check **Automatically manage signing**.
3. **Team**: add your Apple ID (Xcode → Settings → Accounts → +) and pick it.
   Bundle id is `com.life.app`. If Xcode says the id is taken, change it to
   something like `com.yourname.life`.
4. At the top, pick your **iPhone** as the run destination (not a Simulator).
5. Press **Run** (▶). Xcode builds, signs, and installs Life.

### First launch

On the iPhone go to **Settings → General → VPN & Device Management**
(or **Device Management**). Trust the Apple ID that signed the app, then
open **Life**.

Free Apple IDs expire after 7 days. Open the project in Xcode and Run
again to renew.

## 3. CLI install (after signing works in Xcode once)

```bash
./ios/build.sh install
```

If it cannot see the phone:

```bash
xcrun xctrace list devices
DEVICE_UDID=<the-udid> ./ios/build.sh install
```

## 4. Simulator only (no phone)

In Xcode pick an iPhone simulator and press Run. Want reminders fire as
simulator notifications. This is not a substitute for a device install.

```bash
./ios/build.sh test    # domain tests on a simulator
```

## Backup

Settings → Export JSON / Import JSON. The file uses the same schema as
the Android app, so a backup from one can be restored on the other.
Each phone still stores its own copy; there is no sync.

## What this app is

Same product as Android: local-first timeline of Habits, Notes, Finances.
Wants (Want chip + a date) sit in Upcoming, with reminders the evening
before at 20:00 and the morning of at 08:00. Long-press a habit — including
premade catalog rows — to edit. No accounts, cloud, ads, due lists, or skip.
