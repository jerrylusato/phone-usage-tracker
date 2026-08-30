package com.jeremiah.phoneusage.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class AppUsage(
    val packageName: String,
    /** Time this app was in the foreground *while the screen was on*. */
    val timeMs: Long,
    val launches: Int,
    val lastUsedAt: Long,
)

/** Everything the UI and the reports need about one date range, already aggregated. */
data class PeriodSummary(
    val from: LocalDate,
    val to: LocalDate,
    /** Time the screen was actually on — the headline number. */
    val screenTimeMs: Long = 0L,
    /** Of that screen time, how much was spent inside apps. */
    val appTimeMs: Long = 0L,
    val longestSessionMs: Long = 0L,
    val unlocks: Int = 0,
    val launches: Int = 0,
    val boots: Int = 0,
    val eventCount: Int = 0,
    val daysWithData: Int = 0,
    val apps: List<AppUsage> = emptyList(),
    val dailyTotals: Map<LocalDate, Long> = emptyMap(),
    /**
     * Days with no recorded events, filled in from Android's own daily statistics instead.
     * Those are foreground totals rather than measured screen time, so they are marked as
     * estimates in the UI rather than being passed off as the real thing.
     */
    val estimatedDays: Set<LocalDate> = emptySet(),
) {
    val dayCount: Int get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt().coerceAtLeast(1)

    /** Average per day counting only days that actually hold data. */
    val dailyAverageMs: Long get() = if (daysWithData > 0) screenTimeMs / daysWithData else 0L

    val busiestDay: Map.Entry<LocalDate, Long>? get() = dailyTotals.maxByOrNull { it.value }

    val hasData: Boolean get() = screenTimeMs > 0L || appTimeMs > 0L || eventCount > 0

    val hasEstimates: Boolean get() = estimatedDays.isNotEmpty()
}
