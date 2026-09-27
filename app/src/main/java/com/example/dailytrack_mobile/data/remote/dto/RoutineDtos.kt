package com.example.dailytrack_mobile.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ─────────────────────────────────────────────────────────────────────────────
// Routines (/api/routines). The server only stores these; RoutineEngine works
// out everything shown from them.
// ─────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class RoutineDto(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "emoji") val emoji: String? = null,
    @Json(name = "kind") val kind: String = "build",
    @Json(name = "schedule") val schedule: String = "daily",
    @Json(name = "days") val days: Int? = null,
    @Json(name = "target") val target: Int? = null,
    @Json(name = "every") val every: Int? = null,
    @Json(name = "unit") val unit: String? = null,
    @Json(name = "start_date") val startDate: String,
    @Json(name = "end_date") val endDate: String? = null,
    @Json(name = "sort_order") val sortOrder: Int = 0,
    @Json(name = "archived") val archived: Boolean = false,
    /** The day it was added (IST); blank days before it aren't held against it. */
    @Json(name = "created_at") val createdAt: String? = null
)

/** One day's answer. In a save request a null status clears that day. */
@JsonClass(generateAdapter = true)
data class RoutineCheckInDto(
    @Json(name = "routine_id") val routineId: Long,
    @Json(name = "date") val date: String,
    @Json(name = "status") val status: String? = null,
    @Json(name = "note") val note: String? = null
)

@JsonClass(generateAdapter = true)
data class RoutinesResponseDto(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null,
    @Json(name = "routines") val routines: List<RoutineDto> = emptyList(),
    @Json(name = "checkins") val checkins: List<RoutineCheckInDto> = emptyList()
)

/**
 * Creates or fully rewrites a routine. Nulls are left out of the JSON, so the
 * fields that can be cleared use "" instead: no emoji, or no end (not a challenge).
 */
@JsonClass(generateAdapter = true)
data class RoutineRequestDto(
    @Json(name = "name") val name: String,
    @Json(name = "emoji") val emoji: String,
    @Json(name = "kind") val kind: String,
    @Json(name = "schedule") val schedule: String,
    @Json(name = "days") val days: Int? = null,
    @Json(name = "target") val target: Int? = null,
    @Json(name = "every") val every: Int? = null,
    @Json(name = "unit") val unit: String? = null,
    @Json(name = "start_date") val startDate: String,
    @Json(name = "end_date") val endDate: String
)

@JsonClass(generateAdapter = true)
data class RoutineArchiveRequestDto(
    @Json(name = "archived") val archived: Boolean
)

@JsonClass(generateAdapter = true)
data class RoutineResponseDto(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null,
    @Json(name = "routine") val routine: RoutineDto? = null
)

@JsonClass(generateAdapter = true)
data class SaveRoutineCheckInsRequestDto(
    @Json(name = "checkins") val checkins: List<RoutineCheckInDto>
)

/** Entries the server couldn't take (a deleted routine, say) are dropped, never retried. */
@JsonClass(generateAdapter = true)
data class SaveRoutineCheckInsResponseDto(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null,
    @Json(name = "saved") val saved: Int = 0
)
