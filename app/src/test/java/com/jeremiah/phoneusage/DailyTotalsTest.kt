package com.jeremiah.phoneusage

import com.jeremiah.phoneusage.data.DailyTotals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyTotalsTest {

    private val dayStart = 1_760_000_000_000L
    private val dayEnd = dayStart + 24 * 60 * 60 * 1000

    @Test
    fun `a bucket covering the requested day is accepted`() {
        assertTrue(DailyTotals.acceptsBucket(dayStart, dayEnd, dayStart, dayEnd))
    }

    @Test
    fun `todays partial bucket is accepted`() {
        val now = dayStart + 6 * 60 * 60 * 1000
        assertTrue(DailyTotals.acceptsBucket(dayStart, now, dayStart, now))
    }

    @Test
    fun `a weekly bucket is rejected`() {
        // This is the bug: Android answers with week-long buckets once daily ones expire, and
        // a week of usage recorded as one day is what produced impossible totals.
        val weekStart = dayStart - 5L * 24 * 60 * 60 * 1000
        assertFalse(DailyTotals.acceptsBucket(weekStart, dayEnd, dayStart, dayEnd))
    }

    @Test
    fun `a bucket belonging to a neighbouring day is rejected`() {
        val nextDayStart = dayEnd + 60_000
        assertFalse(
            DailyTotals.acceptsBucket(nextDayStart, nextDayStart + 3_600_000, dayStart, dayEnd),
        )
    }

    @Test
    fun `no app can exceed the measured window`() {
        val window = 6 * 60 * 60 * 1000L
        assertEquals(window, DailyTotals.cap(10 * 60 * 60 * 1000L, window))
        assertEquals(60_000L, DailyTotals.cap(60_000L, window))
    }

    @Test
    fun `days holding more usage than a day can contain are flagged`() {
        assertTrue(DailyTotals.isImplausible(mapOf("com.a" to 40L * 60 * 60 * 1000)))
        assertTrue(
            DailyTotals.isImplausible(
                mapOf("com.a" to 20L * 60 * 60 * 1000, "com.b" to 10L * 60 * 60 * 1000),
            ),
        )
        assertFalse(
            DailyTotals.isImplausible(
                mapOf("com.a" to 3L * 60 * 60 * 1000, "com.b" to 2L * 60 * 60 * 1000),
            ),
        )
    }
}
