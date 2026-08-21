package com.jeremiah.phoneusage.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeremiah.phoneusage.UiState
import com.jeremiah.phoneusage.data.MonthKeys
import com.jeremiah.phoneusage.util.Format
import java.time.LocalDate

@Composable
fun HistoryScreen(
    state: UiState,
    onSelectMonth: (String) -> Unit,
    onExportMonth: () -> Unit,
    onDeleteMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val month = state.month
    var selectedDay by remember(state.selectedMonth) { mutableStateOf<LocalDate?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "History",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.months) { key ->
                    val label = MonthKeys.parseMonth(key)?.let { Format.monthTitle(it.atDay(1)) } ?: key
                    MonthChip(
                        label = label,
                        selected = key == state.selectedMonth,
                        onClick = { onSelectMonth(key) },
                    )
                }
            }
        }

        if (month == null || !month.hasData) {
            item {
                SectionCard(title = "No data for this month") {
                    EmptyState(
                        "Nothing has been recorded for this month yet. Data appears once " +
                            "collection runs with usage access granted.",
                    )
                }
            }
        } else {
            item {
                SectionCard(
                    title = MonthKeys.parseMonth(state.selectedMonth)
                        ?.let { Format.monthTitle(it.atDay(1)) } ?: state.selectedMonth,
                    subtitle = "${Format.duration(month.totalForegroundMs)} total · " +
                        "avg ${Format.duration(month.dailyAverageMs)}/day · " +
                        "${month.daysWithData} day(s) with data",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                label = "Unlocks",
                                value = Format.count(month.unlocks),
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = "App opens",
                                value = Format.count(month.launches),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                label = "Screen on",
                                value = Format.duration(month.screenOnMs),
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = "Events kept",
                                value = Format.count(month.eventCount),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Daily screen time") {
                    Column {
                        // One chart in a scrolling row, so every bar shares the same scale.
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            DailyBarChart(
                                data = month.dailyTotals.toList(),
                                barWidth = 22.dp,
                                selected = selectedDay,
                                onSelect = { day -> selectedDay = if (selectedDay == day) null else day },
                            )
                        }
                        val day = selectedDay
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (day != null) {
                                "${Format.weekday(day)} ${Format.dayMonth(day)} · " +
                                    Format.duration(month.dailyTotals[day] ?: 0L)
                            } else {
                                "Tap a bar for that day's total. Scroll for the rest of the month."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SectionCard(
                    title = "Every app",
                    subtitle = "${month.apps.count { it.foregroundMs > 0L }} apps with recorded time",
                ) {
                    Column {
                        val apps = month.apps.filter { it.foregroundMs > 0L || it.launches > 0 }
                        val max = apps.maxOfOrNull { it.foregroundMs } ?: 1L
                        apps.forEach { app -> AppUsageRow(app = app, maxMs = max) }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onExportMonth, modifier = Modifier.weight(1f)) {
                        Text("Export month")
                    }
                    OutlinedButton(onClick = onDeleteMonth, modifier = Modifier.weight(1f)) {
                        Text("Delete month")
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
