package com.jeremiah.phoneusage.report

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jeremiah.phoneusage.MainActivity
import com.jeremiah.phoneusage.R

object Notifications {

    const val CHANNEL_REPORTS = "usage_reports"
    const val ID_DAILY = 1001
    const val ID_WEEKLY = 1002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_REPORTS,
            "Usage reports",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Scheduled daily and weekly summaries of your phone usage."
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, id: Int, report: ReportBuilder.Report) {
        ensureChannel(context)
        // Android 13+ needs the runtime permission, and the user can silence the app at any time.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REPORTS)
            .setSmallIcon(R.drawable.ic_stat_usage)
            .setContentTitle(report.title)
            .setContentText(report.body.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(report.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        runCatching { manager.notify(id, notification) }
    }
}
