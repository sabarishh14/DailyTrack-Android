package com.example.dailytrack_mobile.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Stocks and ETFs added by hand (the server's blueprints/stocks.py): any NSE
// stock, bought and sold at the price paid, valued through the day.

@JsonClass(generateAdapter = true)
data class StockTradeDto(
    val id: Long,
    val date: String,
    /** BUY or SELL */
    val kind: String,
    val quantity: Double,
    val price: Double
)

@JsonClass(generateAdapter = true)
data class StockDto(
    val symbol: String,
    val name: String,
    val quantity: Double = 0.0,
    val invested: Double = 0.0,
    @Json(name = "avg_price") val avgPrice: Double? = null,
    val price: Double? = null,
    val value: Double? = null,
    val trades: List<StockTradeDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class StocksResponseDto(
    val success: Boolean = true,
    val stocks: List<StockDto> = emptyList(),
    /** When the latest of their prices traded (IST). */
    @Json(name = "quoted_at") val quotedAt: String? = null,
    val added: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class StockSearchResultDto(
    val symbol: String,
    val name: String,
    val isin: String? = null
)

@JsonClass(generateAdapter = true)
data class StockSearchResponseDto(
    val success: Boolean = true,
    val results: List<StockSearchResultDto> = emptyList()
)

/** A buy (adding a stock, or more of one) or a sale. */
@JsonClass(generateAdapter = true)
data class StockTradeRequestDto(
    val symbol: String? = null,
    val kind: String? = null,
    val quantity: Double,
    val price: Double,
    /** yyyy-MM-dd */
    val date: String
)
