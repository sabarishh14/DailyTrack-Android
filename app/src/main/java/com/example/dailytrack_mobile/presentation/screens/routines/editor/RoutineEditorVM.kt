package com.example.dailytrack_mobile.presentation.screens.routines.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.local.routines.RoutineReminders
import com.example.dailytrack_mobile.data.remote.dto.RoutineRequestDto
import com.example.dailytrack_mobile.data.repository.RoutineAnswer
import com.example.dailytrack_mobile.data.repository.RoutinesRepository
import com.example.dailytrack_mobile.data.repository.toCheckIn
import com.example.dailytrack_mobile.data.repository.toRoutine
import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import com.example.dailytrack_mobile.domain.routines.IntervalUnit
import com.example.dailytrack_mobile.domain.routines.Routine
import com.example.dailytrack_mobile.domain.routines.RoutineEngine
import com.example.dailytrack_mobile.domain.routines.RoutineKind
import com.example.dailytrack_mobile.domain.routines.RoutineSchedule
import com.example.dailytrack_mobile.notification.routines.RoutineCheckInScheduler
import com.example.dailytrack_mobile.presentation.screens.routines.RoutineEditorTarget
import com.example.dailytrack_mobile.presentation.screens.routines.RoutineTemplate
import com.example.dailytrack_mobile.presentation.screens.routines.RoutineText
import com.example.dailytrack_mobile.presentation.screens.routines.components.DEFAULT_EMOJI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class RoutineEditorVM @Inject constructor(
    private val repository: RoutinesRepository,
    private val reminders: RoutineReminders,
    private val scheduler: RoutineCheckInScheduler
) : ViewModel() {

    /** What's being edited; every schedule's settings are kept so switching back and forth loses nothing. */
    data class Form(
        val name: String = "",
        val emoji: String = DEFAULT_EMOJI,
        val kind: RoutineKind = RoutineKind.BUILD,
        val schedule: RoutineSchedule = RoutineSchedule.DAILY,
        val days: Int = RoutineText.WEEKDAYS,
        val weeklyTarget: Int = 3,
        val monthlyTarget: Int = 4,
        val every: Int = 1,
        val unit: IntervalUnit = IntervalUnit.WEEK,
        /** First day; for a chore, when it's next due. */
        val startDate: LocalDate = LocalDate.now(),
        val isChallenge: Boolean = false,
        val challengeDays: Int = 30
    ) {
        val challengeEnd: LocalDate get() = startDate.plusDays(challengeDays - 1L)

        val canBeChallenge: Boolean get() = schedule != RoutineSchedule.INTERVAL

        /** The routine this form would save, for working out its past days before it exists. */
        fun preview(): Routine = Routine(
            id = 0,
            name = name,
            schedule = schedule,
            days = days.takeIf { schedule == RoutineSchedule.DAYS },
            startDate = startDate,
            endDate = if (isChallenge && canBeChallenge) challengeEnd else null
        )

        fun problem(): String? = when {
            name.isBlank() -> "Give it a name"
            schedule == RoutineSchedule.DAYS && days == 0 -> "Pick at least one day"
            else -> null
        }

        fun toRequest() = RoutineRequestDto(
            name = name.trim(),
            emoji = emoji.trim(),
            kind = kind.key,
            schedule = schedule.key,
            days = days.takeIf { schedule == RoutineSchedule.DAYS },
            target = when (schedule) {
                RoutineSchedule.WEEKLY -> weeklyTarget
                RoutineSchedule.MONTHLY -> monthlyTarget
                else -> null
            },
            every = every.takeIf { schedule == RoutineSchedule.INTERVAL },
            unit = unit.key.takeIf { schedule == RoutineSchedule.INTERVAL },
            startDate = startDate.toString(),
            endDate = if (isChallenge && canBeChallenge) challengeEnd.toString() else ""
        )

        companion object {
            fun from(template: RoutineTemplate?): Form {
                template ?: return Form()
                return Form(
                    name = template.name,
                    emoji = template.emoji,
                    kind = template.kind,
                    schedule = template.schedule,
                    weeklyTarget = if (template.schedule == RoutineSchedule.WEEKLY) template.target ?: 3 else 3,
                    monthlyTarget = if (template.schedule == RoutineSchedule.MONTHLY) template.target ?: 4 else 4,
                    every = template.every ?: 1,
                    unit = template.unit ?: IntervalUnit.WEEK,
                    isChallenge = template.challengeDays != null,
                    challengeDays = template.challengeDays ?: 30
                )
            }

            fun from(routine: Routine): Form = Form(
                name = routine.name,
                emoji = routine.emoji ?: DEFAULT_EMOJI,
                kind = routine.kind,
                schedule = routine.schedule,
                days = routine.days ?: RoutineText.WEEKDAYS,
                weeklyTarget = if (routine.schedule == RoutineSchedule.WEEKLY) routine.target ?: 3 else 3,
                monthlyTarget = if (routine.schedule == RoutineSchedule.MONTHLY) routine.target ?: 4 else 4,
                every = routine.every ?: 1,
                unit = routine.unit ?: IntervalUnit.WEEK,
                startDate = routine.startDate,
                isChallenge = routine.endDate != null,
                challengeDays = routine.endDate
                    ?.let { ChronoUnit.DAYS.between(routine.startDate, it).toInt() + 1 }
                    ?: 30
            )
        }
    }

    data class UiState(
        val loaded: Boolean = false,
        val editingId: Long? = null,
        val archived: Boolean = false,
        val form: Form = Form(),
        val initial: Form = Form(),
        val isSaving: Boolean = false,
        val error: String? = null,
        /** Editing: due days before today that were never answered. */
        val pastBlank: List<LocalDate> = emptyList(),
        /** Editing: how many past days were just marked done. */
        val markedPast: Int? = null,
        /** Adding with a start in the past: mark the days before today done on save. */
        val fillPastOnCreate: Boolean = false,
        /** The routine's reminder on this phone, and what it was when the editor opened. */
        val reminder: LocalTime? = null,
        val initialReminder: LocalTime? = null,
        /** Ring like an alarm rather than arrive as a notification. */
        val alarm: Boolean = false,
        val initialAlarm: Boolean = false,
        /** Set once saved, archived or deleted: the message to show on the way out. */
        val finished: String? = null
    ) {
        val reminderChanged: Boolean get() = reminder != initialReminder || (reminder != null && alarm != initialAlarm)

        val isDirty: Boolean get() = loaded && (form != initial || reminderChanged)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var session: String? = null

    /**
     * Opens the editor. This view model outlives one visit, so each visit gets a
     * new [session] and a fresh form; the same session (after a rotation) keeps it.
     */
    fun start(target: RoutineEditorTarget, session: String) {
        if (this.session == session) return
        this.session = session
        _state.value = UiState()
        viewModelScope.launch {
            _state.value = when (target) {
                is RoutineEditorTarget.New -> UiState(loaded = true, form = Form.from(target.template))
                is RoutineEditorTarget.Edit -> {
                    val snapshot = repository.load()
                    val routine = snapshot.routines.firstOrNull { it.id == target.id }?.toRoutine()
                    if (routine == null) {
                        UiState(loaded = true, error = "This routine doesn't exist any more")
                    } else {
                        val form = Form.from(routine)
                        val checkIns = snapshot.checkins.filter { it.routineId == routine.id }.mapNotNull { it.toCheckIn() }
                        val pastBlank = if (routine.archived) emptyList()
                        else RoutineEngine(listOf(routine), checkIns).unansweredPastDays(routine, LocalDate.now())
                        val reminder = reminders.all()[routine.id]
                        val alarm = routine.id in reminders.alarmIds()
                        UiState(
                            loaded = true, editingId = routine.id, archived = routine.archived,
                            form = form, initial = form, pastBlank = pastBlank,
                            reminder = reminder, initialReminder = reminder,
                            alarm = alarm, initialAlarm = alarm
                        )
                    }
                }
            }
        }
    }

    fun update(transform: (Form) -> Form) {
        _state.update { it.copy(form = transform(it.form), error = null) }
    }

    /** Days the form's routine would have had before today, for "kept it up since then". */
    fun pastDaysFor(form: Form): List<LocalDate> {
        val preview = form.preview()
        return RoutineEngine(listOf(preview), emptyList()).unansweredPastDays(preview, LocalDate.now())
    }

    fun setFillPastOnCreate(fill: Boolean) = _state.update { it.copy(fillPastOnCreate = fill) }

    /** Null turns the reminder off. Takes effect on save. */
    fun setReminder(time: LocalTime?) = _state.update { it.copy(reminder = time) }

    /** Ring like an alarm (true) or arrive as a notification. Takes effect on save. */
    fun setAlarm(alarm: Boolean) = _state.update { it.copy(alarm = alarm) }

    /** Marks every unanswered day before today done; any of them can be changed later in History. */
    fun markPastDone() {
        val current = _state.value
        val id = current.editingId ?: return
        val days = current.pastBlank.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            repository.setCheckIns(days.map { RoutineAnswer(id, it, CheckInStatus.DONE) })
            _state.update { it.copy(pastBlank = emptyList(), markedPast = days.size) }
            repository.flush()
        }
    }

    fun applyTemplate(template: RoutineTemplate) {
        update { current -> Form.from(template).copy(startDate = current.startDate) }
    }

    fun save() {
        val current = _state.value
        if (current.isSaving || !current.loaded) return
        current.form.problem()?.let { problem ->
            _state.update { it.copy(error = problem) }
            return
        }
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val request = current.form.toRequest()
            val result = current.editingId?.let { repository.update(it, request) } ?: repository.create(request)
            result.onSuccess { routine ->
                if (current.reminderChanged || current.editingId == null) {
                    applyReminder(routine.id, current.reminder, current.alarm)
                }
                val filled = if (current.editingId == null && current.fillPastOnCreate) fillPast(routine.toRoutine()) else 0
                val message = when {
                    current.editingId != null -> "Saved"
                    filled > 0 -> "${routine.emoji ?: ""} ${routine.name} added · $filled past days marked done".trim()
                    else -> "${routine.emoji ?: ""} ${routine.name} added".trim()
                }
                _state.update { it.copy(isSaving = false, finished = message) }
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, error = error.message ?: "Couldn't save") }
            }
        }
    }

    fun setArchived(archived: Boolean) {
        val id = _state.value.editingId ?: return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            repository.setArchived(id, archived)
                .onSuccess {
                    _state.update {
                        it.copy(isSaving = false, finished = if (archived) "Archived. Its history is kept." else "Restored")
                    }
                }
                .onFailure { error -> _state.update { it.copy(isSaving = false, error = error.message ?: "Couldn't update") } }
        }
    }

    fun delete() {
        val id = _state.value.editingId ?: return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            repository.delete(id)
                .onSuccess {
                    applyReminder(id, null)
                    _state.update { it.copy(isSaving = false, finished = "Deleted") }
                }
                .onFailure { error -> _state.update { it.copy(isSaving = false, error = error.message ?: "Couldn't delete") } }
        }
    }

    private suspend fun applyReminder(routineId: Long, time: LocalTime?, alarm: Boolean = false) {
        reminders.set(routineId, time, alarm)
        if (time == null) scheduler.cancelReminder(routineId) else scheduler.scheduleReminder(routineId, time, alarm)
    }

    /** For a routine just added with a past start: its days before today, marked done. */
    private suspend fun fillPast(routine: Routine?): Int {
        routine ?: return 0
        val days = RoutineEngine(listOf(routine), emptyList()).unansweredPastDays(routine, LocalDate.now())
        if (days.isEmpty()) return 0
        repository.setCheckIns(days.map { RoutineAnswer(routine.id, it, CheckInStatus.DONE) })
        viewModelScope.launch { repository.flush() }
        return days.size
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun consumeFinished() = _state.update { it.copy(finished = null) }
}
