package com.example.dailytrack_mobile.presentation.screens.routines

import com.example.dailytrack_mobile.data.local.routines.RoutineCheckInSettings
import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import com.example.dailytrack_mobile.domain.routines.DayItem
import com.example.dailytrack_mobile.domain.routines.DayStats
import com.example.dailytrack_mobile.domain.routines.Routine
import com.example.dailytrack_mobile.domain.routines.Score
import com.example.dailytrack_mobile.domain.routines.Streak
import com.example.dailytrack_mobile.domain.routines.UpcomingItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** One routine in "Your routines". */
data class RoutineSummary(
    val routine: Routine,
    val streak: Streak,
    val consistency: Score,
    /** Chores: when the next one falls due, if it isn't due already. */
    val nextDue: LocalDate?
)

data class RoutinesState(
    /** Only true on the very first load, before anything was ever fetched. */
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val today: LocalDate = LocalDate.now(),
    val todayItems: List<DayItem> = emptyList(),
    val todayStats: DayStats? = null,
    val streaks: Map<Long, Streak> = emptyMap(),
    val consistency: Score? = null,
    val perfectDays: Streak? = null,
    val week: List<DayStats?> = emptyList(),
    /** History opens under the week's bars only when asked for. */
    val historyOpen: Boolean = false,
    val historyMonth: YearMonth = YearMonth.now(),
    /** Each day of [historyMonth]; null where there's nothing to open (ahead of today, or before any routine). */
    val history: List<DayStats?> = emptyList(),
    /** The first day any routine counts from: how far back History goes. */
    val historyFirstDay: LocalDate? = null,
    val upcoming: List<UpcomingItem> = emptyList(),
    val routines: List<RoutineSummary> = emptyList(),
    val archived: List<Routine> = emptyList(),
    val yesterdayOpen: Int = 0,
    val pendingSync: Int = 0,
    val checkIn: RoutineCheckInSettings.Settings = RoutineCheckInSettings.Settings(),
    val openDay: LocalDate? = null,
    val openDayItems: List<DayItem> = emptyList(),
    val openDayStats: DayStats? = null,
    val skipping: DayItem? = null,
    val showCheckInSettings: Boolean = false,
    val message: String? = null
)

sealed interface RoutinesAction {
    data object Refresh : RoutinesAction
    data object Resume : RoutinesAction
    data class SetStatus(
        val routineId: Long,
        val date: LocalDate,
        val status: CheckInStatus?,
        val note: String? = null
    ) : RoutinesAction
    data class AskSkipReason(val item: DayItem) : RoutinesAction
    data object DismissSkip : RoutinesAction
    data class OpenDay(val date: LocalDate) : RoutinesAction
    data class ShowMonth(val month: YearMonth) : RoutinesAction
    data object ToggleHistory : RoutinesAction
    data object CloseDay : RoutinesAction
    data object OpenCheckInSettings : RoutinesAction
    data object CloseCheckInSettings : RoutinesAction
    data class SetCheckInEnabled(val enabled: Boolean) : RoutinesAction
    data class SetCheckInTime(val time: LocalTime) : RoutinesAction
    data class ToggleCheckInDay(val day: DayOfWeek) : RoutinesAction
    data object TryCheckIn : RoutinesAction
    data object ConsumeMessage : RoutinesAction
}
