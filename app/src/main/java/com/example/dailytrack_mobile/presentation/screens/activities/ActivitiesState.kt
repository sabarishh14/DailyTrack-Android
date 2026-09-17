package com.example.dailytrack_mobile.presentation.screens.activities

import java.time.Month
import java.time.Year

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────

enum class ActivityType(val label: String) {
    GYM("Gym"),
    BADMINTON("Badminton"),
    CRICKET("Cricket"),
    TABLE_TENNIS("TT"),
    OTHERS("Others")
}

data class ActivityEntry(
    val dayOfMonth: Int,
    val dayOfWeek: String,   // e.g. "FRI"
    val activities: List<ActivityType>,  // empty list means rest day
    val month: Int = 0,
    val year: Int = 0
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen state
// ─────────────────────────────────────────────────────────────────────────────

data class ActivitiesState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedMonth: Month = java.time.LocalDate.now().month,
    val selectedYear: Int = java.time.LocalDate.now().year,
    val allActivities: List<ActivityEntry> = emptyList(),
    val activityLog: List<ActivityEntry> = emptyList()
)

// ─────────────────────────────────────────────────────────────────────────────
// Sample / hardcoded data for July 2026
// ─────────────────────────────────────────────────────────────────────────────

private fun sampleActivities(): List<ActivityEntry> = listOf(
    ActivityEntry(26, "SUN", listOf(ActivityType.CRICKET)),
    ActivityEntry(25, "SAT", listOf(ActivityType.GYM, ActivityType.CRICKET)),
    ActivityEntry(24, "FRI", listOf(ActivityType.BADMINTON)),
    ActivityEntry(23, "THU", listOf()),
    ActivityEntry(22, "WED", listOf(ActivityType.GYM)),
    ActivityEntry(21, "TUE", listOf(ActivityType.TABLE_TENNIS, ActivityType.CRICKET)),
    ActivityEntry(20, "MON", listOf()),
    ActivityEntry(19, "SUN", listOf(ActivityType.GYM, ActivityType.BADMINTON)),
    ActivityEntry(18, "SAT", listOf(ActivityType.GYM)),
    ActivityEntry(17, "FRI", listOf(ActivityType.TABLE_TENNIS)),
    ActivityEntry(16, "THU", listOf()),
    ActivityEntry(15, "WED", listOf(ActivityType.GYM, ActivityType.CRICKET)),
    ActivityEntry(14, "TUE", listOf(ActivityType.BADMINTON)),
    ActivityEntry(13, "MON", listOf(ActivityType.GYM)),
)
