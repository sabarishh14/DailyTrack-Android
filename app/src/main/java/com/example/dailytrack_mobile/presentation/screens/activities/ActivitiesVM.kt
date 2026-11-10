package com.example.dailytrack_mobile.presentation.screens.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.local.datastore.DemoModeManager
import com.example.dailytrack_mobile.data.repository.ActivitiesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ActivitiesVM @Inject constructor(
    private val repository: ActivitiesRepository,
    private val demoModeManager: DemoModeManager
) : ViewModel() {

    private val _state = MutableStateFlow(ActivitiesState())
    val state = _state.asStateFlow()

    /** Showing this month rather than one picked by hand, so it follows the calendar. */
    private var followsCurrentMonth = true

    init {
        val now = LocalDate.now()
        _state.update { it.copy(selectedMonth = now.month, selectedYear = now.year, allActivities = emptyList(), activityLog = emptyList()) }
        viewModelScope.launch {
            demoModeManager.isDemoModeEnabledFlow.collect {
                loadActivities()
            }
        }
        viewModelScope.launch {
            repository.dataUpdateFlow.collect {
                loadActivities()
            }
        }
    }

    fun onAction(action: ActivitiesAction) {
        when (action) {
            is ActivitiesAction.OnMonthChanged -> {
                val now = LocalDate.now()
                followsCurrentMonth = action.month == now.month && action.year == now.year
                _state.update { it.copy(selectedMonth = action.month, selectedYear = action.year) }
                filterActivities()
            }
            ActivitiesAction.Resumed -> {
                // Back on a new month: "this month" moves on with it, a picked month stays.
                val now = LocalDate.now()
                val s = _state.value
                if (followsCurrentMonth && (s.selectedMonth != now.month || s.selectedYear != now.year)) {
                    _state.update { it.copy(selectedMonth = now.month, selectedYear = now.year) }
                    filterActivities()
                }
            }
            is ActivitiesAction.Refresh -> {
                loadActivities(forceRefresh = true)
            }
        }
    }

    private fun loadActivities(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) {
                repository.clearCache()
                _state.update { it.copy(isRefreshing = true) }
            } else {
                _state.update { it.copy(isLoading = true) }
            }
            repository.getPhysicalActivities(forceRefresh = forceRefresh).onSuccess { dtoList ->
                val entries = dtoList.map { dto ->
                    val date = LocalDate.parse(dto.date)
                    val acts = mutableListOf<ActivityType>()
                    if (dto.gym) acts.add(ActivityType.GYM)
                    if (dto.badminton) acts.add(ActivityType.BADMINTON)
                    if (dto.tableTennis) acts.add(ActivityType.TABLE_TENNIS)
                    if (dto.cricket) acts.add(ActivityType.CRICKET)
                    if (dto.others) acts.add(ActivityType.OTHERS)

                    ActivityEntry(
                        dayOfMonth = date.dayOfMonth,
                        dayOfWeek = date.dayOfWeek.name.take(3),
                        activities = acts,
                        month = date.monthValue,
                        year = date.year
                    )
                }
                
                _state.update { it.copy(allActivities = entries, isLoading = false, isRefreshing = false) }
                filterActivities()
            }.onFailure {
                _state.update { it.copy(isLoading = false, isRefreshing = false, allActivities = emptyList(), activityLog = emptyList()) }
            }
        }
    }
    
    private fun filterActivities() {
        val currentState = _state.value
        val filtered = currentState.allActivities.filter { 
            it.month == currentState.selectedMonth.value && it.year == currentState.selectedYear 
        }.sortedByDescending { it.dayOfMonth }
        
        _state.update { it.copy(activityLog = filtered) }
    }
}
