package com.jeremiah.phoneusage.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeremiah.phoneusage.UiState
import com.jeremiah.phoneusage.util.Format
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Suppress("LongParameterList")
@Composable
fun SettingsScreen(
    state: UiState,
    onToggleTracking: (Boolean) -> Unit,
    onCollectNow: () -> Unit,
    onGrantUsageAccess: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestUnrestrictedBattery: () -> Unit,
    onToggleDaily: (Boolean) -> Unit,
    onPickDailyTime: () -> Unit,
    onToggleWeekly: (Boolean) -> Unit,
    onPickWeeklyTime: () -> Unit,
    onPickWeeklyDay: (DayOfWeek) -> Unit,
    onPreviewReport: (Boolean) -> Unit,
    onExportAll: () -> Unit,
    onDeleteMonth: () -> Unit,
    onDeleteOlder: () -> Unit,
    onDeleteEverything: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val use24Hour = android.text.format.DateFormat.is24HourFormat(
        androidx.compose.ui.platform.LocalContext.current,
    )
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        item {
            SectionCard(
                title = "Reports",
                subtitle = "Summaries are built on the phone and delivered as notifications.",
            ) {
                Column {
                    SwitchRow(
                        title = "Weekly report",
                        subtitle = "Covers the last complete week, Monday to Sunday",
                        checked = state.reports.weeklyEnabled,
                        onCheckedChange = onToggleWeekly,
                    )
                    if (state.reports.weeklyEnabled) {
                        ValueRow(
                            title = "Send at",
                            value = "${state.reports.weeklyDay.display()} · " +
                                Format.timeOfDay(state.reports.weeklyMinuteOfDay, use24Hour),
                            onClick = onPickWeeklyTime,
                        )
                        Spacer(Modifier.height(4.dp))
                        WeekdayPicker(
                            selected = state.reports.weeklyDay,
                            onSelect = onPickWeeklyDay,
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))

                    SwitchRow(
                        title = "Daily report",
                        subtitle = "Before noon it covers yesterday, later it covers today so far",
                        checked = state.reports.dailyEnabled,
                        onCheckedChange = onToggleDaily,
                    )
                    if (state.reports.dailyEnabled) {
                        ValueRow(
                            title = "Send at",
                            value = Format.timeOfDay(state.reports.dailyMinuteOfDay, use24Hour),
                            onClick = onPickDailyTime,
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { onPreviewReport(true) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Preview weekly") }
                        FilledTonalButton(
                            onClick = { onPreviewReport(false) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Preview daily") }
                    }

                    if (!state.notificationsEnabled) {
                        Spacer(Modifier.height(10.dp))
                        WarningRow(
                            text = "Notifications are blocked, so reports cannot be delivered.",
                            actionLabel = "Fix",
                            onAction = onOpenNotificationSettings,
                        )
                    }
                    if (!state.batteryUnrestricted) {
                        Spacer(Modifier.height(10.dp))
                        WarningRow(
                            text = "One UI battery management may delay reports and collection. " +
                                "Allowing unrestricted background use keeps them on time.",
                            actionLabel = "Allow",
                            onAction = onRequestUnrestrictedBattery,
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "Collection") {
                Column {
                    SwitchRow(
                        title = "Track usage",
                        subtitle = "Pausing stops new recording. Nothing already stored is removed.",
                        checked = state.trackingEnabled,
                        onCheckedChange = onToggleTracking,
                    )
                    ValueRow(
                        title = "Usage access",
                        value = if (state.hasUsageAccess) "Granted" else "Not granted",
                        onClick = onGrantUsageAccess,
                    )
                    ValueRow(
                        title = "Last collected",
                        value = Format.relative(state.lastCollectedAt),
                        onClick = onCollectNow,
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onCollectNow, enabled = !state.busy) {
                        Text(if (state.busy) "Working…" else "Collect now")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Collection runs about every 30 minutes in the background. Android " +
                            "keeps its own recent history, so a missed run is backfilled on the next one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Your data",
                subtitle = "${Format.bytes(state.storageBytes)} in ${state.months.size} month(s)",
            ) {
                Column {
                    Text(
                        text = "Data is kept until you delete it — there is no automatic expiry. " +
                            "Deletion works one month at a time.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = onExportAll, modifier = Modifier.fillMaxWidth()) {
                        Text("Export everything as JSON")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onDeleteMonth, modifier = Modifier.fillMaxWidth()) {
                        Text("Delete ${state.selectedMonth}")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onDeleteOlder, modifier = Modifier.fillMaxWidth()) {
                        Text("Delete everything before ${state.selectedMonth}")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onDeleteEverything,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Delete all data")
                    }
                }
            }
        }

        item {
            SectionCard(title = "Privacy") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrivacyLine("No internet permission is declared, so nothing can be uploaded.")
                    PrivacyLine("Data lives in this app's private storage and is removed if you uninstall.")
                    PrivacyLine("Only usage statistics are read — no messages, contacts, location or camera.")
                    PrivacyLine("Reports are generated on the phone and shown as local notifications.")
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun WeekdayPicker(selected: DayOfWeek, onSelect: (DayOfWeek) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            val isSelected = day == selected
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                onClick = { onSelect(day) },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun WarningRow(text: String, actionLabel: String, onAction: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun PrivacyLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun DayOfWeek.display(): String =
    getDisplayName(TextStyle.FULL, Locale.getDefault())
