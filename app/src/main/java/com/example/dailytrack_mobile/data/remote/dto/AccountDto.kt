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
