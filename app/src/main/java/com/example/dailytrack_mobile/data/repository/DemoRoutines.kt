package com.example.dailytrack_mobile.data.repository

import com.example.dailytrack_mobile.data.local.routines.RoutinesSnapshot
import com.example.dailytrack_mobile.data.remote.dto.RoutineCheckInDto
import com.example.dailytrack_mobile.data.remote.dto.RoutineDto
import java.time.LocalDate
import kotlin.random.Random

/** Demo mode's routines: a believable three weeks of history that ends today. */
internal object DemoRoutines {

    fun seed(today: LocalDate): RoutinesSnapshot {
        val start = today.minusDays(21)
        val challengeStart = today.minusDays(11)
        val routines = listOf(
            RoutineDto(id = 1, name = "Brush at night", emoji = "🪥", startDate = "$start", sortOrder = 0),
            RoutineDto(id = 2, name = "Read 20 minutes", emoji = "📖", startDate = "$start", sortOrder = 1),
            RoutineDto(id = 3, name = "Gym", emoji = "🏋️", schedule = "weekly", target = 4, startDate = "$start", sortOrder = 2),
            RoutineDto(
                id = 4, name = "No sugar", emoji = "🍬", kind = "avoid",
                startDate = "$challengeStart", endDate = "${challengeStart.plusDays(29)}", sortOrder = 3
            ),
            RoutineDto(id = 5, name = "Drink 3L water", emoji = "💧", schedule = "days", days = 31, startDate = "$start", sortOrder = 4),
            RoutineDto(
                id = 6, name = "Change bedsheets", emoji = "🛏️", schedule = "interval",
                every = 1, unit = "week", startDate = "$today", sortOrder = 5
            ),
            RoutineDto(
                id = 7, name = "Clean AC filter", emoji = "🌬️", schedule = "interval",
                every = 3, unit = "month", startDate = "${today.plusDays(9)}", sortOrder = 6
            )
        )

        val random = Random(7)
        val checkins = mutableListOf<RoutineCheckInDto>()
        var day = start
        while (day < today) {
            fun answer(id: Long, doneRate: Double) {
                val roll = random.nextDouble()
                val status = when {
                    roll < doneRate -> "done"
                    roll < doneRate + 0.05 -> "skipped"
                    else -> "missed"
                }
                checkins += RoutineCheckInDto(id, "$day", status)
            }
            answer(1, 0.9)
            answer(2, 0.72)
            if (random.nextDouble() < 0.6) checkins += RoutineCheckInDto(3, "$day", "done")
            if (day >= challengeStart) answer(4, 0.85)
            if (day.dayOfWeek.value <= 5) answer(5, 0.75)
            day = day.plusDays(1)
        }
        checkins += RoutineCheckInDto(2, "$today", "done")
        return RoutinesSnapshot(owner = "demo", routines = routines, checkins = checkins)
    }
}
