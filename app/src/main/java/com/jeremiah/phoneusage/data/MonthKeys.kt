package com.jeremiah.phoneusage.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Month/day key helpers. Keys are plain ISO strings so files stay readable after export. */
object MonthKeys {
    private val MONTH = DateTimeFormatter.ofPattern("yyyy-MM")

    fun monthOf(timestampMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        MONTH.format(Instant.ofEpochMilli(timestampMs).atZone(zone))

    fun monthOf(date: LocalDate): String = MONTH.format(date)

    fun parseMonth(key: String): YearMonth? = runCatching { YearMonth.parse(key, MONTH) }.getOrNull()

    fun parseDay(key: String): LocalDate? = runCatching { LocalDate.parse(key) }.getOrNull()

    fun currentMonth(zone: ZoneId = ZoneId.systemDefault()): String = MONTH.format(LocalDate.now(zone))
}
