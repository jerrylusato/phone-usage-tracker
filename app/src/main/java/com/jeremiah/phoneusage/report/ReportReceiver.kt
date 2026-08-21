package com.jeremiah.phoneusage.report

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jeremiah.phoneusage.data.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Fires on the scheduled report alarms: collect what is pending, notify, reschedule. */
class ReportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ReportScheduler.ACTION_DAILY && action != ReportScheduler.ACTION_WEEKLY) return

        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = UsageRepository(appContext)
                repository.collect()
                when (action) {
                    ReportScheduler.ACTION_DAILY ->
                        if (repository.prefs.dailyReportEnabled) {
                            Notifications.show(
                                appContext,
                                Notifications.ID_DAILY,
                                ReportBuilder.daily(appContext, repository),
                            )
                        }

                    ReportScheduler.ACTION_WEEKLY ->
                        if (repository.prefs.weeklyReportEnabled) {
                            Notifications.show(
                                appContext,
                                Notifications.ID_WEEKLY,
                                ReportBuilder.weekly(appContext, repository),
                            )
                        }
                }
            } finally {
                ReportScheduler.sync(appContext)
                pending.finish()
            }
        }
    }
}
