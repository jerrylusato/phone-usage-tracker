package com.jeremiah.phoneusage.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class AppUsage(
    val packageName: String,
    val foregroundMs: Long,
    val launches: Int,
    val lastUsedAt: Long,
)

/** Everything the UI and the reports need about one date range, already aggregated. */
data class PeriodSummary(
    val from: LocalDate,
    val to: LocalDate,
    val totalForegroundMs: Long = 0L,
    val screenOnMs: Long = 0L,
    val longestSessionMs: Long = 0L,
    val unlocks: Int = 0,
    val launches: Int = 0,
    val boots: Int = 0,
    val eventCount: Int = 0,
    val daysWithData: Int = 0,
    val apps: List<AppUsage> = emptyList(),
    val dailyTotals: Map<LocalDate, Long> = emptyMap(),
) {
    val dayCount: Int get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt().coerceAtLeast(1)

    /** Average per day counting only days that actually hold data. */
    val dailyAverageMs: Long get() = if (daysWithData > 0) totalForegroundMs / daysWithData else 0L

    val busiestDay: Map.Entry<LocalDate, Long>? get() = dailyTotals.maxByOrNull { it.value }

    val hasData: Boolean get() = totalForegroundMs > 0L || eventCount > 0
}
