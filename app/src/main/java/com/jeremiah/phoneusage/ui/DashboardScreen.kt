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

/** Which period the week chart and the app list are showing. */
private enum class Scope(val label: String, val short: String) {
    TODAY("Today", "Today"),
    THIS_WEEK("This week", "This wk"),
    LAST_WEEK("Last week", "Last wk"),
}

@Composable
fun DashboardScreen(
    state: UiState,
    onGrantUsageAccess: () -> Unit,
    onRefresh: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now(ZoneId.systemDefault()) }
    var weekScope by remember { mutableStateOf(Scope.THIS_WEEK) }
    var appScope by remember { mutableStateOf(Scope.TODAY) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    fun summaryFor(scope: Scope): PeriodSummary? = when (scope) {
        Scope.TODAY -> state.today
        Scope.THIS_WEEK -> state.thisWeek
        Scope.LAST_WEEK -> state.lastWeek
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Greeting(state) }

        if (!state.hasUsageAccess) {
            item { UsageAccessCard(onGrantUsageAccess) }
        }

        item { TodayHero(today = state.today, week = state.thisWeek, yesterday = state.yesterday) }

        item { TodayStats(state.today) }

        item {
            val week = summaryFor(weekScope)
            val comparison = when (weekScope) {
                Scope.LAST_WEEK -> state.weekBeforeLast
                else -> state.lastWeekToDate
            }
            val comparisonLabel = when (weekScope) {
                Scope.LAST_WEEK -> "vs the week before"
                else -> "vs the same days last week"
            }
            SectionCard(
                title = weekScope.label,
                subtitle = week?.let { weekSubtitle(it, comparison, comparisonLabel) },
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ScopeChip(Scope.THIS_WEEK.label, weekScope == Scope.THIS_WEEK) {
                            weekScope = Scope.THIS_WEEK
                            selectedDay = null
                        }
                        ScopeChip(Scope.LAST_WEEK.label, weekScope == Scope.LAST_WEEK) {
                            weekScope = Scope.LAST_WEEK
                            selectedDay = null
                        }
                    }
                },
            ) {
                if (week == null || week.dailyTotals.isEmpty()) {
                    EmptyState("Nothing recorded for this week yet.")
                } else {
                    Column {
                        DailyBarChart(
                            data = week.dailyTotals.toList(),
                            selected = selectedDay,
                            highlight = today.takeIf { weekScope == Scope.THIS_WEEK },
                            estimated = week.estimatedDays,
                            onSelect = { day -> selectedDay = if (selectedDay == day) null else day },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val day = selectedDay
                        if (day != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "${Format.weekday(day)} ${Format.dayMonth(day)} · " +
                                    Format.duration(week.dailyTotals[day] ?: 0L) +
                                    if (day in week.estimatedDays) " (estimated)" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (week.hasEstimates) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Faded bars predate this app and come from Android's own " +
                                    "daily totals, which count app time rather than screen time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            val source = summaryFor(appScope)
            SectionCard(
                title = "Top apps",
                subtitle = appScope.label,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Scope.entries.forEach { scope ->
                            ScopeChip(scope.short, appScope == scope) { appScope = scope }
                        }
                    }
                },
            ) {
                val apps = source?.apps?.filter { it.timeMs > 0L }?.take(6).orEmpty()
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
                        val max = apps.first().timeMs
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

private fun weekSubtitle(
    week: PeriodSummary,
    comparison: PeriodSummary?,
    comparisonLabel: String,
): String = buildString {
    append(Format.duration(week.screenTimeMs))
    append(" screen time · avg ")
    append(Format.duration(week.dailyAverageMs))
    Format.delta(week.screenTimeMs, comparison?.screenTimeMs ?: 0L)?.let {
        append(" · $it $comparisonLabel")
    }
}

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
    val total = today?.screenTimeMs ?: 0L
    val reference = maxOf(
        week?.dailyTotals?.values?.maxOrNull() ?: 0L,
        yesterday?.screenTimeMs ?: 0L,
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
                        text = "screen on",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Format.delta(total, yesterday?.screenTimeMs ?: 0L)?.let { delta ->
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
                    text = "Yesterday " + Format.duration(yesterday?.screenTimeMs ?: 0L),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${today?.apps?.count { it.timeMs > 0L } ?: 0} apps used",
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
                label = "In apps",
                value = Format.duration(today?.appTimeMs ?: 0L),
                hint = "of your screen time",
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
