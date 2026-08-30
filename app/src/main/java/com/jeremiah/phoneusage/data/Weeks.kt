package com.jeremiah.phoneusage.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Calendar weeks running Monday to Sunday. */
object Weeks {

    fun startOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun endOf(date: LocalDate): LocalDate = startOf(date).plusDays(6)

    /** Monday to Sunday of the current week. Days still in the future read as no data. */
    fun thisWeek(today: LocalDate): ClosedRange<LocalDate> = startOf(today)..endOf(today)

    fun lastWeek(today: LocalDate): ClosedRange<LocalDate> {
        val start = startOf(today).minusWeeks(1)
        return start..start.plusDays(6)
    }

    fun weekBeforeLast(today: LocalDate): ClosedRange<LocalDate> {
        val start = startOf(today).minusWeeks(2)
        return start..start.plusDays(6)
    }

    /**
     * The same slice of last week: Monday through the same weekday. Comparing a part-finished
     * week against a whole one would always show a fake drop, so this is what the "vs last
     * week" figure uses while the current week is still running.
     */
    fun sameSliceLastWeek(today: LocalDate): ClosedRange<LocalDate> =
        startOf(today).minusWeeks(1)..today.minusWeeks(1)
}
