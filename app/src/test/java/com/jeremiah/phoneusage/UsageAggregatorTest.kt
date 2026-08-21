package com.jeremiah.phoneusage

import com.jeremiah.phoneusage.data.EventTypes
import com.jeremiah.phoneusage.data.LoggedEvent
import com.jeremiah.phoneusage.data.UsageAggregator
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageAggregatorTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val day: LocalDate = LocalDate.of(2026, 8, 20)

    private fun at(hour: Int, minute: Int = 0): Long =
        day.atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `sums per-app foreground time from daily totals`() {
        val summary = UsageAggregator.summarize(
            from = day,
            to = day,
            events = emptyList(),
            daily = mapOf(day to mapOf("com.a" to 60_000L, "com.b" to 30_000L)),
            zone = zone,
            nowMs = at(23),
        )

        assertEquals(90_000L, summary.totalForegroundMs)
        assertEquals("com.a", summary.apps.first().packageName)
        assertEquals(1, summary.daysWithData)
        assertEquals(90_000L, summary.dailyTotals[day])
    }

    @Test
    fun `counts a launch only when the foreground app actually changes`() {
        val events = listOf(
            LoggedEvent(at(9, 0), "com.a", EventTypes.ACTIVITY_RESUMED),
            LoggedEvent(at(9, 1), "com.a", EventTypes.ACTIVITY_RESUMED),
            LoggedEvent(at(9, 2), "com.b", EventTypes.ACTIVITY_RESUMED),
            LoggedEvent(at(9, 3), "com.a", EventTypes.ACTIVITY_RESUMED),
        )

        val summary = UsageAggregator.summarize(
            from = day,
            to = day,
            events = events,
            daily = emptyMap(),
            zone = zone,
            nowMs = at(23),
        )

        assertEquals(3, summary.launches)
        assertEquals(2, summary.apps.first { it.packageName == "com.a" }.launches)
    }

    @Test
    fun `measures screen-on time, sessions and unlocks`() {
        val events = listOf(
            LoggedEvent(at(8, 0), "android", EventTypes.SCREEN_INTERACTIVE),
            LoggedEvent(at(8, 1), "android", EventTypes.KEYGUARD_HIDDEN),
            LoggedEvent(at(8, 20), "android", EventTypes.SCREEN_NON_INTERACTIVE),
            LoggedEvent(at(12, 0), "android", EventTypes.SCREEN_INTERACTIVE),
            LoggedEvent(at(12, 5), "android", EventTypes.SCREEN_NON_INTERACTIVE),
        )

        val summary = UsageAggregator.summarize(
            from = day,
            to = day,
            events = events,
            daily = emptyMap(),
            zone = zone,
            nowMs = at(23),
        )

        assertEquals(25 * 60_000L, summary.screenOnMs)
        assertEquals(20 * 60_000L, summary.longestSessionMs)
        assertEquals(1, summary.unlocks)
    }

    @Test
    fun `an open screen session is measured up to now, not to the end of the range`() {
        val events = listOf(LoggedEvent(at(20, 0), "android", EventTypes.SCREEN_INTERACTIVE))

        val summary = UsageAggregator.summarize(
            from = day,
            to = day,
            events = events,
            daily = emptyMap(),
            zone = zone,
            nowMs = at(20, 30),
        )

        assertEquals(30 * 60_000L, summary.screenOnMs)
    }

    @Test
    fun `events outside the range are ignored`() {
        val events = listOf(
            LoggedEvent(at(9, 0), "com.a", EventTypes.ACTIVITY_RESUMED),
            LoggedEvent(
                day.minusDays(1).atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
                "com.b",
                EventTypes.ACTIVITY_RESUMED,
            ),
        )

        val summary = UsageAggregator.summarize(
            from = day,
            to = day,
            events = events,
            daily = emptyMap(),
            zone = zone,
            nowMs = at(23),
        )

        assertEquals(1, summary.eventCount)
        assertEquals(1, summary.launches)
    }

    @Test
    fun `days without data render as zero rather than disappearing`() {
        val summary = UsageAggregator.summarize(
            from = day.minusDays(2),
            to = day,
            events = emptyList(),
            daily = mapOf(day to mapOf("com.a" to 1_000L)),
            zone = zone,
            nowMs = at(23),
        )

        assertEquals(3, summary.dailyTotals.size)
        assertEquals(0L, summary.dailyTotals[day.minusDays(2)])
        assertEquals(1_000L, summary.dailyTotals[day])
    }
}
