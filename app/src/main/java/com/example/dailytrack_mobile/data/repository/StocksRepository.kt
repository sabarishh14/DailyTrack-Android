package com.example.dailytrack_mobile.data.repository

import com.example.dailytrack_mobile.data.local.demo.DemoDataManager
import com.example.dailytrack_mobile.data.remote.api.DailyTrackApi
import com.example.dailytrack_mobile.data.remote.dto.StockSearchResultDto
import com.example.dailytrack_mobile.data.remote.dto.StockTradeRequestDto
import com.example.dailytrack_mobile.data.remote.dto.StocksResponseDto
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stocks and ETFs added by hand: valued through the day at the market's price
 * (backend/stocks_service.py, or the private vault's engine). Each change
 * refreshes the Investments screen.
 */
@Singleton
class StocksRepository @Inject constructor(
    private val api: DailyTrackApi,
    private val investments: InvestmentsRepository,
    private val demo: DemoDataManager
) {
    suspend fun stocks(): Result<StocksResponseDto> =
        if (demo.isDemoModeEnabled()) Result.success(StocksResponseDto()) else call { api.getStocks() }

    suspend fun search(query: String): Result<List<StockSearchResultDto>> =
        call { api.searchStocks(query) }.map { it.results }

    suspend fun add(symbol: String, quantity: Double, price: Double, date: String) =
        change { api.addStock(StockTradeRequestDto(symbol = symbol, quantity = quantity, price = price, date = date)) }

    suspend fun trade(symbol: String, kind: String, quantity: Double, price: Double, date: String) =
        change { api.addStockTrade(symbol, StockTradeRequestDto(kind = kind, quantity = quantity, price = price, date = date)) }

    suspend fun removeTrade(symbol: String, id: Long) = change { api.removeStockTrade(symbol, id) }

    suspend fun remove(symbol: String) = change { api.removeStock(symbol) }

    private suspend fun change(block: suspend () -> Response<StocksResponseDto>): Result<StocksResponseDto> =
        call(block).onSuccess {
            investments.clearCache()
            demo.notifyDataUpdated()
        }

    private suspend fun <T> call(block: suspend () -> Response<T>): Result<T> = try {
        val response = block()
        val body = response.body()
        if (response.isSuccessful && body != null) Result.success(body)
        else Result.failure(Exception(serverMessage(response) ?: "Something went wrong (${response.code()})"))
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Result.failure(Exception("Couldn't reach the server"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun serverMessage(response: Response<*>): String? = response.errorBody()?.string()?.let {
        runCatching { org.json.JSONObject(it).optString("message") }.getOrNull()?.takeIf { m -> m.isNotBlank() }
    }
}
