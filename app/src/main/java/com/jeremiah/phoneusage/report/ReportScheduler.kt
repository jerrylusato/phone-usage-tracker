package com.jeremiah.phoneusage.report

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jeremiah.phoneusage.data.Prefs
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Schedules the next report alarm for whichever reports are enabled.
 *
 * Alarms are inexact ([AlarmManager.setAndAllowWhileIdle]) on purpose: a summary that lands a
 * few minutes late is fine, and it avoids asking for the exact-alarm permission. Each firing
 * reschedules the next one, and boot / app update re-syncs everything.
 */
object ReportScheduler {

    const val ACTION_DAILY = "com.jeremiah.phoneusage.action.DAILY_REPORT"
    const val ACTION_WEEKLY = "com.jeremiah.phoneusage.action.WEEKLY_REPORT"

    private const val TAG = "ReportScheduler"
    private const val REQUEST_DAILY = 11
    private const val REQUEST_WEEKLY = 12

    fun sync(context: Context, now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())) {
        val prefs = Prefs(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return

        schedule(
            context = context,
            alarms = alarms,
            action = ACTION_DAILY,
            requestCode = REQUEST_DAILY,
            enabled = prefs.dailyReportEnabled,
            triggerAt = nextDaily(prefs.dailyReportMinuteOfDay, now),
        )
        schedule(
            context = context,
            alarms = alarms,
            action = ACTION_WEEKLY,
            requestCode = REQUEST_WEEKLY,
            enabled = prefs.weeklyReportEnabled,
            triggerAt = nextWeekly(prefs.weeklyReportDay.value, prefs.weeklyReportMinuteOfDay, now),
        )
    }

    fun nextDaily(minuteOfDay: Int, now: ZonedDateTime): ZonedDateTime {
        val candidate = now.with(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
        return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
    }

    fun nextWeekly(isoDayOfWeek: Int, minuteOfDay: Int, now: ZonedDateTime): ZonedDateTime {
        val day = java.time.DayOfWeek.of(isoDayOfWeek)
        val candidate = now.with(TemporalAdjusters.nextOrSame(day))
            .with(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
        return if (candidate.isAfter(now)) candidate else candidate.plusWeeks(1)
    }

    private fun schedule(
        context: Context,
        alarms: AlarmManager,
        action: String,
        requestCode: Int,
        enabled: Boolean,
        triggerAt: ZonedDateTime,
    ) {
        val intent = Intent(context, ReportReceiver::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        if (!enabled) {
            PendingIntent.getBroadcast(context, requestCode, intent, flags)?.let { pending ->
                alarms.cancel(pending)
                pending.cancel()
            }
            return
        }

        val pending = PendingIntent.getBroadcast(context, requestCode, intent, flags)
        runCatching {
            alarms.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt.toInstant().toEpochMilli(),
                pending,
            )
        }.onFailure { Log.w(TAG, "Could not schedule $action", it) }
    }
}
