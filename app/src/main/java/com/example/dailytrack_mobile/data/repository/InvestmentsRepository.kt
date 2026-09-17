package com.example.dailytrack_mobile.data.repository

import com.example.dailytrack_mobile.data.local.demo.DemoDataManager
import com.example.dailytrack_mobile.data.remote.api.DailyTrackApi
import com.example.dailytrack_mobile.data.remote.dto.EquityHoldingDto
import com.example.dailytrack_mobile.data.remote.dto.ManualAssetDto
import com.example.dailytrack_mobile.data.remote.dto.AddManualAssetRequestDto
import com.example.dailytrack_mobile.data.remote.dto.MutualFundHoldingDto
import com.example.dailytrack_mobile.data.remote.dto.PortfolioSnapshotDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class FullPortfolioData(
    val snapshots: List<PortfolioSnapshotDto>,
    val equityHoldings: List<EquityHoldingDto>,
    val mutualFundHoldings: List<MutualFundHoldingDto>,
    val manualAssets: List<ManualAssetDto>
)

@Singleton
class InvestmentsRepository @Inject constructor(
    private val api: DailyTrackApi,
    private val demoDataManager: DemoDataManager
) {
    val dataUpdateFlow: SharedFlow<Unit> get() = demoDataManager.dataUpdateFlow

    private var cachedPortfolio: FullPortfolioData? = null
    private val cachedEquityByDate = mutableMapOf<String, List<EquityHoldingDto>>()
    private val cachedMutualFundsByDate = mutableMapOf<String, List<MutualFundHoldingDto>>()

    fun clearCache() {
        cachedPortfolio = null
        cachedEquityByDate.clear()
        cachedMutualFundsByDate.clear()
    }

    suspend fun getFullPortfolio(forceRefresh: Boolean = false): Result<FullPortfolioData> = coroutineScope {
        try {
            if (demoDataManager.isDemoModeEnabled()) {
                return@coroutineScope Result.success(demoDataManager.getFullPortfolio())
            }

            if (!forceRefresh && cachedPortfolio != null) {
                return@coroutineScope Result.success(cachedPortfolio!!)
            }

            // 1. Fetch snapshots to get historical data and the latest date
            val snapshots = api.getInvestments()
            
            // Extract the latest date (assuming the list is ordered descending as in the backend)
            val latestDate = snapshots.firstOrNull()?.date?.substringBefore("T")
            
            // 2. Fetch parallel requests for holdings
            val equityDeferred = async { api.getEquityHoldings() }
            val mfDeferred = async { 
                if (latestDate != null) {
                    api.getMutualFundHoldings(latestDate)
                } else {
                    emptyList()
                }
            }
            val manualAssetsDeferred = async { api.getManualAssets() }
            
            val equityHoldings = equityDeferred.await()
            val mutualFundHoldings = mfDeferred.await()
            val manualAssets = manualAssetsDeferred.await()
            
            val fullData = FullPortfolioData(
                snapshots = snapshots,
                equityHoldings = equityHoldings,
                mutualFundHoldings = mutualFundHoldings,
                manualAssets = manualAssets
            )
            cachedPortfolio = fullData
            Result.success(fullData)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Equity holdings as they stood on [date]. Snapshots are immutable once
     * written, so a fetched date is cached for the life of the session.
     */
    suspend fun getEquityHoldingsForDate(date: String): Result<List<EquityHoldingDto>> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            demoDataManager.getFullPortfolio().equityHoldings
        } else {
            cachedEquityByDate[date] ?: api.getEquityHoldingsForDate(date).also {
                cachedEquityByDate[date] = it
            }
        }
    }

    /** Mutual fund holdings as they stood on [date]. See [getEquityHoldingsForDate]. */
    suspend fun getMutualFundHoldingsForDate(date: String): Result<List<MutualFundHoldingDto>> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            demoDataManager.getFullPortfolio().mutualFundHoldings
        } else {
            cachedMutualFundsByDate[date] ?: api.getMutualFundHoldings(date).also {
                cachedMutualFundsByDate[date] = it
            }
        }
    }

    suspend fun addInvestment(
        name: String,
        category: String,
        amount: Double,
        frequency: String,
        note: String?
    ): Result<Unit> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            demoDataManager.addInvestment(
                name = name,
                category = category,
                amount = amount,
                frequency = frequency,
                note = note
            )
        }
    }

    suspend fun addManualAsset(
        category: String,
        name: String,
        investedValue: Double,
        currentValue: Double,
        interestRate: Double? = null,
        startDate: String? = null,
        maturityDate: String? = null,
        isRecurring: Boolean = false,
        amountToAdd: Double? = null,
        intervalValue: Int? = null,
        intervalUnit: String? = null,
        nextRunDate: String? = null
    ): Result<Unit> = coroutineScope {
        try {
            if (demoDataManager.isDemoModeEnabled()) {
                demoDataManager.addManualAsset(
                    category = category,
                    name = name,
                    investedValue = investedValue,
                    currentValue = currentValue,
                    interestRate = interestRate,
                    startDate = startDate,
                    maturityDate = maturityDate,
                    isRecurring = isRecurring,
                    amountToAdd = amountToAdd,
                    intervalValue = intervalValue,
                    intervalUnit = intervalUnit,
                    nextRunDate = nextRunDate
                )
                return@coroutineScope Result.success(Unit)
            }

            val request = AddManualAssetRequestDto(
                category = category,
                name = name,
                investedValue = investedValue,
                currentValue = currentValue,
                interestRate = interestRate,
                startDate = startDate,
                maturityDate = maturityDate,
                isRecurring = isRecurring,
                amountToAdd = amountToAdd,
                intervalValue = intervalValue,
                intervalUnit = intervalUnit,
                nextRunDate = nextRunDate
            )
            val response = api.addManualAsset(request)
            if (response.success) {
                clearCache()
                demoDataManager.notifyDataUpdated()
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message ?: "Failed to save asset"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addAsset(
        name: String,
        assetClass: String,
        purchasePrice: Double,
        currentValue: Double,
        note: String?
    ): Result<Unit> = addManualAsset(
        category = assetClass,
        name = name,
        investedValue = purchasePrice,
        currentValue = currentValue
    )
}
