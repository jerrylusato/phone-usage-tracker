package com.jeremiah.phoneusage

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jeremiah.phoneusage.report.Notifications
import com.jeremiah.phoneusage.ui.DashboardScreen
import com.jeremiah.phoneusage.ui.HistoryScreen
import com.jeremiah.phoneusage.ui.PhoneUsageTheme
import com.jeremiah.phoneusage.ui.ReportTimePickerDialog
import com.jeremiah.phoneusage.ui.SettingsScreen
import java.time.DayOfWeek

class MainActivity : ComponentActivity() {

    private val viewModel: UsageViewModel by viewModels()
    private var resumedBefore = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.ensureChannel(this)
        viewModel.ensureScheduled()
        setContent {
            PhoneUsageTheme {
                AppRoot(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Usage access and notification permission are granted outside the app, so re-check on
        // every return. The first pass is already covered by the view model's own start-up load.
        if (resumedBefore) viewModel.onPermissionsChanged() else resumedBefore = true
    }
}

private enum class Tab { DASHBOARD, HISTORY, SETTINGS }

private enum class DeleteTarget { MONTH, OLDER, ALL }

private enum class TimeTarget { DAILY, WEEKLY }

@Composable
private fun AppRoot(viewModel: UsageViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by remember { mutableStateOf(Tab.DASHBOARD) }
    var deleteTarget by remember { mutableStateOf<DeleteTarget?>(null) }
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }
    var exportAll by remember { mutableStateOf(true) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? -> uri?.let { viewModel.export(it, exportAll) } }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.onPermissionsChanged() }

    // Reports are on by default, so ask for the notification permission once on Android 13+.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.DASHBOARD,
                    onClick = { tab = Tab.DASHBOARD },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Dashboard") },
                )
                NavigationBarItem(
                    selected = tab == Tab.HISTORY,
                    onClick = { tab = Tab.HISTORY },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    label = { Text("History") },
                )
                NavigationBarItem(
                    selected = tab == Tab.SETTINGS,
                    onClick = { tab = Tab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (state.loading || state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when (tab) {
                Tab.DASHBOARD -> DashboardScreen(
                    state = state,
                    onGrantUsageAccess = { openUsageAccessSettings(context) },
                    onRefresh = { viewModel.refresh(collectFirst = true) },
                    onOpenHistory = { tab = Tab.HISTORY },
                )

                Tab.HISTORY -> HistoryScreen(
                    state = state,
                    onSelectMonth = viewModel::selectMonth,
                    onExportMonth = {
                        exportAll = false
                        exportLauncher.launch("phone-usage-${state.selectedMonth}.json")
                    },
                    onDeleteMonth = { deleteTarget = DeleteTarget.MONTH },
                )

                Tab.SETTINGS -> SettingsScreen(
                    state = state,
                    onToggleTracking = viewModel::setTracking,
                    onCollectNow = { viewModel.refresh(collectFirst = true) },
                    onGrantUsageAccess = { openUsageAccessSettings(context) },
                    onOpenNotificationSettings = { openNotificationSettings(context) },
                    onRequestUnrestrictedBattery = { requestUnrestrictedBattery(context) },
                    onToggleDaily = viewModel::setDailyReport,
                    onPickDailyTime = { timeTarget = TimeTarget.DAILY },
                    onToggleWeekly = viewModel::setWeeklyReport,
                    onPickWeeklyTime = { timeTarget = TimeTarget.WEEKLY },
                    onPickWeeklyDay = { day: DayOfWeek -> viewModel.setWeeklyReportDay(day) },
                    onPreviewReport = viewModel::previewReport,
                    onExportAll = {
                        exportAll = true
                        exportLauncher.launch("phone-usage-all.json")
                    },
                    onDeleteMonth = { deleteTarget = DeleteTarget.MONTH },
                    onDeleteOlder = { deleteTarget = DeleteTarget.OLDER },
                    onDeleteEverything = { deleteTarget = DeleteTarget.ALL },
                )
            }
        }
    }

    state.preview?.let { report ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPreview,
            title = { Text(report.title) },
            text = { Text(report.body, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissPreview) { Text("Close") }
            },
        )
    }

    timeTarget?.let { target ->
        val current = when (target) {
            TimeTarget.DAILY -> state.reports.dailyMinuteOfDay
            TimeTarget.WEEKLY -> state.reports.weeklyMinuteOfDay
        }
        ReportTimePickerDialog(
            minuteOfDay = current,
            is24Hour = DateFormat.is24HourFormat(context),
            onDismiss = { timeTarget = null },
            onConfirm = { minuteOfDay ->
                when (target) {
                    TimeTarget.DAILY -> viewModel.setDailyReportTime(minuteOfDay)
                    TimeTarget.WEEKLY -> viewModel.setWeeklyReportTime(minuteOfDay)
                }
                timeTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        val (title, body) = when (target) {
            DeleteTarget.MONTH -> "Delete ${state.selectedMonth}?" to
                "Every event and daily total recorded for ${state.selectedMonth} will be removed from this phone."

            DeleteTarget.OLDER -> "Delete everything before ${state.selectedMonth}?" to
                "All months older than ${state.selectedMonth} will be removed. Newer data is kept."

            DeleteTarget.ALL -> "Delete all data?" to
                "Every month of recorded usage will be removed from this phone."
        }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (target) {
                            DeleteTarget.MONTH -> viewModel.deleteMonth(state.selectedMonth)
                            DeleteTarget.OLDER -> viewModel.deleteOlderThan(state.selectedMonth)
                            DeleteTarget.ALL -> viewModel.deleteEverything()
                        }
                        deleteTarget = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

private fun openUsageAccessSettings(context: Context) {
    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    if (!start(context, intent)) start(context, Intent(Settings.ACTION_SETTINGS))
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    if (!start(context, intent)) {
        start(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null)),
        )
    }
}

private fun requestUnrestrictedBattery(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.fromParts("package", context.packageName, null))
    if (!start(context, intent)) {
        start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

private fun start(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}
