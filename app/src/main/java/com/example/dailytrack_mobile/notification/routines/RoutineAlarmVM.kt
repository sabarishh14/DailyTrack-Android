package com.example.dailytrack_mobile.notification.routines

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.repository.RoutinesRepository
import com.example.dailytrack_mobile.data.repository.engine
import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import com.example.dailytrack_mobile.domain.routines.DayItem
import com.example.dailytrack_mobile.domain.routines.DayStats
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The check-in alarm's list: tonight's routines, answered right there on the alarm screen. */
@HiltViewModel
class RoutineAlarmVM @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: RoutinesRepository,
    savedState: SavedStateHandle
) : ViewModel() {

    val date: LocalDate = savedState.get<String>(RoutineCheckInReceiver.EXTRA_DATE)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate.now()

    data class State(
        val loaded: Boolean = false,
        /** What tonight is about: everything due, and weekly goals still short. */
        val items: List<DayItem> = emptyList(),
        val stats: DayStats? = null
    ) {
        val open: Int get() = items.count { it.needsAnswer }
    }

    val state: StateFlow<State> = repository.snapshot
        .filterNotNull()
        .map { snapshot ->
            val engine = snapshot.engine()
            State(
                loaded = true,
                items = engine.dayItems(date).filter { it.required || it.status != null || it.progress?.met == false },
                stats = engine.dayStats(date)
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, State())

    init {
        viewModelScope.launch { repository.load() }
    }

    fun answer(routineId: Long, status: CheckInStatus?) {
        viewModelScope.launch {
            repository.setCheckIn(routineId, date, status)
            // Keeps a check-in question or reminder in the shade in step with this.
            val engine = repository.load().engine()
            RoutineCheckInNotifier.syncIfShowing(context, engine, date)
            if (status != null) RoutineReminderNotifier.cancel(context, routineId)
            repository.flush()
        }
    }
}
