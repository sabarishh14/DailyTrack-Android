@file:Suppress("PropertyName")

package com.example.dailytrack_mobile.data.remote.dto

import com.squareup.moshi.JsonClass

// The private vault's contents (data/vault): the same tables the server kept
// readable, row for row, field for field (named as the server names them), so
// the web and the phone read each other's items. Locked away in items: "assets",
// "funds", "stocks", "state", and "day:YYYY-MM" for each month of days.

@JsonClass(generateAdapter = true)
data class VSnapshot(
    var id: Long,
    var date: String,
    var total_equity_inv: Double? = 0.0,
    var total_equity_curr: Double? = 0.0,
    var total_mf_inv: Double? = 0.0,
    var total_mf_curr: Double? = 0.0,
    var total_fixed_income_inv: Double? = 0.0,
    var total_fixed_income_curr: Double? = 0.0,
    var total_provident_inv: Double? = 0.0,
    var total_provident_curr: Double? = 0.0,
    var total_gold_inv: Double? = 0.0,
    var total_gold_curr: Double? = 0.0,
    var grand_total_inv: Double? = 0.0,
    var grand_total_curr: Double? = 0.0,
    var synced: Boolean? = false,
)

@JsonClass(generateAdapter = true)
data class VEquity(
    var id: Long,
    var date: String,
    var symbol: String,
    var quantity: Double,
    var average_price: Double,
    var ltp: Double,
    var invested_value: Double,
    var current_value: Double,
    var repriced: Boolean? = false,
)

@JsonClass(generateAdapter = true)
data class VMf(
    var id: Long,
    var date: String,
    var symbol: String,
    var quantity: Double,
    var average_price: Double,
    var nav: Double,
    var invested_value: Double,
    var current_value: Double,
    var isin: String? = null,
    var repriced: Boolean? = false,
)

@JsonClass(generateAdapter = true)
data class VAsset(
    var id: Long,
    var category: String,
    var name: String,
    var invested_value: Double? = 0.0,
    var current_value: Double? = 0.0,
    var interest_rate: Double? = null,
    var start_date: String? = null,
    var maturity_date: String? = null,
    var last_updated: String? = null,
    var installment: Double? = null,
)

@JsonClass(generateAdapter = true)
data class VTask(
    var id: Long,
    var asset_name: String? = null,
    var amount_to_add: Double? = null,
    var interval_value: Int? = 1,
    var interval_unit: String? = "months",
    var next_run_date: String? = null,
    var is_active: Boolean? = true,
)

@JsonClass(generateAdapter = true)
data class VFund(
    var id: Long,
    var amfi_code: String,
    var isin: String? = null,
    var name: String,
    var amc: String? = null,
    var folio_ref: String,
    var folio_tail: String? = null,
    var units: Double? = 0.0,
    var invested: Double? = 0.0,
    var source: String? = "manual",
    var statement_to: String? = null,
    var updated_at: String? = null,
)

@JsonClass(generateAdapter = true)
data class VFundTx(
    var id: Long,
    var fund_id: Long,
    var date: String,
    var kind: String,
    var amount: Double? = null,
    var units: Double? = null,
    var nav: Double? = null,
    var source: String,
    var key: String,
)

@JsonClass(generateAdapter = true)
data class VSip(
    var id: Long,
    var fund_id: Long,
    var amount: Double? = null,
    var day: Int,
    var next_date: String,
    var active: Boolean? = true,
)

@JsonClass(generateAdapter = true)
data class VFundValue(
    var id: Long,
    var date: String,
    var amfi_code: String,
    var name: String,
    var units: Double? = 0.0,
    var nav: Double? = 0.0,
    var nav_date: String? = null,
    var invested: Double? = 0.0,
    var value: Double? = 0.0,
)

@JsonClass(generateAdapter = true)
data class VStock(
    var id: Long,
    var symbol: String,
    var name: String,
    var isin: String? = null,
    var quantity: Double = 0.0,
    var invested: Double? = 0.0,
    var updated_at: String? = null,
)

@JsonClass(generateAdapter = true)
data class VStockTrade(
    var id: Long,
    var stock_id: Long,
    var date: String,
    var kind: String,
    var quantity: Double,
    var price: Double,
)

@JsonClass(generateAdapter = true)
data class VStockValue(
    var id: Long,
    var date: String,
    var symbol: String,
    var name: String,
    var quantity: Double,
    var price: Double,
    var invested: Double,
    var value: Double,
)

/** When Kite's holdings (and stocks added by hand) were last priced, and when the prices traded (IST). */
@JsonClass(generateAdapter = true)
data class VState(
    var priced_at: String? = null,
    var quoted_at: String? = null,
    var quotes_seen_at: String? = null,
    var stocks_priced_at: String? = null,
    var stocks_quoted_at: String? = null,
)

/** Everything in the vault, opened. */
@JsonClass(generateAdapter = true)
data class VTables(
    var snapshots: MutableList<VSnapshot> = mutableListOf(),
    var equity: MutableList<VEquity> = mutableListOf(),
    var mf: MutableList<VMf> = mutableListOf(),
    var assets: MutableList<VAsset> = mutableListOf(),
    var tasks: MutableList<VTask> = mutableListOf(),
    var funds: MutableList<VFund> = mutableListOf(),
    var fund_tx: MutableList<VFundTx> = mutableListOf(),
    var sips: MutableList<VSip> = mutableListOf(),
    var fund_values: MutableList<VFundValue> = mutableListOf(),
    var stocks: MutableList<VStock> = mutableListOf(),
    var stock_trades: MutableList<VStockTrade> = mutableListOf(),
    var stock_values: MutableList<VStockValue> = mutableListOf(),
    var state: VState = VState(),
)

@JsonClass(generateAdapter = true)
data class VAssetsItem(val assets: List<VAsset> = emptyList(), val tasks: List<VTask> = emptyList())

@JsonClass(generateAdapter = true)
data class VFundsItem(val funds: List<VFund> = emptyList(), val fund_tx: List<VFundTx> = emptyList(), val sips: List<VSip> = emptyList())

@JsonClass(generateAdapter = true)
data class VStocksItem(val stocks: List<VStock> = emptyList(), val stock_trades: List<VStockTrade> = emptyList())

@JsonClass(generateAdapter = true)
data class VDayItem(
    val snapshots: List<VSnapshot> = emptyList(),
    val equity: List<VEquity> = emptyList(),
    val mf: List<VMf> = emptyList(),
    val fund_values: List<VFundValue> = emptyList(),
    val stock_values: List<VStockValue> = emptyList(),
)
