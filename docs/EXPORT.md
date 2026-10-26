# Plain export

Share → **Share export** writes two files in the app external files directory
(`context.getExternalFilesDir(null)`), then opens the Android share sheet
with the JSON. The full settings backup (**Export JSON** / **Import JSON**)
is a different file: the whole on-device `AppData` document. This export is
a stable, readable snapshot. It is not imported back.

| File | Role |
| --- | --- |
| `life-export.json` | Canonical snapshot. This is the file the share sheet sends. |
| `life-export.md` | Same snapshot as a Markdown table. |

Both files are rewritten together each time you share. Dates use the device
zone at export time. Instants are UTC.

## JSON

`schemaVersion` is **1**. Unknown fields should be ignored by readers.
Field order is the order below. Defaults are written, including empty
strings and nulls.

```json
{
  "schemaVersion": 1,
  "exportedAt": "2026-10-26T15:00:00Z",
  "zone": "Europe/Belgrade",
  "habits": [
    {
      "id": "h_abc",
      "name": "Walk",
      "icon": "🚶",
      "why": "Out",
      "archived": false,
      "currentStreak": 2,
      "bestStreak": 5,
      "loggedToday": true
    }
  ],
  "entries": [
    {
      "id": "e1",
      "kind": "HABIT",
      "at": "2026-10-25T07:00:00Z",
      "date": "2026-10-25",
      "habitId": "h_abc",
      "body": "",
      "tags": [],
      "amountMinor": null,
      "direction": null,
      "payee": "",
      "category": "",
      "dueDate": null,
      "isWant": false,
      "wantStatus": "NONE",
      "reflection": "",
      "resolvedAt": null
    }
  ]
}
```

### Top level

| Field | Meaning |
| --- | --- |
| `schemaVersion` | `1` for this document. |
| `exportedAt` | ISO-8601 instant (UTC) when the file was built. |
| `zone` | `ZoneId` used for `date`, streak days, and the Markdown times. |
| `habits` | Every saved habit, in stored order. Catalog rows that were never logged are omitted. |
| `entries` | Every timeline event (habit, note, finance), oldest `at` first, then `id`. |

### Habits

| Field | Meaning |
| --- | --- |
| `id`, `name`, `icon`, `why`, `archived` | The habit definition. |
| `currentStreak` | Consecutive calendar days with at least one log for this habit. If today has no log yet, the run is counted back from yesterday so the streak stays open until the day ends. |
| `bestStreak` | Longest consecutive run in the habit's history, including the current one. |
| `loggedToday` | True when at least one log falls on today's date in `zone`. |

Several logs on the same local date count as one day. A missing day ends the run. There is no schedule, skip, or score.

### Entries

| Field | Meaning |
| --- | --- |
| `id` | Event id. |
| `kind` | `HABIT`, `NOTE`, or `FINANCE`. |
| `at` | ISO-8601 instant (UTC) of `createdAt`. |
| `date` | Local calendar date of `at` in `zone`, `yyyy-MM-dd`. |
| `habitId` | Habit id when `kind` is `HABIT`, otherwise null. |
| `body` | Note text, or a finance memo. |
| `tags` | Note tags. |
| `amountMinor` | Money in minor units (cents). Null when this is not a finance event. |
| `direction` | `IN` or `OUT` for finance, otherwise null. |
| `payee`, `category` | Finance labels. |
| `dueDate` | `yyyy-MM-dd` when a note is dated or is a Want. |
| `isWant` | True when the note is a commitment. |
| `wantStatus` | `NONE`, `OPEN`, `DONE`, `CANCELED`, or `MISSED`. |
| `reflection` | Written reason when a Want was canceled or missed. |
| `resolvedAt` | ISO-8601 instant when a Want was closed, otherwise null. |

Opening balance and currency stay in the full backup only. Finance amounts here are minor units.

## Markdown

`life-export.md` starts with `# Life export`, lists `schemaVersion`,
`exportedAt`, and `zone`, then a habit table (name, current streak, best
streak, logged today) and an entry table (date, local time `HH:mm`, kind,
detail). Habit detail is the habit name. Note detail is the body.
Finance detail is the signed minor amount, payee, and category, for example
`-450 Lidl (Food)`.
