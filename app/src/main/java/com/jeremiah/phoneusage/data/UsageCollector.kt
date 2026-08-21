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
        // Always redo today and yesterday: today is still growing, yesterday may have been
        // captured before midnight.
        val from = if (lastRollup == null) {
            today.minusDays(DAILY_BACKFILL_DAYS)
        } else {
            minOf(lastRollup, today.minusDays(1))
        }

        val perDay = LinkedHashMap<LocalDate, Map<String, Long>>()
        var day = from
        while (!day.isAfter(today)) {
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = minOf(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), nowMs)
            if (end > start) {
                runCatching { manager.queryAndAggregateUsageStats(start, end) }
                    .onSuccess { stats ->
                        val totals = HashMap<String, Long>()
                        stats.forEach { (pkg, usage) ->
                            val ms = usage.totalTimeInForeground
                            if (ms > 0L) totals[pkg] = ms
                        }
                        if (totals.isNotEmpty()) perDay[day] = totals
                    }
                    .onFailure { Log.w(TAG, "Daily query failed for $day", it) }
            }
            day = day.plusDays(1)
        }

        store.mergeDailyTotals(perDay)
        prefs.lastDailyRollupDay = today
        return perDay.size
    }

    private companion object {
        const val TAG = "UsageCollector"

        /** On a fresh install, pull in whatever history the platform still holds. */
        const val FIRST_RUN_BACKFILL_MS = 30L * 24 * 60 * 60 * 1000
        const val DAILY_BACKFILL_DAYS = 60L
        const val CURSOR_OVERLAP_MS = 60_000L
    }
}
