# Life BIP Max watch app

Date: 2026-11-10
Repo: https://github.com/markmarvik/life
Status: design, awaiting review

## Goal

A BIP Max (Zepp OS) watch app that talks to the existing Life Android app through Gadgetbridge watch-app messages. A tap or a short note on the watch writes straight into Life’s local timeline. Morning and evening digests, plus Life’s existing Want reminders, help Mark stay on daily practice without turning Life into a scheduler.

Tone (Miyamoto Musashi): do what is useful, practice today, no guilt scoreboard. This is not Steady’s old Path feature, not a habit due list, and not wearable health sync.

## Decisions already made

- First version: one-tap habits plus a quick note field. Money and voice/file transfer wait.
- The Gadgetbridge listener lives inside Life (`com.life.app`). No extra phone companion.
- A tap always logs a habit *now*. It does not undo today’s log.
- Until [Gadgetbridge PR #7290](https://codeberg.org/Freeyourgadget/Gadgetbridge/pulls/7290) is in Nightly, Life talks to the sidecar package `nodomain.freeyourgadget.gadgetbridge.zepptest` (launcher: Gadgetbridge (Zepp test)). Settings can switch the package later.
- Life’s product rule “do not add wearables” is waived only for this small logging-and-reminder companion.

## Out of scope

- Money / finance logging on the watch
- Voice recordings or other binary transfers (the message body limit is about 384 KB; short clips can come later)
- Habit schedules, skip, due lists, streak scores, or “you skipped” copy
- Editing habits on the watch
- Tags or Want dates when creating a note from the watch
- An iPhone watch app
- Queuing logs on the watch when the phone is unreachable
- Replacing or updating Gadgetbridge Nightly
- Changing the upstream Gadgetbridge PR

## Architecture

```
BIP Max watch app  <-->  Gadgetbridge (Zepp test)  <-->  Life (com.life.app)
     Zepp OS              Intent API + BLE               listener + timeline
```

- Watch app id is the decimal string `3266101` (distinct from the #3266 test app `3266001`).
- Life registers as the companion with `SEND_WATCH_APP_MESSAGE` and a `replyTo` PendingIntent so Gadgetbridge delivers this app id only to Life (`PendingIntent.getCreatorPackage()`).
- Listen-only uses the same command with `replyTo` and no payload.
- Incoming messages arrive as `WATCH_APP_MESSAGE` extras `device`, `appId`, base64 `payload`.
- Life decodes the payload as UTF-8 JSON and writes through the same habit, note, and Want domain code the phone UI already uses.
- A manifest-registered receiver must handle incoming messages even when the timeline screen is not open. Registration is not tied to one Activity being visible.
- If no companion is listening, Gadgetbridge drops the message. The watch does not retry later.

## Watch screens

Home (“today”):

- Habits Life sent, ranked the same way Life already ranks them, with habits not yet logged today listed first.
- Open Wants that are due today.
- Each habit row: name, and a short “Logged” after Life confirms.
- Each Want row: title, and one tap sends `want_done`.

Note:

- A short text field and a send control. This is for a sentence, not a long journal.
- Success writes a normal Life note (body only, no tags, no Want date).

Disconnected:

- If a sync or send times out, show “Open Life on your phone”.
- Do not show “Logged” until Life replies `ok`.

The sideloaded watch package must not use the 48-hour `expiredTime` from the #3266 test app. Use a long expiry so the install does not vanish overnight.

Install the `.zab` with Gadgetbridge’s File Installer, same as the test watch app. Target deviceSource `11206915` (Amazfit Bip Max).

## Phone screens

- Timeline: watch-originated entries look like any other habit log or note. Want completion uses the existing Done path.
- Settings:
  - Enable the watch listener (on by default after install of this feature, still gated by Gadgetbridge’s own “Allow watch app messages” opt-in).
  - Gadgetbridge package field, default `nodomain.freeyourgadget.gadgetbridge.zepptest`.
  - Listener status: registered or not, and which package it is using.
  - Morning digest toggle and time, default 08:00 on.
  - Evening digest toggle and time, default 20:00 on.
  - Notification permission state, explained in plain words if it is off (digests will not fire).
  - Debug log (see Logging).

## Reminders and notifications

Existing Want reminders (evening before at 20:00, morning of at 08:00) stay as they are.

New optional digests:

- Morning, default 08:00: “today’s path” — usual habits not yet logged today, plus open Wants due today.
- Evening, default 20:00: what’s still open from that same set.

These are ordinary Android notifications so Gadgetbridge can already show them on the BIP Max when notification forwarding is enabled. If the watch companion is registered, Life also sends the same digest over the messaging API so opening the watch app lands on that list.

“Usual habits” means habits Life already ranks as ones Mark actually logs, not a new schedule. Missing a day is not called out as a failure.

## Message contract

JSON, UTF-8, schema version `v: 1`. Unknown types and missing required fields are ignored (phone) or shown as a send failure (watch).

Watch to phone:

- `{"v":1,"type":"sync"}`
- `{"v":1,"type":"log_habit","habitId":"<id>"}`
- `{"v":1,"type":"note","text":"<sentence>"}`
- `{"v":1,"type":"want_done","wantId":"<id>"}`

Phone to watch:

- `{"v":1,"type":"habits","items":[{"id":"...","name":"...","loggedToday":false}],"wants":[{"id":"...","title":"...","dueToday":true}]}`
- `{"v":1,"type":"ok","ref":"log_habit","id":"<habitId>"}` (same shape for `note` and `want_done`, `id` omitted for notes)
- `{"v":1,"type":"error","reason":"bad_payload"}` (`reason` is one of `bad_payload`, `unknown_id`)
- `{"v":1,"type":"digest","habits":[{"id":"...","name":"..."}],"wants":[{"id":"...","title":"..."}]}`

`log_habit` of an unknown id, or `want_done` of an unknown or already-done Want, gets `error` / `unknown_id` and writes nothing.

## Error handling

- No “Logged” on the watch until `ok`.
- Timeout or no reply: “Open Life on your phone”. Nothing is queued on the watch.
- A second tap logs a second time, after its own `ok`.
- Bad JSON or unknown type: ignored on the phone; debug-logged; no crash.
- Notification permission denied: digests do not fire; Settings says so.
- Wrong Gadgetbridge package or Gadgetbridge opt-in off: listener status in Settings is not “registered”; watch sync fails the same way as unreachable.

## Logging

Purpose: debug a failed round trip without leaking private content.

Two places, same events:

1. Logcat tag `LifeWatch`.
2. An in-app debug log in Settings: the last 200 events, newest first, with a copy/share action so Mark can send it without adb.

Each event records time, level (`info` / `warn` / `error`), a short event name, and non-private detail. Allowed detail includes message `type`, watch `appId`, payload size in bytes, habit id, Want id, Gadgetbridge package, whether the listener registered, digest habit/Want counts, notification scheduled/fired/permission-denied, and error class or `reason`.

Never log: raw payloads, note text, notification body copy that repeats a note, MAC addresses, tokens, or Gadgetbridge extras dumps.

The watch shows the last error on the disconnected screen. It does not keep a long private log.

## Source layout

- Phone listener, settings, digest scheduling, debug log, and tests live in https://github.com/markmarvik/life
- Watch sources live in that same repo under `watch/` (Zepp OS app, Zeus build, `appId` 3266101, deviceSource 11206915)
- Built `.zab` is an artifact, not required on `main` if CI cannot build Zeus; the sources must be in the repo

## Testing

Unit tests (no device):

- Parse and reject the message shapes above
- `log_habit` / `note` / `want_done` call the same domain writes as the phone UI
- Today’s-path and digest contents: usual habits not logged today, plus open Wants due today
- Debug log redaction: note text and raw payloads never appear

Device proof (BIP Max + Gadgetbridge (Zepp test) + Life):

- Install the watch app with File Installer
- Sync shows habits and today’s Wants
- Tap a habit → timeline has the log and the watch shows “Logged”
- Send a note → timeline has the note
- Mark a Want done → it is Done in Life
- A digest notification can be triggered (or the scheduled time observed) and appears as an Android notification
- Nightly remains installed and is not replaced

This is not done until the device round trip passes. Do not claim it works on every Zepp OS watch.

## Future (not this version)

Voice clips and other files over the same API, using the ~384 KB body limit or split payloads. Money logging. Switching the default Gadgetbridge package to Nightly after #7290 merges.
