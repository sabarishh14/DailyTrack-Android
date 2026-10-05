package com.example.dailytrack_mobile.presentation.screens.routines

import com.example.dailytrack_mobile.domain.routines.IntervalUnit
import com.example.dailytrack_mobile.domain.routines.RoutineKind
import com.example.dailytrack_mobile.domain.routines.RoutineSchedule

/** A starting point for a new routine; everything stays editable. */
data class RoutineTemplate(
    val emoji: String,
    val name: String,
    val kind: RoutineKind = RoutineKind.BUILD,
    val schedule: RoutineSchedule = RoutineSchedule.DAILY,
    val target: Int? = null,
    val every: Int? = null,
    val unit: IntervalUnit? = null,
    val challengeDays: Int? = null
)

internal val RoutineTemplates = listOf(
    RoutineTemplate("🪥", "Brush at night"),
    RoutineTemplate("📖", "Read 20 minutes"),
    RoutineTemplate("🏋️", "Gym", schedule = RoutineSchedule.WEEKLY, target = 4),
    RoutineTemplate("🍬", "No sugar", kind = RoutineKind.AVOID, challengeDays = 30),
    RoutineTemplate("💧", "Drink 3L water"),
    RoutineTemplate("🧘", "Meditate 10 minutes"),
    RoutineTemplate("🚶", "Walk 8,000 steps"),
    RoutineTemplate("📵", "No phone in bed", kind = RoutineKind.AVOID)
)

/** What the routine editor opens for. */
sealed interface RoutineEditorTarget {
    data class New(val template: RoutineTemplate? = null) : RoutineEditorTarget
    data class Edit(val id: Long) : RoutineEditorTarget
}
