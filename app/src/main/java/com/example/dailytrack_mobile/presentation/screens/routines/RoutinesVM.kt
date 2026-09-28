package com.example.dailytrack_mobile.presentation.screens.routines

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.local.auth.AuthManager
import com.example.dailytrack_mobile.data.local.datastore.DemoModeManager
import com.example.dailytrack_mobile.data.local.routines.RoutineCheckInSettings
import com.example.dailytrack_mobile.data.local.routines.RoutineReminders
import com.example.dailytrack_mobile.data.local.routines.RoutinesSnapshot
import com.example.dailytrack_mobile.data.repository.RoutinesRepository
import com.example.dailytrack_mobile.data.repository.engine
import com.example.dailytrack_mobile.data.repository.toRoutine
import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import com.example.dailytrack_mobile.domain.routines.DayItem
import com.example.dailytrack_mobile.domain.routines.DayStats
import com.example.dailytrack_mobile.domain.routines.Routine
import com.example.dailytrack_mobile.domain.routines.RoutineEngine
import com.example.dailytrack_mobile.domain.routines.RoutineSchedule
import com.example.dailytrack_mobile.domain.routines.Score
import com.example.dailytrack_mobile.domain.routines.Streak
import com.example.dailytrack_mobile.domain.routines.UpcomingItem
import com.example.dailytrack_mobile.data.local.routines.RoutineAlarmSettings
import com.example.dailytrack_mobile.notification.routines.RoutineAlarmService
import com.example.dailytrack_mobile.notification.routines.RoutineCheckInNotifier
import com.example.dailytrack_mobile.notification.routines.RoutineCheckInReceiver
import com.example.dailytrack_mobile.notification.routines.RoutineReminderNotifier
import com.example.dailytrack_mobile.notification.routines.RoutineCheckInScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class RoutinesVM @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: RoutinesRepository,
    private val checkInSettings: RoutineCheckInSettings,
    private val scheduler: RoutineCheckInScheduler,
    private val reminders: RoutineReminders,
    private val alarmSettings: RoutineAlarmSettings,
    demoModeManager: DemoModeManager,
    authManager: AuthManager
) : ViewModel() {

    private val _state = MutableStateFlow(RoutinesState())
    val state: StateFlow<RoutinesState> = _state.asStateFlow()

    private var engine: RoutineEngine? = null
    private var lastRefreshAt = 0L

    // Which routine's page is open, the month on its calendar and the day picked.
    private var detailId: Long? = null
    private var detailMonth: YearMonth = YearMonth.now()
    private var detailDay: LocalDate? = null

    init {
        // Every change to the phone's copy (a tick here, an answer from the
        // notification, a refresh) rebuilds the page.
        viewModelScope.launch {
            repository.snapshot.filterNotNull().collectLatest { rebuild(it) }
        }
        // Whose routines these are: reload whenever demo mode or the signed-in person changes.
        viewModelScope.launch {
            combine(demoModeManager.isDemoModeEnabledFlow, authManager.userEmailFlow) { demo, email -> demo to email }
                .distinctUntilChanged()
                .collect { refresh(showSpinner = false) }
        }
        viewModelScope.launch {
            checkInSettings.settings.collect { settings -> _state.update { it.copy(checkIn = settings) } }
        }
        viewModelScope.launch {
            reminders.reminders.collect { times -> _state.update { it.copy(reminders = times) } }
        }
        viewModelScope.launch {
            // Keeps the nightly alarm and each reminder set, e.g. after the app was updated or reinstalled.
            scheduler.schedule(checkInSettings.current())
            val alarms = reminders.alarmIds()
            reminders.all().forEach { (id, time) -> scheduler.scheduleReminder(id, time, alarm = id in alarms) }
        }
    }

    fun onAction(action: RoutinesAction) {
        when (action) {
            RoutinesAction.Refresh -> refresh(showSpinner = true)
            RoutinesAction.Resume -> {
                if (_state.value.today != LocalDate.now()) repository.snapshot.value?.let { rebuildLater(it) }
                if (System.currentTimeMillis() - lastRefreshAt > REFRESH_AFTER_MS) refresh(showSpinner = false)
            }
            is RoutinesAction.SetStatus -> viewModelScope.launch {
                _state.update { it.copy(skipping = null) }
                repository.setCheckIn(action.routineId, action.date, action.status, action.note)
                // Answered here: its reminder (or ringing alarm) for today has done its job.
                if (action.status != null && action.date == LocalDate.now()) {
                    RoutineAlarmService.stopIfFor(context, action.routineId)
                    RoutineReminderNotifier.cancel(context, action.routineId)
                }
                repository.flush()
            }
            is RoutinesAction.AskSkipReason -> _state.update { it.copy(skipping = action.item) }
            RoutinesAction.DismissSkip -> _state.update { it.copy(skipping = null) }
            is RoutinesAction.OpenDay -> {
                val engine = engine ?: return
                _state.update {
                    it.copy(
                        openDay = action.date,
                        openDayItems = engine.dayItems(action.date),
                        openDayStats = engine.dayStats(action.date)
                    )
                }
            }
            RoutinesAction.CloseDay -> _state.update { it.copy(openDay = null, openDayItems = emptyList(), openDayStats = null) }
            RoutinesAction.ToggleHistory -> _state.update { it.copy(historyOpen = !it.historyOpen) }
            is RoutinesAction.OpenRoutine -> {
                detailId = action.id
                detailMonth = YearMonth.now()
                detailDay = LocalDate.now()
                updateDetail()
            }
            RoutinesAction.CloseRoutine -> {
                detailId = null
                _state.update { it.copy(detail = null) }
            }
            is RoutinesAction.ShowRoutineMonth -> {
                detailMonth = action.month
                updateDetail()
            }
            is RoutinesAction.SelectRoutineDay -> {
                detailDay = action.date
                updateDetail()
            }
            is RoutinesAction.ShowMonth -> {
                _state.update { it.copy(historyMonth = action.month) }
                val engine = engine ?: return
                viewModelScope.launch {
                    val history = withContext(Dispatchers.Default) { historyFor(engine, action.month, LocalDate.now()) }
                    _state.update { if (it.historyMonth == action.month) it.copy(history = history) else it }
                }
            }
            RoutinesAction.OpenCheckInSettings -> _state.update { it.copy(showCheckInSettings = true) }
            RoutinesAction.CloseCheckInSettings -> _state.update { it.copy(showCheckInSettings = false) }
            is RoutinesAction.SetCheckInEnabled -> updateCheckIn { it.copy(enabled = action.enabled) }
            is RoutinesAction.SetCheckInTime -> updateCheckIn { it.copy(time = action.time) }
            is RoutinesAction.SetCheckInAlarm -> updateCheckIn { it.copy(alarm = action.alarm) }
            is RoutinesAction.ToggleCheckInDay -> updateCheckIn { settings ->
                val days = if (action.day in settings.days) settings.days - action.day else settings.days + action.day
                settings.copy(days = days)
            }
            RoutinesAction.TryCheckIn -> {
                val engine = engine ?: return
                val today = LocalDate.now()
                val open = engine.dayItems(today).count { it.needsAnswer }
                if (_state.value.checkIn.alarm && open > 0) {
                    viewModelScope.launch {
                        if (!RoutineCheckInReceiver.ringCheckIn(context, alarmSettings.current(), today, open)) {
                            RoutineCheckInNotifier.ask(context, engine, today)
                        }
                    }
                } else if (!RoutineCheckInNotifier.ask(context, engine, today)) {
                    RoutineCheckInNotifier.showWrapUp(context, engine, today)
                    _state.update { it.copy(message = "Everything's answered for today, so here's how it ends") }
                }
            }
            RoutinesAction.ConsumeMessage -> _state.update { it.copy(message = null) }
        }
    }

    private fun updateDetail() {
        val engine = engine ?: return
        viewModelScope.launch {
            val detail = withContext(Dispatchers.Default) { buildDetail(engine, LocalDate.now()) }
            _state.update { it.copy(detail = detail) }
        }
    }

    /** The open routine's page, or null once it's gone (deleted or archived). */
    private fun buildDetail(engine: RoutineEngine, today: LocalDate): RoutineDetail? {
        val id = detailId ?: return null
        val routine = engine.routines.firstOrNull { it.id == id } ?: return null
        val month = detailMonth
        val answers = engine.answersFor(routine)
        return RoutineDetail(
            routine = routine,
            streak = engine.streak(routine, today),
            last30 = engine.routineConsistency(routine, today),
            allTime = engine.allTime(routine, today),
            doneCount = answers.count { it.status == CheckInStatus.DONE },
            month = month,
            days = (1..month.lengthOfMonth()).map { dayOfMonth ->
                val date = month.atDay(dayOfMonth)
                if (date > today) null else engine.itemOn(routine, date)
            },
            selected = detailDay?.takeIf { it <= today }?.let { engine.itemOn(routine, it) },
            skips = answers.filter { it.status == CheckInStatus.SKIPPED }.take(30),
            nextDue = engine.nextDue(routine),
            lastDone = engine.lastDone(routine),
            progress = engine.itemOn(routine, today)?.progress
        )
    }

    private fun updateCheckIn(transform: (RoutineCheckInSettings.Settings) -> RoutineCheckInSettings.Settings) {
        viewModelScope.launch {
            val settings = checkInSettings.update(transform)
            scheduler.schedule(settings)
        }
    }

    private fun refresh(showSpinner: Boolean) {
        viewModelScope.launch {
            if (showSpinner) _state.update { it.copy(isRefreshing = true) }
            repository.load()
            val result = repository.refresh()
            lastRefreshAt = System.currentTimeMillis()
            _state.update {
                it.copy(isLoading = false, isRefreshing = false, error = result.exceptionOrNull()?.message)
            }
        }
    }

    private fun rebuildLater(snapshot: RoutinesSnapshot) {
        viewModelScope.launch { rebuild(snapshot) }
    }

    private suspend fun rebuild(snapshot: RoutinesSnapshot) {
        val today = LocalDate.now()
        val openDay = _state.value.openDay
        val historyMonth = _state.value.historyMonth
        val page = withContext(Dispatchers.Default) { buildPage(snapshot, today, openDay, historyMonth) }
        val detail = withContext(Dispatchers.Default) { buildDetail(page.engine, today) }
        if (detail == null) detailId = null
        engine = page.engine
        _state.update { current ->
            current.copy(
                // Something saved on the phone is enough to show; otherwise wait for the first fetch.
                isLoading = current.isLoading && snapshot.syncedAt == null && snapshot.routines.isEmpty(),
                today = today,
                todayItems = page.todayItems,
                todayStats = page.todayStats,
                streaks = page.streaks,
                consistency = page.consistency,
                previousConsistency = page.previousConsistency,
                perfectDays = page.perfectDays,
                week = page.week,
                history = if (current.historyMonth == historyMonth) page.history else current.history,
                historyFirstDay = page.historyFirstDay,
                upcoming = page.upcoming,
                routines = page.summaries,
                archived = page.archived,
                yesterdayOpen = page.yesterdayOpen,
                pendingSync = snapshot.pending.size,
                openDayItems = if (current.openDay == openDay && page.openDayItems != null) page.openDayItems else current.openDayItems,
                openDayStats = if (current.openDay == openDay && page.openDayStats != null) page.openDayStats else current.openDayStats,
                detail = detail,
                skipping = current.skipping?.let { skipping ->
                    page.todayItems.firstOrNull { it.routine.id == skipping.routine.id && it.date == skipping.date }
                        ?: skipping
                }
            )
        }
        // A check-in notification on screen follows answers given here.
        RoutineCheckInNotifier.syncIfShowing(context, page.engine, today)
    }

    private class Page(
        val engine: RoutineEngine,
        val todayItems: List<DayItem>,
        val todayStats: DayStats,
        val streaks: Map<Long, Streak>,
        val consistency: Score,
        val previousConsistency: Score,
        val perfectDays: Streak,
        val week: List<DayStats?>,
        val history: List<DayStats?>,
        val historyFirstDay: LocalDate?,
        val upcoming: List<UpcomingItem>,
        val summaries: List<RoutineSummary>,
        val archived: List<Routine>,
        val yesterdayOpen: Int,
        val openDayItems: List<DayItem>?,
        val openDayStats: DayStats?
    )

    private fun buildPage(snapshot: RoutinesSnapshot, today: LocalDate, openDay: LocalDate?, historyMonth: YearMonth): Page {
        val engine = snapshot.engine()
        val streaks = engine.routines.associate { it.id to engine.streak(it, today) }
        val upcoming = engine.upcoming(today)
        val nextDue = upcoming.associate { it.routine.id to it.date }
        val summaries = engine.routines.map { routine ->
            RoutineSummary(
                routine = routine,
                streak = streaks.getValue(routine.id),
                consistency = engine.routineConsistency(routine, today),
                nextDue = nextDue[routine.id]?.takeIf { routine.schedule == RoutineSchedule.INTERVAL }
            )
        }
        return Page(
            engine = engine,
            todayItems = engine.dayItems(today),
            todayStats = engine.dayStats(today),
            streaks = streaks,
            consistency = engine.consistency(today),
            previousConsistency = engine.previousConsistency(today),
            perfectDays = engine.perfectDays(today),
            week = engine.week(today),
            history = historyFor(engine, historyMonth, today),
            historyFirstDay = engine.routines.minOfOrNull { it.startDate },
            upcoming = upcoming,
            summaries = summaries,
            archived = snapshot.routines.filter { it.archived }.mapNotNull { it.toRoutine() },
            // Only blanks that would count as missed; days before a routine was added don't.
            yesterdayOpen = engine.dayItems(today.minusDays(1))
                .count { it.required && it.status == null && it.routine.countsIfUnanswered(it.date) },
            openDayItems = openDay?.let { engine.dayItems(it) },
            openDayStats = openDay?.let { engine.dayStats(it) }
        )
    }

    /** Every day of [month] that can be opened, with how it went. */
    private fun historyFor(engine: RoutineEngine, month: YearMonth, today: LocalDate): List<DayStats?> {
        val first = engine.routines.minOfOrNull { it.startDate }
        return (1..month.lengthOfMonth()).map { dayOfMonth ->
            val day = month.atDay(dayOfMonth)
            if (first == null || day > today || day < first) null else engine.dayStats(day)
        }
    }

    private companion object {
        const val REFRESH_AFTER_MS = 60_000L
    }
}
