package com.jeremiah.phoneusage.report

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jeremiah.phoneusage.data.Prefs
import com.jeremiah.phoneusage.work.CollectWorker

/**
 * Alarms do not survive a reboot, so they are rebuilt here. Periodic collection is restored
 * too, even though WorkManager normally reschedules itself, because an app update can drop
 * the pending alarm without clearing the work request.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> {
                val appContext = context.applicationContext
                if (Prefs(appContext).trackingEnabled) CollectWorker.enable(appContext)
                ReportScheduler.sync(appContext)
            }
        }
    }
}
