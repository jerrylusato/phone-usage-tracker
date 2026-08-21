package com.jeremiah.phoneusage.data

import android.content.Context
import java.io.OutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Single entry point the UI, the workers and the report receiver all go through. */
class UsageRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = EventStore(appContext.filesDir)
    private val collector = UsageCollector(appContext)

    val prefs = Prefs(appContext)

    fun hasUsageAccess(): Boolean = collector.hasUsageAccess()

    fun collect(nowMs: Long = System.currentTimeMillis()): CollectResult = collector.collect(nowMs)

    fun summarize(
        from: LocalDate,
        to: LocalDate,
        nowMs: Long = System.currentTimeMillis(),
    ): PeriodSummary {
        val months = monthKeysBetween(from, to)
        return UsageAggregator.summarize(
            from = from,
            to = to,
            events = store.readEvents(months),
            daily = store.readDailyTotals(months),
            zone = ZoneId.systemDefault(),
            nowMs = nowMs,
        )
    }

    fun summarizeMonth(monthKey: String, nowMs: Long = System.currentTimeMillis()): PeriodSummary? {
        val month = MonthKeys.parseMonth(monthKey) ?: return null
        val today = LocalDate.now(ZoneId.systemDefault())
        val from = month.atDay(1)
        val to = minOf(month.atEndOfMonth(), maxOf(today, from))
        return summarize(from, to, nowMs)
    }

    fun months(): List<String> {
        val stored = store.months()
        val current = MonthKeys.currentMonth()
        return if (stored.contains(current)) stored else (listOf(current) + stored)
    }

    fun storageBytes(): Long = store.storageBytes()

    fun eventCount(month: String): Int = store.eventCount(month)

    fun deleteMonth(month: String) {
        store.deleteMonth(month)
    }

    fun deleteBefore(month: String): Int = store.deleteBefore(month)

    fun deleteEverything(): Int {
        val deleted = store.deleteAll()
        prefs.resetCollectionState()
        return deleted
    }

    fun export(out: OutputStream, months: List<String>) = store.export(out, months)

    private fun monthKeysBetween(from: LocalDate, to: LocalDate): List<String> {
        val keys = ArrayList<String>()
        var month = YearMonth.from(from)
        val last = YearMonth.from(to)
        while (!month.isAfter(last)) {
            keys += MonthKeys.monthOf(month.atDay(1))
            month = month.plusMonths(1)
        }
        return keys
    }
}
