package com.jeremiah.phoneusage.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure aggregation over stored data — no Android types, so it is unit testable.
 *
 * Screen time is measured from the event stream, and an app only accrues time while the screen
 * is on. A media player left running with a dark screen therefore counts for nothing, which is
 * what "how long was I actually looking at my phone" means. Android's own
 * `totalTimeInForeground` is not used for this, because it counts activity-resumed time and
 * over-reports whenever a pause event is missed.
 *
 * Days with no recorded events at all (before this app was installed, or after a long gap) fall
 * back to the platform's daily totals and are reported separately as estimates.
 */
object UsageAggregator {

    @Suppress("LongMethod", "CyclomaticComplexMethod")
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
        val horizon = minOf(rangeEnd, nowMs)

        val inRange = events.asSequence()
            .filter { it.timestamp in rangeStart until horizon }
            .sortedBy { it.timestamp }
            .toList()

        val screenByDay = HashMap<LocalDate, Long>()
        val appTime = HashMap<String, Long>()
        val launchesByApp = HashMap<String, Int>()
        val lastUsed = HashMap<String, Long>()
        val daysWithEvents = HashSet<LocalDate>()

        var longestSession = 0L
        var unlocks = 0
        var boots = 0
        var launches = 0

        // A session already running when the range opened would otherwise be dropped entirely.
        // The state at rangeStart comes from the last screen event before it where one exists;
        // otherwise it is inferred from the first screen event inside the range, and credited
        // only back to the earliest event actually on record — never blindly to midnight.
        val priorScreenEvent = events.lastOrNull {
            it.timestamp < rangeStart && it.type in SCREEN_STATE_EVENTS
        }
        val firstScreenEvent = inRange.firstOrNull { it.type in SCREEN_STATE_EVENTS }
        var screenOn: Boolean
        var screenSince: Long
        if (priorScreenEvent != null) {
            screenOn = priorScreenEvent.type == EventTypes.SCREEN_INTERACTIVE
            screenSince = rangeStart
        } else {
            screenOn = firstScreenEvent != null &&
                firstScreenEvent.type != EventTypes.SCREEN_INTERACTIVE
            screenSince = maxOf(rangeStart, inRange.firstOrNull()?.timestamp ?: rangeStart)
        }
        var currentPackage: String? = null
        var currentSince: Long? = null

        fun creditApp(packageName: String?, start: Long?, end: Long) {
            if (packageName == null || start == null) return
            val duration = end - start
            if (duration > 0L) appTime[packageName] = (appTime[packageName] ?: 0L) + duration
        }

        fun creditScreen(start: Long, end: Long) {
            if (end <= start) return
            longestSession = maxOf(longestSession, end - start)
            splitByDay(start, end, zone) { day, ms ->
                screenByDay[day] = (screenByDay[day] ?: 0L) + ms
            }
        }

        inRange.forEach { event ->
            daysWithEvents += Instant.ofEpochMilli(event.timestamp).atZone(zone).toLocalDate()
            when (event.type) {
                EventTypes.SCREEN_INTERACTIVE -> {
                    if (!screenOn) {
                        screenOn = true
                        screenSince = event.timestamp
                        if (currentPackage != null) currentSince = event.timestamp
                    }
                }

                EventTypes.SCREEN_NON_INTERACTIVE, EventTypes.DEVICE_SHUTDOWN -> {
                    if (screenOn) {
                        creditApp(currentPackage, currentSince, event.timestamp)
                        creditScreen(screenSince, event.timestamp)
                        screenOn = false
                    }
                    currentSince = null
                    if (event.type == EventTypes.DEVICE_SHUTDOWN) currentPackage = null
                }

                EventTypes.ACTIVITY_RESUMED -> {
                    if (event.packageName.isNotEmpty()) {
                        if (screenOn) creditApp(currentPackage, currentSince, event.timestamp)
                        if (currentPackage != event.packageName) {
                            // Opening an app with the screen off is not the user opening it.
                            if (screenOn) {
                                launchesByApp[event.packageName] =
                                    (launchesByApp[event.packageName] ?: 0) + 1
                                launches++
                            }
                            currentPackage = event.packageName
                        }
                        currentSince = if (screenOn) event.timestamp else null
                        lastUsed[event.packageName] =
                            maxOf(lastUsed[event.packageName] ?: 0L, event.timestamp)
                    }
                }

                EventTypes.ACTIVITY_PAUSED, EventTypes.ACTIVITY_STOPPED -> {
                    if (event.packageName == currentPackage) {
                        if (screenOn) creditApp(currentPackage, currentSince, event.timestamp)
                        currentPackage = null
                        currentSince = null
                    }
                }

                EventTypes.KEYGUARD_HIDDEN -> unlocks++
                EventTypes.DEVICE_STARTUP -> boots++
            }
        }

        // The screen may still be on when the range ends: measure up to now, not past it.
        if (screenOn) {
            creditApp(currentPackage, currentSince, horizon)
            creditScreen(screenSince, horizon)
        }

        // Days we never saw an event for fall back to the platform's own daily totals.
        val estimatedDays = LinkedHashSet<LocalDate>()
        var day = from
        while (!day.isAfter(to)) {
            if (day !in daysWithEvents) {
                val totals = daily[day]
                if (totals != null && totals.isNotEmpty()) {
                    estimatedDays += day
                    screenByDay[day] = (screenByDay[day] ?: 0L) + totals.values.sum()
                    totals.forEach { (pkg, ms) ->
                        appTime[pkg] = (appTime[pkg] ?: 0L) + ms
                    }
                }
            }
            day = day.plusDays(1)
        }

        val dailyTotals = LinkedHashMap<LocalDate, Long>()
        day = from
        while (!day.isAfter(to)) {
            dailyTotals[day] = screenByDay[day] ?: 0L
            day = day.plusDays(1)
        }

        val packages = appTime.keys + launchesByApp.keys
        val apps = packages.map { pkg ->
            AppUsage(
                packageName = pkg,
                timeMs = appTime[pkg] ?: 0L,
                launches = launchesByApp[pkg] ?: 0,
                lastUsedAt = lastUsed[pkg] ?: 0L,
            )
        }.sortedWith(compareByDescending<AppUsage> { it.timeMs }.thenByDescending { it.launches })

        return PeriodSummary(
            from = from,
            to = to,
            screenTimeMs = dailyTotals.values.sum(),
            appTimeMs = appTime.values.sum(),
            longestSessionMs = longestSession,
            unlocks = unlocks,
            launches = launches,
            boots = boots,
            eventCount = inRange.size,
            daysWithData = dailyTotals.count { it.value > 0L },
            apps = apps,
            dailyTotals = dailyTotals,
            estimatedDays = estimatedDays,
        )
    }

    private val SCREEN_STATE_EVENTS = setOf(
        EventTypes.SCREEN_INTERACTIVE,
        EventTypes.SCREEN_NON_INTERACTIVE,
        EventTypes.DEVICE_SHUTDOWN,
    )

    /** Splits an interval across local midnights so per-day totals stay honest. */
    private inline fun splitByDay(
        start: Long,
        end: Long,
        zone: ZoneId,
        credit: (LocalDate, Long) -> Unit,
    ) {
        var cursor = start
        while (cursor < end) {
            val day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val sliceEnd = minOf(end, nextMidnight)
            credit(day, sliceEnd - cursor)
            cursor = sliceEnd
        }
    }
}
