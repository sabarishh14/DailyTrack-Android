package com.example.dailytrack_mobile.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BudgetDto(
    @Json(name = "category") val category: String,
    @Json(name = "monthly_limit") val monthlyLimit: Double
)

@JsonClass(generateAdapter = true)
data class BudgetsResponseDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "budgets") val budgets: List<BudgetDto> = emptyList(),
    @Json(name = "message") val message: String? = null
)

/**
 * Server-side suggestion for one category: an exponentially-weighted average of
 * completed months of spend, plus how many months went into it so the UI can say
 * how confident the number is.
 */
@JsonClass(generateAdapter = true)
data class BudgetSuggestionDto(
    @Json(name = "suggested") val suggested: Double,
    @Json(name = "months_of_history") val monthsOfHistory: Int
)

@JsonClass(generateAdapter = true)
data class BudgetSuggestionsResponseDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "suggestions") val suggestions: Map<String, BudgetSuggestionDto> = emptyMap(),
    @Json(name = "message") val message: String? = null
)
