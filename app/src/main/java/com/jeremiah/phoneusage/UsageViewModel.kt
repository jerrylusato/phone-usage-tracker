package com.jeremiah.phoneusage

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeremiah.phoneusage.data.MonthKeys
import com.jeremiah.phoneusage.data.PeriodSummary
import com.jeremiah.phoneusage.data.UsageRepository
import com.jeremiah.phoneusage.data.Weeks
import com.jeremiah.phoneusage.report.ReportBuilder
import com.jeremiah.phoneusage.report.ReportScheduler
import com.jeremiah.phoneusage.work.CollectWorker
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReportSettings(
    val dailyEnabled: Boolean = false,
    val dailyMinuteOfDay: Int = 21 * 60,
    val weeklyEnabled: Boolean = true,
    val weeklyDay: DayOfWeek = DayOfWeek.MONDAY,
    val weeklyMinuteOfDay: Int = 9 * 60,
)

data class UiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val trackingEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val batteryUnrestricted: Boolean = true,
    val lastCollectedAt: Long = 0L,
    val today: PeriodSummary? = null,
    val yesterday: PeriodSummary? = null,
    /** Monday to Sunday of the current week; days still ahead read as no data. */
    val thisWeek: PeriodSummary? = null,
    val lastWeek: PeriodSummary? = null,
    /** Monday through today's weekday, one week back — the honest partial-week comparison. */
    val lastWeekToDate: PeriodSummary? = null,
    val weekBeforeLast: PeriodSummary? = null,
    val months: List<String> = emptyList(),
    val selectedMonth: String = MonthKeys.currentMonth(),
    val month: PeriodSummary? = null,
    val storageBytes: Long = 0L,
    val reports: ReportSettings = ReportSettings(),
    val preview: ReportBuilder.Report? = null,
    val message: String? = null,
)

class UsageViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UsageRepository(application)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh(collectFirst = true)
    }

    // ------------------------------------------------------------------ loading

    fun refresh(collectFirst: Boolean = true) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            var collectedMessage: String? = null
            val snapshot = withContext(Dispatchers.IO) {
                if (collectFirst) {
                    val result = repository.collect()
                    if (result.granted && result.newEvents > 0) {
                        collectedMessage = "${result.newEvents} new events recorded"
                    }
                }
                snapshot(_state.value.selectedMonth)
            }
            _state.update { current ->
                current.copy(
                    loading = false,
                    busy = false,
                    hasUsageAccess = snapshot.hasUsageAccess,
                    trackingEnabled = snapshot.trackingEnabled,
                    notificationsEnabled = snapshot.notificationsEnabled,
                    batteryUnrestricted = snapshot.batteryUnrestricted,
                    lastCollectedAt = snapshot.lastCollectedAt,
                    today = snapshot.today,
                    yesterday = snapshot.yesterday,
                    thisWeek = snapshot.thisWeek,
                    lastWeek = snapshot.lastWeek,
                    lastWeekToDate = snapshot.lastWeekToDate,
                    weekBeforeLast = snapshot.weekBeforeLast,
                    months = snapshot.months,
                    selectedMonth = snapshot.selectedMonth,
                    month = snapshot.month,
                    storageBytes = snapshot.storageBytes,
                    reports = snapshot.reports,
                    message = collectedMessage ?: current.message,
                )
            }
        }
    }

    private class Snapshot(
        val hasUsageAccess: Boolean,
        val trackingEnabled: Boolean,
        val notificationsEnabled: Boolean,
        val batteryUnrestricted: Boolean,
        val lastCollectedAt: Long,
        val today: PeriodSummary,
        val yesterday: PeriodSummary,
        val thisWeek: PeriodSummary,
        val lastWeek: PeriodSummary,
        val lastWeekToDate: PeriodSummary,
        val weekBeforeLast: PeriodSummary,
        val months: List<String>,
        val selectedMonth: String,
        val month: PeriodSummary?,
        val storageBytes: Long,
        val reports: ReportSettings,
    )

    private fun snapshot(requestedMonth: String): Snapshot {
        val context = getApplication<Application>()
        val today = LocalDate.now(ZoneId.systemDefault())
        val months = repository.months()
        val month = if (months.contains(requestedMonth)) requestedMonth else months.firstOrNull()
            ?: MonthKeys.currentMonth()
        val prefs = repository.prefs
        val ranges = listOf(
            today..today,
            today.minusDays(1)..today.minusDays(1),
            Weeks.thisWeek(today),
            Weeks.lastWeek(today),
            Weeks.sameSliceLastWeek(today),
            Weeks.weekBeforeLast(today),
        )
        val summaries = repository.summarizeMany(ranges)
        return Snapshot(
            hasUsageAccess = repository.hasUsageAccess(),
            trackingEnabled = prefs.trackingEnabled,
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            batteryUnrestricted = isBatteryUnrestricted(context),
            lastCollectedAt = prefs.lastCollectedAt,
            today = summaries[0],
            yesterday = summaries[1],
            thisWeek = summaries[2],
            lastWeek = summaries[3],
            lastWeekToDate = summaries[4],
            weekBeforeLast = summaries[5],
            months = months,
            selectedMonth = month,
            month = repository.summarizeMonth(month),
            storageBytes = repository.storageBytes(),
            reports = ReportSettings(
                dailyEnabled = prefs.dailyReportEnabled,
                dailyMinuteOfDay = prefs.dailyReportMinuteOfDay,
                weeklyEnabled = prefs.weeklyReportEnabled,
                weeklyDay = prefs.weeklyReportDay,
                weeklyMinuteOfDay = prefs.weeklyReportMinuteOfDay,
            ),
        )
    }

    private fun isBatteryUnrestricted(context: Context): Boolean =
        runCatching {
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        }.getOrDefault(true)

    // ------------------------------------------------------------------ actions

    fun selectMonth(month: String) {
        _state.update { it.copy(selectedMonth = month, busy = true) }
        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) { repository.summarizeMonth(month) }
            _state.update { it.copy(month = summary, busy = false) }
        }
    }

    fun setTracking(enabled: Boolean) {
        val context = getApplication<Application>()
        repository.prefs.trackingEnabled = enabled
        if (enabled) CollectWorker.enable(context) else CollectWorker.disable(context)
        _state.update {
            it.copy(
                trackingEnabled = enabled,
                message = if (enabled) "Collection resumed" else "Collection paused — nothing is deleted",
            )
        }
    }

    fun setDailyReport(enabled: Boolean) = updateReports { prefs ->
        prefs.dailyReportEnabled = enabled
    }

    fun setDailyReportTime(minuteOfDay: Int) = updateReports { prefs ->
        prefs.dailyReportMinuteOfDay = minuteOfDay
    }

    fun setWeeklyReport(enabled: Boolean) = updateReports { prefs ->
        prefs.weeklyReportEnabled = enabled
    }

    fun setWeeklyReportDay(day: DayOfWeek) = updateReports { prefs ->
        prefs.weeklyReportDay = day
    }

    fun setWeeklyReportTime(minuteOfDay: Int) = updateReports { prefs ->
        prefs.weeklyReportMinuteOfDay = minuteOfDay
    }

    private fun updateReports(block: (com.jeremiah.phoneusage.data.Prefs) -> Unit) {
        val prefs = repository.prefs
        block(prefs)
        ReportScheduler.sync(getApplication())
        _state.update {
            it.copy(
                reports = ReportSettings(
                    dailyEnabled = prefs.dailyReportEnabled,
                    dailyMinuteOfDay = prefs.dailyReportMinuteOfDay,
                    weeklyEnabled = prefs.weeklyReportEnabled,
                    weeklyDay = prefs.weeklyReportDay,
                    weeklyMinuteOfDay = prefs.weeklyReportMinuteOfDay,
                ),
            )
        }
    }

    fun previewReport(weekly: Boolean) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val report = withContext(Dispatchers.IO) {
                if (weekly) {
                    ReportBuilder.weekly(context, repository)
                } else {
                    ReportBuilder.daily(context, repository)
                }
            }
            _state.update { it.copy(preview = report) }
        }
    }

    fun dismissPreview() = _state.update { it.copy(preview = null) }

    fun deleteMonth(month: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteMonth(month) }
            _state.update { it.copy(message = "Deleted data for $month") }
            refresh(collectFirst = false)
        }
    }

    fun deleteOlderThan(month: String) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { repository.deleteBefore(month) }
            _state.update {
                it.copy(
                    message = if (deleted > 0) {
                        "Deleted $deleted month(s) before $month"
                    } else {
                        "Nothing older than $month to delete"
                    },
                )
            }
            refresh(collectFirst = false)
        }
    }

    fun deleteEverything() {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { repository.deleteEverything() }
            _state.update { it.copy(message = "Deleted all stored data ($deleted months)") }
            refresh(collectFirst = false)
        }
    }

    fun export(uri: Uri, allMonths: Boolean) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val months = if (allMonths) repository.months() else listOf(_state.value.selectedMonth)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        repository.export(stream, months)
                    } ?: error("Could not open the selected file")
                }
            }
            _state.update {
                it.copy(
                    message = result.fold(
                        onSuccess = { _ -> "Exported ${months.size} month(s)" },
                        onFailure = { error -> "Export failed: ${error.message}" },
                    ),
                )
            }
        }
    }

    fun onPermissionsChanged() = refresh(collectFirst = true)

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Called once the first time the app runs so collection and reports are scheduled. */
    fun ensureScheduled() {
        val context = getApplication<Application>()
        if (repository.prefs.trackingEnabled) CollectWorker.enable(context)
        ReportScheduler.sync(context)
    }
}
