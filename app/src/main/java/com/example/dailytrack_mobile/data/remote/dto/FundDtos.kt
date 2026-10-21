package com.example.dailytrack_mobile.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Mutual funds held outside Kite (the server's blueprints/funds.py): a CAS
// statement import, funds added by hand, and their SIPs.

@JsonClass(generateAdapter = true)
data class FundSipDto(
    val id: Long,
    val amount: Double,
    val day: Int,
    val next: String? = null
)

/** One fund, its folios merged: units held, what went in, and today's worth at AMFI's NAV. */
@JsonClass(generateAdapter = true)
data class FundDto(
    val code: String,
    val name: String,
    val amc: String? = null,
    val units: Double = 0.0,
    val invested: Double = 0.0,
    val value: Double? = null,
    val nav: Double? = null,
    /** The last 4 of each folio number; the rest is never stored. */
    val folios: List<String> = emptyList(),
    val sips: List<FundSipDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class FundsResponseDto(
    val success: Boolean = true,
    val funds: List<FundDto> = emptyList(),
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class FundSearchResultDto(
    val code: String,
    val name: String,
    val amc: String? = null,
    val nav: Double = 0.0,
    @Json(name = "nav_date") val navDate: String? = null
)

@JsonClass(generateAdapter = true)
data class FundSearchResponseDto(
    val success: Boolean = true,
    val results: List<FundSearchResultDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class FundSipRequestDto(
    val amount: Double,
    val day: Int,
    /** yyyy-MM-dd: past instalments since then are filled in at each day's NAV. */
    val since: String
)

/** A lump sum on a day, a monthly SIP, or both. */
@JsonClass(generateAdapter = true)
data class AddFundRequestDto(
    val code: String,
    val amount: Double? = null,
    val date: String? = null,
    val sip: FundSipRequestDto? = null
)

@JsonClass(generateAdapter = true)
data class FundImportResponseDto(
    val success: Boolean = true,
    val added: List<String> = emptyList(),
    val updated: List<String> = emptyList(),
    @Json(name = "skipped_kite") val skippedKite: List<String> = emptyList(),
    val unknown: List<String> = emptyList(),
    val funds: List<FundDto> = emptyList()
)
