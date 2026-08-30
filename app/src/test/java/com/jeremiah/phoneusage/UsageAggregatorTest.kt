package com.jeremiah.phoneusage

import com.jeremiah.phoneusage.data.EventTypes
import com.jeremiah.phoneusage.data.LoggedEvent
import com.jeremiah.phoneusage.data.UsageAggregator
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAggregatorTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val day: LocalDate = LocalDate.of(2026, 8, 20)

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = day): Long =
        date.atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun minutes(count: Int): Long = count * 60_000L

    private fun summarize(
        events: List<LoggedEvent>,
        daily: Map<LocalDate, Map<String, Long>> = emptyMap(),
        from: LocalDate = day,
        to: LocalDate = day,
        nowMs: Long = at(23, 59),
    ) = UsageAggregator.summarize(from, to, events, daily, zone, nowMs)

    @Test
    fun `an app playing with the screen off earns no time`() {
        val summary = summarize(
            listOf(
                LoggedEvent(at(8, 0), "android", EventTypes.SCREEN_INTERACTIVE),
                LoggedEvent(at(8, 5), "com.music", EventTypes.ACTIVITY_RESUMED),
                LoggedEvent(at(8, 10), "android", EventTypes.SCREEN_NON_INTERACTIVE),
            ),
            nowMs = at(9, 0),
        )

        // Five minutes of looking at it, then fifty minutes of dark-screen playback.
        assertEquals(minutes(5), summary.apps.single().timeMs)
        assertEquals(minutes(10), summary.screenTimeMs)
        assertEquals(minutes(5), summary.appTimeMs)
    }

    @Test
    fun `screen time is measured, not taken from foreground totals`() {
        val summary = summarize(
            listOf(
                LoggedEvent(at(8, 0), "android", EventTypes.SCREEN_INTERACTIVE),
                LoggedEvent(at(8, 1), "android", EventTypes.KEYGUARD_HIDDEN),
                LoggedEvent(at(8, 20), "android", EventTypes.SCREEN_NON_INTERACTIVE),
                LoggedEvent(at(12, 0), "android", EventTypes.SCREEN_INTERACTIVE),
                LoggedEvent(at(12, 5), "android", EventTypes.SCREEN_NON_INTERACTIVE),
            ),
            // The platform claims nine hours of foreground time for this day; it is ignored,
            // because the day has events of its own.
            daily = mapOf(day to mapOf("com.music" to 9 * 60 * 60_000L)),
        )

        assertEquals(minutes(25), summary.screenTimeMs)
        assertEquals(minutes(20), summary.longestSessionMs)
        assertEquals(1, summary.unlocks)
        assertTrue(summary.estimatedDays.isEmpty())
    }

    @Test
    fun `a session already running when the range opens is credited from the day before`() {
        val summary = summarize(
            listOf(
                LoggedEvent(at(23, 50, day.minusDays(1)), "android", EventTypes.SCREEN_INTERACTIVE),
                LoggedEvent(at(0, 10), "android", EventTypes.SCREEN_NON_INTERACTIVE),
            ),
        )

        assertEquals(minutes(10), summary.screenTimeMs)
    }

    @Test
    fun `without earlier evidence the inferred session starts at the first event, not midnight`() {
        val summary = summarize(
            listOf(
                LoggedEvent(at(8, 0), "com.a", EventTypes.ACTIVITY_RESUMED),
                LoggedEvent(at(8, 30), "android", EventTypes.SCREEN_NON_INTERACTIVE),
            ),
        )

        // Half an hour of evidence, not the eight and a half hours since midnight.
        assertEquals(minutes(30), summary.screenTimeMs)
    }

    @Test
    fun `screen time is split across midnight into the right days`() {
        val summary = summarize(
            listOf(
                LoggedEvent(at(23, 40), "android", EventTypes.SCREEN_INTERACTIVE),
                LoggedEvent(at(0, 20, day.plusDays(1)), "android", EventTypes.SCREEN_NON_INTERACTIVE),
            ),
            from = day,
            to = day.plusDays(1),
            nowMs = at(12, 0, day.plusDays(1)),
        )

        assertEquals(minutes(20), summary.dailyTotals[day])
        assertEquals(minutes(20), summary.dailyTotals[day.plusDays(1)])
        assertEquals(minutes(40), summary.screenTimeMs)
    }

    @Test
    fun `an app opened while the screen is off is not counted as a launch`() {
        val summary = summarize(
            listOf(
                LoggedEvent(at(8, 0), "android", EventTypes.SCREEN_INTERACTIVE),
                LoggedEvent(at(8, 1), "com.a", EventTypes.ACTIVITY_RESUMED),
                LoggedEvent(at(8, 2), "android", EventTypes.SCREEN_NON_INTERACTIVE),
                LoggedEvent(at(9, 0), "com.b", EventTypes.ACTIVITY_RESUMED),
            ),
            nowMs = at(10, 0),
        )

        assertEquals(1, summary.launches)
        // com.b came to the foreground with the screen dark: no time, no launch, no listing.
        assertTrue(summary.apps.none { it.packageName == "com.b" })
    }

    @Test
    fun `switching between apps counts one launch per switch`() {
        val summary = summarize(
            listOf(
                LoggedEvent(at(9, 0), "android", EventTypes.SCREEN_INTERACTIVE),
                LoggedEvent(at(9, 1), "com.a", EventTypes.ACTIVITY_RESUMED),
                LoggedEvent(at(9, 2), "com.a", EventTypes.ACTIVITY_RESUMED),
                LoggedEvent(at(9, 3), "com.b", EventTypes.ACTIVITY_RESUMED),
                LoggedEvent(at(9, 4), "com.a", EventTypes.ACTIVITY_RESUMED),
                LoggedEvent(at(9, 5), "android", EventTypes.SCREEN_NON_INTERACTIVE),
            ),
            nowMs = at(10, 0),
        )

        assertEquals(3, summary.launches)
        assertEquals(2, summary.apps.first { it.packageName == "com.a" }.launches)
        assertEquals(minutes(3), summary.apps.first { it.packageName == "com.a" }.timeMs)
        assertEquals(minutes(1), summary.apps.first { it.packageName == "com.b" }.timeMs)
    }

    @Test
    fun `moving between activities inside one app is a single open`() {
        val summary = summarize(
            listOf(
                LoggedEvent(at(9, 0), "android", EventTypes.SCREEN_INTERACTIVE),
                LoggedEvent(at(9, 1), "com.whatsapp", EventTypes.ACTIVITY_RESUMED),
                LoggedEvent(at(9, 2), "com.whatsapp", EventTypes.ACTIVITY_PAUSED),
                LoggedEvent(at(9, 2), "com.whatsapp", EventTypes.ACTIVITY_RESUMED),
                LoggedEvent(at(9, 3), "com.whatsapp", EventTypes.ACTIVITY_PAUSED),
                LoggedEvent(at(9, 3), "com.whatsapp", EventTypes.ACTIVITY_RESUMED),
                LoggedEvent(at(9, 5), "android", EventTypes.SCREEN_NON_INTERACTIVE),
            ),
            nowMs = at(10, 0),
        )

        // Chat list to a chat to a photo is one open, not three.
        assertEquals(1, summary.launches)
        assertEquals(minutes(4), summary.apps.single().timeMs)
    }

    @Test
    fun `returning to the same app after the screen went off is a new open`() {
        val summary = summarize(
            listOf(
                LoggedEvent(at(9, 0), "android", EventTypes.SCREEN_INTERACTIVE),
                LoggedEvent(at(9, 1), "com.whatsapp", EventTypes.ACTIVITY_RESUMED),
                LoggedEvent(at(9, 2), "android", EventTypes.SCREEN_NON_INTERACTIVE),
                LoggedEvent(at(9, 30), "android", EventTypes.SCREEN_INTERACTIVE),
                LoggedEvent(at(9, 31), "com.whatsapp", EventTypes.ACTIVITY_RESUMED),
                LoggedEvent(at(9, 33), "android", EventTypes.SCREEN_NON_INTERACTIVE),
            ),
            nowMs = at(10, 0),
        )

        assertEquals(2, summary.launches)
    }

    @Test
    fun `days without events fall back to platform totals and are marked as estimates`() {
        val summary = summarize(
            listOf(
                LoggedEvent(at(9, 0), "android", EventTypes.SCREEN_INTERACTIVE),
                LoggedEvent(at(9, 30), "android", EventTypes.SCREEN_NON_INTERACTIVE),
            ),
            daily = mapOf(
                day.minusDays(1) to mapOf("com.a" to minutes(45)),
                day to mapOf("com.a" to minutes(600)),
            ),
            from = day.minusDays(1),
            to = day,
        )

        assertEquals(setOf(day.minusDays(1)), summary.estimatedDays)
        assertEquals(minutes(45), summary.dailyTotals[day.minusDays(1)])
        assertEquals(minutes(30), summary.dailyTotals[day])
        assertEquals(minutes(75), summary.screenTimeMs)
    }

    @Test
    fun `an open session is measured up to now, not to the end of the range`() {
        val summary = summarize(
            listOf(LoggedEvent(at(20, 0), "android", EventTypes.SCREEN_INTERACTIVE)),
            nowMs = at(20, 30),
        )

        assertEquals(minutes(30), summary.screenTimeMs)
    }

    @Test
    fun `events outside the range are ignored`() {
        val summary = summarize(
            listOf(
                LoggedEvent(at(9, 0), "android", EventTypes.SCREEN_INTERACTIVE),
                LoggedEvent(at(9, 5), "android", EventTypes.SCREEN_NON_INTERACTIVE),
                LoggedEvent(at(9, 0, day.minusDays(1)), "android", EventTypes.SCREEN_INTERACTIVE),
                LoggedEvent(at(9, 5, day.minusDays(1)), "android", EventTypes.SCREEN_NON_INTERACTIVE),
            ),
        )

        assertEquals(2, summary.eventCount)
        assertEquals(minutes(5), summary.screenTimeMs)
    }
}
