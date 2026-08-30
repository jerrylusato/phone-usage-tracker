package com.jeremiah.phoneusage.data

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate

/** Small amount of local bookkeeping: where collection got to, and whether it is on. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("phone_usage", Context.MODE_PRIVATE)

    /** Timestamp of the newest event already written to disk. Next query starts just after it. */
    var eventCursor: Long
        get() = sp.getLong(KEY_EVENT_CURSOR, 0L)
        set(value) = sp.edit().putLong(KEY_EVENT_CURSOR, value).apply()

    /** Last day whose per-app totals were refreshed from the system's daily statistics. */
    var lastDailyRollupDay: LocalDate?
        get() = sp.getString(KEY_LAST_DAILY, null)?.let { MonthKeys.parseDay(it) }
        set(value) = sp.edit().putString(KEY_LAST_DAILY, value?.toString()).apply()

    var lastCollectedAt: Long
        get() = sp.getLong(KEY_LAST_COLLECTED, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_COLLECTED, value).apply()

    var trackingEnabled: Boolean
        get() = sp.getBoolean(KEY_TRACKING, true)
        set(value) = sp.edit().putBoolean(KEY_TRACKING, value).apply()

    /** One-off flag: whether inflated days written by earlier versions were cleaned out. */
    var dailyDataRepaired: Boolean
        get() = sp.getBoolean(KEY_DATA_REPAIRED, false)
        set(value) = sp.edit().putBoolean(KEY_DATA_REPAIRED, value).apply()

    // --------------------------------------------------------- report schedule

    var dailyReportEnabled: Boolean
        get() = sp.getBoolean(KEY_DAILY_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_DAILY_ENABLED, value).apply()

    /** Minutes past midnight, local time. */
    var dailyReportMinuteOfDay: Int
        get() = sp.getInt(KEY_DAILY_TIME, 21 * 60)
        set(value) = sp.edit().putInt(KEY_DAILY_TIME, value.coerceIn(0, 24 * 60 - 1)).apply()

    var weeklyReportEnabled: Boolean
        get() = sp.getBoolean(KEY_WEEKLY_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_WEEKLY_ENABLED, value).apply()

    var weeklyReportDay: DayOfWeek
        get() = DayOfWeek.of(sp.getInt(KEY_WEEKLY_DAY, DayOfWeek.MONDAY.value))
        set(value) = sp.edit().putInt(KEY_WEEKLY_DAY, value.value).apply()

    var weeklyReportMinuteOfDay: Int
        get() = sp.getInt(KEY_WEEKLY_TIME, 9 * 60)
        set(value) = sp.edit().putInt(KEY_WEEKLY_TIME, value.coerceIn(0, 24 * 60 - 1)).apply()

    /** Cleared alongside a full data wipe so a later collection re-backfills from scratch. */
    fun resetCollectionState() {
        sp.edit()
            .remove(KEY_EVENT_CURSOR)
            .remove(KEY_LAST_DAILY)
            .apply()
    }

    private companion object {
        const val KEY_EVENT_CURSOR = "event_cursor"
        const val KEY_LAST_DAILY = "last_daily_rollup_day"
        const val KEY_LAST_COLLECTED = "last_collected_at"
        const val KEY_TRACKING = "tracking_enabled"
        const val KEY_DATA_REPAIRED = "daily_data_repaired_v2"
        const val KEY_DAILY_ENABLED = "daily_report_enabled"
        const val KEY_DAILY_TIME = "daily_report_minute"
        const val KEY_WEEKLY_ENABLED = "weekly_report_enabled"
        const val KEY_WEEKLY_DAY = "weekly_report_day"
        const val KEY_WEEKLY_TIME = "weekly_report_minute"
    }
}
