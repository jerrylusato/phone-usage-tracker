package com.jeremiah.phoneusage.report

import android.content.Context
import com.jeremiah.phoneusage.data.PeriodSummary
import com.jeremiah.phoneusage.data.UsageRepository
import com.jeremiah.phoneusage.util.AppLabels
import com.jeremiah.phoneusage.util.Format
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Turns a period summary into the text shown in a report notification (and in the in-app
 * preview). Everything is computed locally; reports never leave the device.
 */
object ReportBuilder {

    data class Report(val title: String, val body: String, val summary: PeriodSummary)

    /**
     * A daily report scheduled for the morning describes yesterday; one scheduled for
     * midday or later describes the day so far.
     */
    fun dailyRange(now: LocalDateTime): Pair<LocalDate, LocalDate> {
        val day = if (now.hour < 12) now.toLocalDate().minusDays(1) else now.toLocalDate()
        return day to day
    }

    /** The seven complete days ending yesterday. */
    fun weeklyRange(today: LocalDate): Pair<LocalDate, LocalDate> {
        val end = today.minusDays(1)
        return end.minusDays(6) to end
    }

    fun daily(
        context: Context,
        repository: UsageRepository,
        now: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault()),
    ): Report {
        val (from, to) = dailyRange(now)
        val summary = repository.summarize(from, to)
        val previous = repository.summarize(from.minusDays(1), from.minusDays(1))
        val isToday = to == now.toLocalDate()
        val heading = if (isToday) "Today so far" else Format.weekday(to) + ", " + Format.dayMonth(to)
        return Report(
            title = "$heading · ${Format.duration(summary.totalForegroundMs)}",
            body = body(context, summary, previous, if (isToday) "yesterday" else "the day before"),
            summary = summary,
        )
    }

    fun weekly(
        context: Context,
        repository: UsageRepository,
        today: LocalDate = LocalDate.now(ZoneId.systemDefault()),
    ): Report {
        val (from, to) = weeklyRange(today)
        val summary = repository.summarize(from, to)
        val previous = repository.summarize(from.minusDays(7), from.minusDays(1))
        return Report(
            title = "Week of ${Format.dayMonth(from)} · ${Format.duration(summary.totalForegroundMs)}",
            body = body(context, summary, previous, "the week before"),
            summary = summary,
        )
    }

    private fun body(
        context: Context,
        summary: PeriodSummary,
        previous: PeriodSummary,
        previousLabel: String,
    ): String {
        if (!summary.hasData) {
            return "No usage recorded yet for this period. Open Phone Usage to check that " +
                "usage access is still granted."
        }
        val lines = ArrayList<String>(5)

        if (summary.dayCount > 1) {
            val busiest = summary.busiestDay
            val busiestText = busiest?.takeIf { it.value > 0L }?.let {
                " · busiest ${Format.weekday(it.key)} ${Format.duration(it.value)}"
            }.orEmpty()
            lines += "Average ${Format.duration(summary.dailyAverageMs)}/day$busiestText"
        }

        val top = summary.apps.filter { it.foregroundMs > 0L }.take(3)
        if (top.isNotEmpty()) {
            lines += "Top: " + top.joinToString(" · ") {
                "${AppLabels.label(context, it.packageName)} ${Format.duration(it.foregroundMs)}"
            }
        }

        val activity = buildList {
            if (summary.unlocks > 0) add("${Format.count(summary.unlocks)} unlocks")
            if (summary.launches > 0) add("${Format.count(summary.launches)} app opens")
            if (summary.screenOnMs > 0) add("screen on ${Format.duration(summary.screenOnMs)}")
        }
        if (activity.isNotEmpty()) lines += activity.joinToString(" · ")

        Format.delta(summary.totalForegroundMs, previous.totalForegroundMs)?.let {
            lines += "$it vs $previousLabel (${Format.duration(previous.totalForegroundMs)})"
        }

        return lines.joinToString("\n")
    }
}
