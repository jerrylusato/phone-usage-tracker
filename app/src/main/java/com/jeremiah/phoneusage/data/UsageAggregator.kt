package com.jeremiah.phoneusage.data

import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure aggregation over stored data — no Android types, so it is unit testable.
 *
 * Per-app and per-day totals come from the platform's daily statistics, which are the most
 * accurate numbers available to a normal app. The raw event stream supplies the things daily
 * statistics do not carry: app launch counts, unlocks, screen-on time, sessions and boots.
 */
object UsageAggregator {

    fun summarize(
        from: LocalDate,
        to: LocalDate,
        events: List<LoggedEvent>,
        daily: Map<LocalDate, Map<String, Long>>,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMs: Long = System.currentTimeMillis(),
    ): PeriodSummary {
        val rangeStart = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEnd = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val openInterval = minOf(rangeEnd, nowMs)

        val dailyInRange = daily.filterKeys { !it.isBefore(from) && !it.isAfter(to) }
        val foregroundByApp = HashMap<String, Long>()
        dailyInRange.values.forEach { totals ->
            totals.forEach { (pkg, ms) -> foregroundByApp[pkg] = (foregroundByApp[pkg] ?: 0L) + ms }
        }

        val dailyTotals = LinkedHashMap<LocalDate, Long>()
        var day = from
        while (!day.isAfter(to)) {
            dailyTotals[day] = dailyInRange[day]?.values?.sum() ?: 0L
            day = day.plusDays(1)
        }

        val launchesByApp = HashMap<String, Int>()
        val lastUsedByApp = HashMap<String, Long>()
        var launches = 0
        var unlocks = 0
        var boots = 0
        var screenOnMs = 0L
        var longestSessionMs = 0L
        var screenOnSince: Long? = null
        var lastResumedPackage: String? = null
        var eventCount = 0

        events.asSequence()
            .filter { it.timestamp in rangeStart until rangeEnd }
            .forEach { event ->
                eventCount++
                when (event.type) {
                    EventTypes.ACTIVITY_RESUMED -> {
                        if (event.packageName.isNotEmpty()) {
                            if (event.packageName != lastResumedPackage) {
                                launchesByApp[event.packageName] =
                                    (launchesByApp[event.packageName] ?: 0) + 1
                                launches++
                                lastResumedPackage = event.packageName
                            }
                            lastUsedByApp[event.packageName] =
                                maxOf(lastUsedByApp[event.packageName] ?: 0L, event.timestamp)
                        }
                    }

                    EventTypes.SCREEN_INTERACTIVE -> {
                        if (screenOnSince == null) screenOnSince = event.timestamp
                    }

                    EventTypes.SCREEN_NON_INTERACTIVE, EventTypes.DEVICE_SHUTDOWN -> {
                        screenOnSince?.let { start ->
                            val session = (event.timestamp - start).coerceAtLeast(0L)
                            screenOnMs += session
                            longestSessionMs = maxOf(longestSessionMs, session)
                        }
                        screenOnSince = null
                        // A dark screen also ends whatever was in the foreground.
                        lastResumedPackage = null
                    }

                    EventTypes.KEYGUARD_HIDDEN -> unlocks++
                    EventTypes.DEVICE_STARTUP -> boots++
                }
            }

        // The screen may still be on when the range ends: count it up to now.
        screenOnSince?.let { start ->
            val session = (openInterval - start).coerceAtLeast(0L)
            screenOnMs += session
            longestSessionMs = maxOf(longestSessionMs, session)
        }

        val packages = foregroundByApp.keys + launchesByApp.keys
        val apps = packages.map { pkg ->
            AppUsage(
                packageName = pkg,
                foregroundMs = foregroundByApp[pkg] ?: 0L,
                launches = launchesByApp[pkg] ?: 0,
                lastUsedAt = lastUsedByApp[pkg] ?: 0L,
            )
        }.sortedWith(compareByDescending<AppUsage> { it.foregroundMs }.thenByDescending { it.launches })

        return PeriodSummary(
            from = from,
            to = to,
            totalForegroundMs = foregroundByApp.values.sum(),
            screenOnMs = screenOnMs,
            longestSessionMs = longestSessionMs,
            unlocks = unlocks,
            launches = launches,
            boots = boots,
            eventCount = eventCount,
            daysWithData = dailyInRange.count { it.value.values.sum() > 0L },
            apps = apps,
            dailyTotals = dailyTotals,
        )
    }
}
