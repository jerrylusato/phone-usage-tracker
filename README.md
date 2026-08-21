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

## What it records

| Source | Data | Why |
| --- | --- | --- |
| `UsageStatsManager.queryEvents` | app opened/paused/stopped, screen on/off, lock/unlock, foreground service start/stop, boots, user interactions | app launch counts, unlocks, screen-on time, sessions |
| `UsageStatsManager.queryAndAggregateUsageStats` | per-day, per-app foreground milliseconds | accurate screen-time totals that survive collection gaps |

It never touches messages, contacts, call logs, location, camera, microphone or clipboard, and
it does not read the content of any app.

Collection runs about every 30 minutes through WorkManager, plus whenever you open the app or a
report fires. It is deliberately **not** a permanent foreground service: Android keeps its own
recent usage history, so a missed run is backfilled by the next one, and Android 15+ shuts down
long-running `dataSync` foreground services anyway.

## Reports

Configured under **Settings → Reports**:

- **Weekly** (on by default) — pick weekday and time. Covers the seven complete days ending
  yesterday and compares them with the seven before.
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

Needs JDK 21 and Android SDK platform 36.

```bash
./gradlew :app:assembleDebug            # debug APK
./gradlew :app:testDebugUnitTest        # aggregation unit tests
./gradlew :app:lintDebug                # Android lint
./gradlew :app:assembleRelease          # signed release APK
```

Release signing reads `keystore.properties` and `release.keystore` from the repository root.
Both are gitignored, and they exist only on the machine that built the APK. **Keep a backup**:
Android will refuse to update an installed APK that is signed with a different key. Without
those files `assembleRelease` still runs, producing an unsigned APK.

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
