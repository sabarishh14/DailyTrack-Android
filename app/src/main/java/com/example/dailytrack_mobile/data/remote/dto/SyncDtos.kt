package com.example.dailytrack_mobile.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Count of transactions still waiting to be pushed to Google Sheets. */
@JsonClass(generateAdapter = true)
data class PendingSheetSyncDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "count") val count: Int = 0,
    @Json(name = "message") val message: String? = null
)

/**
 * One batch of the Sheets push. The server deliberately works in small batches,
 * so [hasMore] tells the client to call again until the queue is drained.
 */
@JsonClass(generateAdapter = true)
data class SheetsSyncResponseDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null,
    @Json(name = "synced_count") val syncedCount: Int = 0,
    @Json(name = "has_more") val hasMore: Boolean = false
)

@JsonClass(generateAdapter = true)
data class LetterboxdSyncRequestDto(
    @Json(name = "username") val username: String
)

/**
 * One newline-delimited JSON line from the Letterboxd RSS sync stream. Progress
 * lines carry [status]; the final line carries [success] and a summary.
 */
@JsonClass(generateAdapter = true)
data class LetterboxdSyncEventDto(
    @Json(name = "status") val status: String? = null,
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "added_movies") val addedMovies: Int? = null,
    @Json(name = "added_logs") val addedLogs: Int? = null
)
