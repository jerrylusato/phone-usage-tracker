package com.jeremiah.phoneusage.ui

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
import com.jeremiah.phoneusage.data.PeriodSummary
import com.jeremiah.phoneusage.util.Format
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun DashboardScreen(
    state: UiState,
    onGrantUsageAccess: () -> Unit,
    onRefresh: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = state.today
    val week = state.week
    var appScope by remember { mutableStateOf(AppScope.TODAY) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Greeting(state) }

        if (!state.hasUsageAccess) {
            item { UsageAccessCard(onGrantUsageAccess) }
        }

        item { TodayHero(today = today, week = week, yesterday = state.yesterday) }

        item {
            TodayStats(today)
        }

        if (week != null) {
            item {
                SectionCard(
                    title = "Last 7 days",
                    subtitle = buildString {
                        append(Format.duration(week.totalForegroundMs))
                        append(" total · avg ")
                        append(Format.duration(week.dailyAverageMs))
                        Format.delta(week.totalForegroundMs, state.previousWeek?.totalForegroundMs ?: 0L)
                            ?.let { append(" · $it vs previous week") }
                    },
                ) {
                    Column {
                        DailyBarChart(
                            data = week.dailyTotals.toList(),
                            selected = selectedDay,
                            onSelect = { day -> selectedDay = if (selectedDay == day) null else day },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val day = selectedDay
                        if (day != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "${Format.weekday(day)} ${Format.dayMonth(day)} · " +
                                    Format.duration(week.dailyTotals[day] ?: 0L),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            val source = if (appScope == AppScope.TODAY) today else week
            SectionCard(
                title = "Top apps",
                subtitle = if (appScope == AppScope.TODAY) "Today" else "Last 7 days",
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ScopeChip("Today", appScope == AppScope.TODAY) { appScope = AppScope.TODAY }
                        ScopeChip("7 days", appScope == AppScope.WEEK) { appScope = AppScope.WEEK }
                    }
                },
            ) {
                val apps = source?.apps?.filter { it.foregroundMs > 0L }?.take(6).orEmpty()
                if (apps.isEmpty()) {
                    EmptyState(
                        if (state.hasUsageAccess) {
                            "No app usage recorded for this period yet."
                        } else {
                            "Grant usage access to start recording."
                        },
                    )
                } else {
                    Column {
                        val max = apps.first().foregroundMs
                        apps.forEach { app -> AppUsageRow(app = app, maxMs = max) }
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = onOpenHistory) { Text("See every app and month") }
                    }
                }
            }
        }

        item { CollectionFooter(state, onRefresh) }
    }
}

private enum class AppScope { TODAY, WEEK }

@Composable
private fun Greeting(state: UiState) {
    val now = remember { LocalTime.now(ZoneId.systemDefault()) }
    val part = when {
        now.hour < 12 -> "Good morning"
        now.hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    Column {
        Text(
            text = part,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = Format.dayMonth(LocalDate.now(ZoneId.systemDefault())) +
                if (state.trackingEnabled) " · tracking on" else " · tracking paused",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UsageAccessCard(onGrant: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Usage access needed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Android only reports app usage to apps you allow explicitly. Tap below, " +
                    "find Phone Usage in the list and turn on \"Permit usage tracking\".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onGrant) { Text("Open usage access settings") }
        }
    }
}

@Composable
private fun TodayHero(today: PeriodSummary?, week: PeriodSummary?, yesterday: PeriodSummary?) {
    val total = today?.totalForegroundMs ?: 0L
    val reference = maxOf(
        week?.dailyTotals?.values?.maxOrNull() ?: 0L,
        yesterday?.totalForegroundMs ?: 0L,
        1L,
    )
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UsageRing(
                progress = total.toFloat() / reference.toFloat(),
                modifier = Modifier.size(132.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = Format.duration(total),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "screen time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Format.delta(total, yesterday?.totalForegroundMs ?: 0L)?.let { delta ->
                    Pill(
                        text = "$delta vs yesterday",
                        container = if (delta.startsWith("-")) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                    )
                }
                Text(
                    text = "Yesterday " + Format.duration(yesterday?.totalForegroundMs ?: 0L),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${today?.apps?.count { it.foregroundMs > 0L } ?: 0} apps used",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TodayStats(today: PeriodSummary?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = "Unlocks",
                value = Format.count(today?.unlocks ?: 0),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "App opens",
                value = Format.count(today?.launches ?: 0),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = "Screen on",
                value = Format.duration(today?.screenOnMs ?: 0L),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Longest session",
                value = Format.duration(today?.longestSessionMs ?: 0L),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CollectionFooter(state: UiState, onRefresh: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Last collected ${Format.relative(state.lastCollectedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${Format.bytes(state.storageBytes)} stored on this phone · never uploaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onRefresh, enabled = !state.busy) {
                Text(if (state.busy) "Working…" else "Refresh")
            }
        }
    }
}

@Composable
fun Pill(text: String, container: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ScopeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
