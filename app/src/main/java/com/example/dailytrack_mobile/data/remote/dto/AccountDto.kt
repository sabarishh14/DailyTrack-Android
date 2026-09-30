package com.example.dailytrack_mobile.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AccountDto(
    @Json(name = "account") val account: String,
    // null when the user's category scope hides balances (see ACCESS_CONTROL.md)
    @Json(name = "balance") val balance: Double?,
    @Json(name = "real_balance") val realBalance: Double?,
    @Json(name = "balance_tracked") val balanceTracked: Boolean,
    // Floor the user wants to keep; null when unset or balances are hidden.
    @Json(name = "min_balance") val minBalance: Double? = null
)

/**
 * A new account. [type] is "savings" or "credit_card"; a card is named CC-… on
 * the server, and only a savings account keeps [balance] and [minBalance].
 */
@JsonClass(generateAdapter = true)
data class CreateAccountRequestDto(
    @Json(name = "name") val name: String,
    @Json(name = "type") val type: String,
    @Json(name = "balance") val balance: Double? = null,
    @Json(name = "min_balance") val minBalance: Double? = null
)

@JsonClass(generateAdapter = true)
data class CreateAccountResponseDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null,
    @Json(name = "account") val account: AccountDto? = null
)
