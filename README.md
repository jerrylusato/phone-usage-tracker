# Phone Usage

A local-only Android app that records how you use your phone, shows it on a dashboard, and
sends you scheduled weekly (and optional daily) summaries as notifications.

Nothing is uploaded. The app does not declare `android.permission.INTERNET`, so the process
cannot open a network connection at all. Recorded data is never deleted automatically — only
when you ask, one month at a time.

Built for a Samsung Galaxy S22+ (Android 16 / One UI), but runs on anything from Android 8.

## Install

The signed APK is at `app/build/outputs/apk/release/app-release.apk`.

1. Copy the APK to the phone (USB, Quick Share, Drive — anything).
2. Open it with **My Files** and allow "Install unknown apps" for whichever app opened it.
3. Launch **Phone Usage** and complete the three setup prompts:
   - **Usage access** — required. The dashboard shows a red card until it is granted:
     Settings → Special access → Usage access → Phone Usage → allow.
   - **Notifications** — asked on first launch; needed for reports only.
   - **Battery: unrestricted** — recommended on One UI. Without it, Samsung's "sleeping apps"
     handling can delay background collection and report delivery by hours.

Or install over USB with `adb install -r app/build/outputs/apk/release/app-release.apk`.

## What "screen time" means here

Screen time is the time the screen was **actually on**, measured from the system event stream,
and an app only accrues time while it is in the foreground *and* the screen is on. Music playing
with a dark screen counts for nothing.

An "open" is a change of foreground *app*, counted only while the screen is on. Moving between
an app's own screens is not an open — apps pause and resume their activities constantly while
you use them, and counting those inflates busy apps several times over.

This is deliberately not Android's `totalTimeInForeground`, which counts activity-resumed time
and over-reports whenever a pause event is missed — that is why a phone can appear to have been
"used" for nine hours. The platform's daily totals are still read, but only as a fallback for
days that have no events of their own (before the app was installed, or after a long gap). Those
days are labelled as estimates in the UI and drawn as faded bars.

| Source | Data | Used for |
| --- | --- | --- |
| `UsageStatsManager.queryEvents` | app resumed/paused/stopped, screen on/off, lock/unlock, foreground service start/stop, boots, interactions | screen time, per-app time, launches, unlocks, sessions |
| `UsageStatsManager.queryUsageStats(INTERVAL_DAILY)` | per-day, per-app foreground milliseconds | estimated fallback for days with no events |

It never touches messages, contacts, call logs, location, camera, microphone or clipboard, and
it does not read the content of any app.

Collection runs about every 30 minutes through WorkManager, plus whenever you open the app or a
report fires. It is deliberately **not** a permanent foreground service: Android keeps its own
recent usage history, so a missed run is backfilled by the next one, and Android 15+ shuts down
long-running `dataSync` foreground services anyway.

Daily totals are only requested for the last seven days, because that is roughly as far back as
Android keeps day-resolution buckets. Older queries come back as weekly or monthly buckets, and
recording one of those as a single day is what made earlier builds report weeks of 260 hours.
Buckets spanning far more than a day are now rejected, and days already stored with impossible
totals are removed once, on first run of this version.

## Reports

Configured under **Settings → Reports**:

- **Weekly** (on by default) — pick weekday and time. Covers the last complete calendar week,
  Monday to Sunday, and compares it with the week before.
- **Daily** (off by default) — pick a time. Before noon it reports yesterday; from midday on it
  reports today so far.

Both use inexact alarms (`setAndAllowWhileIdle`), so a report can land a few minutes late but
never asks for the exact-alarm permission. Alarms are rebuilt after a reboot or app update.
"Preview weekly/daily" shows exactly what the next notification will say.

## Storage, deletion and export

Files live in the app's private internal storage, `/data/data/com.jeremiah.phoneusage/files`:

```
events/2026-08.jsonl   one JSON event per line
daily/2026-08.json     {"2026-08-21": {"com.package": foregroundMs}}
```

Roughly a few hundred KB per month. Deletion is manual and per month — delete the selected
month, everything before it, or everything, from **Settings → Your data** or the History tab.
Daily totals merge upwards (never downwards), so a day the platform has already forgotten is
not erased from your own history.

**Export** writes a single plain-JSON file through the system file picker, containing the raw
events and daily totals for the selected month or for everything.

Uninstalling the app deletes all of it. Export first if you want to keep it.

## Build

Needs JDK 21 and Android SDK platform 36. GitHub CI provisions the SDK automatically.

```bash
./gradlew :app:assembleDebug            # debug APK
./gradlew :app:testDebugUnitTest        # aggregation unit tests
./gradlew :app:lintDebug                # Android lint
./gradlew :app:assembleRelease          # signed release APK
```

Release signing reads `keystore.properties` and `release.keystore` from the repository root.
Both are gitignored. Local builds use them; automated releases use encrypted GitHub Actions secrets. **Keep a backup**:
Android will refuse to update an installed APK that is signed with a different key. Release builds fail when signing configuration is missing.

## Weeks

Weeks run **Monday to Sunday**. The dashboard chart and the app list both switch between *This
week* and *Last week*, and the app list adds *Today*. While the current week is still running,
"vs last week" compares it against the same slice of last week — Monday through the same
weekday — because measuring a part-finished week against a whole one always invents a drop.

## Layout

```
data/    storage, collection, pure aggregation (unit tested)
report/  report text, notifications, alarm scheduling, boot handling
work/    periodic WorkManager collection
ui/      Compose dashboard, history and settings screens
```

## Known limits

- Android reports app usage, not what happened inside an app.
- Foreground totals come from the platform's own daily buckets; they can differ slightly from
  Samsung's Digital Wellbeing, which uses its own definition of screen time.
- If the app is force-stopped or restricted by battery management, collection stops until you
  next open it. The platform's history means little or nothing is actually lost.
- Names for apps you cannot launch (some system components) fall back to the package name; the
  app asks only for launcher-app visibility instead of `QUERY_ALL_PACKAGES`.

## Public releases

MIT licensed. Get verified release downloads and installation help at
[usage.jerrylusato.com](https://usage.jerrylusato.com). See [RELEASING.md](RELEASING.md)
for tag-based CI releases, signing prerequisites, checksums, and provenance verification.
