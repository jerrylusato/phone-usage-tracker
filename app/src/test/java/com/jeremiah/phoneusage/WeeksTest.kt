package com.jeremiah.phoneusage

import com.jeremiah.phoneusage.data.Weeks
import com.jeremiah.phoneusage.report.ReportBuilder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeksTest {

    // Thursday.
    private val thursday: LocalDate = LocalDate.of(2026, 8, 20)

    @Test
    fun `weeks run monday to sunday`() {
        assertEquals(LocalDate.of(2026, 8, 17), Weeks.startOf(thursday))
        assertEquals(LocalDate.of(2026, 8, 23), Weeks.endOf(thursday))
    }

    @Test
    fun `monday belongs to its own week, sunday to the week that started six days earlier`() {
        assertEquals(LocalDate.of(2026, 8, 17), Weeks.startOf(LocalDate.of(2026, 8, 17)))
        assertEquals(LocalDate.of(2026, 8, 17), Weeks.startOf(LocalDate.of(2026, 8, 23)))
    }

    @Test
    fun `this week covers the full monday to sunday frame even mid-week`() {
        val week = Weeks.thisWeek(thursday)
        assertEquals(LocalDate.of(2026, 8, 17), week.start)
        assertEquals(LocalDate.of(2026, 8, 23), week.endInclusive)
    }

    @Test
    fun `last week and the week before it are complete calendar weeks`() {
        val last = Weeks.lastWeek(thursday)
        assertEquals(LocalDate.of(2026, 8, 10), last.start)
        assertEquals(LocalDate.of(2026, 8, 16), last.endInclusive)

        val before = Weeks.weekBeforeLast(thursday)
        assertEquals(LocalDate.of(2026, 8, 3), before.start)
        assertEquals(LocalDate.of(2026, 8, 9), before.endInclusive)
    }

    @Test
    fun `the mid-week comparison uses the same slice of last week`() {
        val slice = Weeks.sameSliceLastWeek(thursday)
        assertEquals(LocalDate.of(2026, 8, 10), slice.start)
        // Thursday last week, not the whole week.
        assertEquals(LocalDate.of(2026, 8, 13), slice.endInclusive)
    }

    @Test
    fun `the weekly report covers the last complete calendar week`() {
        val monday = LocalDate.of(2026, 8, 24)
        val (from, to) = ReportBuilder.weeklyRange(monday)
        assertEquals(LocalDate.of(2026, 8, 17), from)
        assertEquals(LocalDate.of(2026, 8, 23), to)
    }
}
