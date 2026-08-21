package com.jeremiah.phoneusage.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

object Format {

    /** "4h 07m", "37m", "48s", "—" for nothing. */
    fun duration(ms: Long): String {
        if (ms <= 0L) return "0m"
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return when {
            hours > 0 -> "${hours}h ${minutes.toString().padStart(2, '0')}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    /** Compact form for chart labels and tiles: "4h", "37m". */
    fun durationShort(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h${minutes}"
            hours > 0 -> "${hours}h"
            else -> "${TimeUnit.MILLISECONDS.toMinutes(ms)}m"
        }
    }

    fun count(value: Int): String = String.format(Locale.getDefault(), "%,d", value)

    fun timeOfDay(minuteOfDay: Int): String =
        String.format(Locale.getDefault(), "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

    fun bytes(value: Long): String = when {
        value >= 1024L * 1024 -> String.format(Locale.getDefault(), "%.1f MB", value / (1024.0 * 1024))
        value >= 1024L -> String.format(Locale.getDefault(), "%.0f KB", value / 1024.0)
        else -> "$value B"
    }

    /** "just now", "12 min ago", "3 h ago", "2 days ago", "never". */
    fun relative(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return "never"
        val elapsed = (now - timestamp).coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
        val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
        val days = TimeUnit.MILLISECONDS.toDays(elapsed)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours h ago"
            days == 1L -> "yesterday"
            else -> "$days days ago"
        }
    }

    private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE")
    private val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    fun dayMonth(date: LocalDate): String = DAY_MONTH.format(date)

    fun weekday(date: LocalDate): String = WEEKDAY.format(date)

    fun monthTitle(date: LocalDate): String = MONTH_YEAR.format(date)

    /** "+12%", "-8%", or null when there is nothing to compare against. */
    fun delta(current: Long, previous: Long): String? {
        if (previous <= 0L) return null
        val percent = ((current - previous) * 100.0 / previous).toInt()
        return if (percent >= 0) "+$percent%" else "$percent%"
    }
}
