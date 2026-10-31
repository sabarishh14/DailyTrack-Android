package com.example.dailytrack_mobile.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PortfolioSnapshotDto(
    val id: Long,
    val date: String,
    
    @Json(name = "inv_stocks") val invStocks: Double?,
    @Json(name = "curr_stocks") val currStocks: Double?,
    
    @Json(name = "inv_mf") val invMf: Double?,
    @Json(name = "curr_mf") val currMf: Double?,
    
    @Json(name = "inv_fixed") val invFixed: Double?,
    @Json(name = "curr_fixed") val currFixed: Double?,
    
    @Json(name = "inv_prov") val invProv: Double?,
    @Json(name = "curr_prov") val currProv: Double?,
    
    @Json(name = "inv_gold") val invGold: Double?,
    @Json(name = "curr_gold") val currGold: Double?,
    
    @Json(name = "total_inv") val grandTotalInv: Double?,
    @Json(name = "total_curr") val grandTotalCurr: Double?
)

@JsonClass(generateAdapter = true)
data class EquityHoldingDto(
    val symbol: String,
    val quantity: Double,
    @Json(name = "average_price") val averagePrice: Double,
    val ltp: Double,
    @Json(name = "invested_value") val investedValue: Double,
    @Json(name = "current_value") val currentValue: Double
)

/** One day of a single stock's or fund's position (GET /api/investments/history). */
@JsonClass(generateAdapter = true)
data class HoldingHistoryPointDto(
    val date: String,
    @Json(name = "Current") val current: Double? = null,
    @Json(name = "Invested") val invested: Double? = null
)

@JsonClass(generateAdapter = true)
data class MutualFundHoldingDto(
    val symbol: String,
    val quantity: Double,
    @Json(name = "average_price") val averagePrice: Double,
    val nav: Double,
    @Json(name = "invested_value") val investedValue: Double,
    @Json(name = "current_value") val currentValue: Double
)

@JsonClass(generateAdapter = true)
data class ManualAssetDto(
    val id: Long,
    val category: String, // e.g. "FD", "EPF", "PPF", "NPS", "SGB", "RealEstate", "Cash"
    val name: String,
    @Json(name = "invested_value") val investedValue: Double,
    @Json(name = "current_value") val currentValue: Double,
    @Json(name = "interest_rate") val interestRate: Double?,
    @Json(name = "start_date") val startDate: String?,
    @Json(name = "maturity_date") val maturityDate: String?,
    @Json(name = "last_updated") val lastUpdated: String?
)

@JsonClass(generateAdapter = true)
data class AddManualAssetRequestDto(
    val category: String,
    val name: String,
    @Json(name = "invested_value") val investedValue: Double,
    @Json(name = "current_value") val currentValue: Double,
    @Json(name = "interest_rate") val interestRate: Double? = null,
    @Json(name = "start_date") val startDate: String? = null,
    @Json(name = "maturity_date") val maturityDate: String? = null,
    @Json(name = "is_recurring") val isRecurring: Boolean = false,
    @Json(name = "amount_to_add") val amountToAdd: Double? = null,
    @Json(name = "interval_value") val intervalValue: Int? = null,
    @Json(name = "interval_unit") val intervalUnit: String? = null,
    @Json(name = "next_run_date") val nextRunDate: String? = null,
    // RD only: the monthly deposit; the server works out the values from it.
    @Json(name = "installment") val installment: Double? = null
)

/**
 * How fresh the holdings' prices are (/api/investments/prices, kite_daily.py):
 * on Daily, when the stocks' prices traded ([stale] while the market's open and
 * they're well behind); on Manual, the last Kite sync. Times are India's.
 */
@JsonClass(generateAdapter = true)
data class PriceTimesDto(
    @Json(name = "mode") val mode: String = "manual",
    @Json(name = "synced") val synced: String? = null,
    @Json(name = "stocks_at") val stocksAt: String? = null,
    @Json(name = "checked_at") val checkedAt: String? = null,
    @Json(name = "stale") val stale: Boolean = false
)
