package com.jeremiah.phoneusage.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CollectResult(
    val granted: Boolean,
    val newEvents: Int = 0,
    val daysRefreshed: Int = 0,
)

/**
 * Reads what Android is willing to tell a normal app about phone usage and writes it to
 * local storage. Two passes per run:
 *
 *  1. the raw event stream since the last recorded event (app switches, screen on/off,
 *     lock/unlock, foreground services, boots);
 *  2. per-day, per-app foreground totals for recent days, which stay correct even if
 *     collection was asleep for a while.
 *
 * Because the platform retains its own history, an occasional gap between runs costs
 * nothing — the next run backfills it. That is why this app polls periodically instead of
 * pinning a permanent foreground service, which recent Android versions time out anyway.
 */
class UsageCollector(context: Context) {

    private val appContext = context.applicationContext
    private val store = EventStore(appContext.filesDir)
    private val prefs = Prefs(appContext)

    fun hasUsageAccess(): Boolean {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun collect(nowMs: Long = System.currentTimeMillis()): CollectResult {
        if (!hasUsageAccess()) return CollectResult(granted = false)
        val manager = appContext.getSystemService(UsageStatsManager::class.java)
            ?: return CollectResult(granted = false)

        val newEvents = collectEvents(manager, nowMs)
        val daysRefreshed = collectDailyTotals(manager, nowMs)
        prefs.lastCollectedAt = nowMs
        return CollectResult(granted = true, newEvents = newEvents, daysRefreshed = daysRefreshed)
    }

    private fun collectEvents(manager: UsageStatsManager, nowMs: Long): Int {
        val cursor = prefs.eventCursor
        val begin = if (cursor <= 0L) nowMs - FIRST_RUN_BACKFILL_MS else cursor + 1
        if (begin >= nowMs) return 0

        val collected = ArrayList<LoggedEvent>()
        var newestSeen = cursor
        runCatching {
            val stream = manager.queryEvents(begin, nowMs)
            val event = UsageEvents.Event()
            while (stream.hasNextEvent()) {
                stream.getNextEvent(event)
                if (event.timeStamp > newestSeen) newestSeen = event.timeStamp
                if (event.eventType in EventTypes.TRACKED) {
                    collected += LoggedEvent(
                        timestamp = event.timeStamp,
                        packageName = event.packageName.orEmpty(),
                        type = event.eventType,
                        className = event.className,
                    )
                }
            }
        }.onFailure {
            Log.w(TAG, "Event query failed", it)
            return 0
        }

        store.appendEvents(collected)
        // Never move the cursor backwards, and keep it close to now so the next query stays small.
        prefs.eventCursor = maxOf(newestSeen, nowMs - CURSOR_OVERLAP_MS)
        return collected.size
    }

    private fun collectDailyTotals(manager: UsageStatsManager, nowMs: Long): Int {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val lastRollup = prefs.lastDailyRollupDay
        // Only days Android still holds at daily resolution are worth asking about; older days
        // answer with weekly or monthly buckets, which are useless as per-day figures.
        val earliest = today.minusDays(DAILY_BACKFILL_DAYS)
        val from = if (lastRollup == null) {
            earliest
        } else {
            // Always redo today and yesterday: today is still growing, and yesterday may have
            // been captured before midnight.
            maxOf(earliest, minOf(lastRollup, today.minusDays(1)))
        }

        val perDay = LinkedHashMap<LocalDate, Map<String, Long>>()
        var day = from
        while (!day.isAfter(today)) {
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = minOf(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), nowMs)
            if (end > start) {
                val totals = dailyTotalsFor(manager, start, end)
                if (totals.isNotEmpty()) perDay[day] = totals
            }
            day = day.plusDays(1)
        }

        store.replaceDailyTotals(perDay)
        prefs.lastDailyRollupDay = today
        return perDay.size
    }

    /**
     * One day's per-app foreground totals.
     *
     * `queryUsageStats(INTERVAL_DAILY, ...)` is used rather than `queryAndAggregateUsageStats`
     * because the latter silently folds in weekly and monthly buckets once a day ages out of
     * daily retention, reporting a whole week of usage as a single day. Buckets that span far
     * more than a day are rejected for the same reason, and no app can exceed the window.
     */
    private fun dailyTotalsFor(
        manager: UsageStatsManager,
        dayStart: Long,
        dayEnd: Long,
    ): Map<String, Long> {
        val buckets = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd)
        }.onFailure { Log.w(TAG, "Daily query failed for $dayStart", it) }.getOrNull()
            ?: return emptyMap()

        val window = dayEnd - dayStart
        val totals = HashMap<String, Long>()
        buckets.forEach { bucket ->
            if (!DailyTotals.acceptsBucket(
                    bucketStart = bucket.firstTimeStamp,
                    bucketEnd = bucket.lastTimeStamp,
                    dayStart = dayStart,
                    dayEnd = dayEnd,
                )
            ) {
                return@forEach
            }
            val packageName = bucket.packageName ?: return@forEach
            val ms = bucket.totalTimeInForeground
            if (ms <= 0L) return@forEach
            totals[packageName] = DailyTotals.cap((totals[packageName] ?: 0L) + ms, window)
        }
        return totals
    }

    private companion object {
        const val TAG = "UsageCollector"

        /** On a fresh install, pull in whatever history the platform still holds. */
        const val FIRST_RUN_BACKFILL_MS = 30L * 24 * 60 * 60 * 1000
        const val DAILY_BACKFILL_DAYS = 7L
        const val CURSOR_OVERLAP_MS = 60_000L
    }
}
