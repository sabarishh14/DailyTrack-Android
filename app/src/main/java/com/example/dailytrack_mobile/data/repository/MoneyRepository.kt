package com.example.dailytrack_mobile.data.repository

import com.example.dailytrack_mobile.data.local.datastore.PrefsStringListCache
import com.example.dailytrack_mobile.data.local.demo.DemoDataManager
import com.example.dailytrack_mobile.data.remote.api.DailyTrackApi
import com.example.dailytrack_mobile.data.remote.dto.AccountDto
import com.example.dailytrack_mobile.data.remote.dto.AddTransactionRequestDto
import com.example.dailytrack_mobile.data.remote.dto.BudgetDto
import com.example.dailytrack_mobile.data.remote.dto.BudgetSuggestionDto
import com.example.dailytrack_mobile.data.remote.dto.BulkEditTransactionItemDto
import com.example.dailytrack_mobile.data.remote.dto.TransactionDto
import com.example.dailytrack_mobile.data.remote.dto.TransactionsResponseDto
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoneyRepository @Inject constructor(
    private val api: DailyTrackApi,
    private val demoDataManager: DemoDataManager,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val KEY_MOST_USED_EXPENSE = "cached_most_used_expense"
        private const val KEY_MOST_USED_INCOME = "cached_most_used_income"
        private const val KEY_ACCOUNTS = "cached_accounts"
        private const val KEY_CATEGORIES = "cached_categories"
        private const val KEY_DEMO_BUDGETS = "demo_budgets"
        private const val MAX_SHEET_SYNC_BATCHES = 200
    }

    val dataUpdateFlow: SharedFlow<Unit> get() = demoDataManager.dataUpdateFlow

    private val prefs by lazy {
        context.getSharedPreferences("money_repo_cache", Context.MODE_PRIVATE)
    }
    private val listCache by lazy { PrefsStringListCache(prefs) }

    private var cachedAccounts: List<AccountDto>? = null
    private var cachedAccountNames = mutableListOf<String>()
    private val cachedTransactions = mutableMapOf<String, TransactionsResponseDto>()
    private var cachedCategories: List<String>? = null
    private var cachedBudgets: List<BudgetDto>? = null
    private var inMemoryMostUsedExpense = mutableListOf<String>()
    private var inMemoryMostUsedIncome = mutableListOf<String>()
    private val cachedAllDescriptions = mutableListOf<String>()
    private val cachedDescriptionsByCategory = mutableMapOf<String, MutableList<String>>()
    private var allHistoricalTransactionsFetched = false

    init {
        try {
            inMemoryMostUsedExpense = listCache.read(KEY_MOST_USED_EXPENSE).toMutableList()
            inMemoryMostUsedIncome = listCache.read(KEY_MOST_USED_INCOME).toMutableList()
            cachedAccountNames = listCache.read(KEY_ACCOUNTS).toMutableList()
            listCache.read(KEY_CATEGORIES).takeIf { it.isNotEmpty() }?.let { cachedCategories = it }
        } catch (_: Exception) { }
    }

    fun getCachedMostUsedExpenseCategories(): List<String> = synchronized(this) { inMemoryMostUsedExpense.toList() }
    fun getCachedMostUsedIncomeCategories(): List<String> = synchronized(this) { inMemoryMostUsedIncome.toList() }
    fun getCachedAccounts(): List<String> = synchronized(this) { (cachedAccounts?.map { it.account } ?: cachedAccountNames).toList() }
    fun getCachedCategories(): List<String> = synchronized(this) { cachedCategories ?: emptyList() }

    fun saveMostUsedCategories(expenses: List<String>, income: List<String>) {
        synchronized(this) {
            if (expenses.isNotEmpty()) {
                inMemoryMostUsedExpense = expenses.toMutableList()
                listCache.write(KEY_MOST_USED_EXPENSE, expenses)
            }
            if (income.isNotEmpty()) {
                inMemoryMostUsedIncome = income.toMutableList()
                listCache.write(KEY_MOST_USED_INCOME, income)
            }
        }
    }

    fun clearCache() {
        cachedTransactions.clear()
        cachedBudgets = null
    }

    fun recordSingleDescription(category: String, note: String) {
        val trimmed = note.trim()
        if (trimmed.isNotBlank()) {
            synchronized(this) {
                cachedAllDescriptions.remove(trimmed)
                cachedAllDescriptions.add(0, trimmed)
                val catKey = category.trim()
                if (catKey.isNotBlank()) {
                    val list = cachedDescriptionsByCategory.getOrPut(catKey) { mutableListOf() }
                    list.remove(trimmed)
                    list.add(0, trimmed)
                }
            }
        }
    }

    fun recordTransactions(txs: List<TransactionDto>) {
        synchronized(this) {
            for (tx in txs) {
                val desc = tx.description?.trim()
                if (!desc.isNullOrBlank()) {
                    if (!cachedAllDescriptions.contains(desc)) {
                        cachedAllDescriptions.add(desc)
                    }
                    val catKey = tx.heading.trim()
                    if (catKey.isNotBlank()) {
                        val list = cachedDescriptionsByCategory.getOrPut(catKey) { mutableListOf() }
                        if (!list.contains(desc)) {
                            list.add(desc)
                        }
                    }
                }
            }
        }
    }

    fun getAllCachedDescriptions(): Pair<List<String>, Map<String, List<String>>> {
        synchronized(this) {
            return Pair(
                cachedAllDescriptions.toList(),
                cachedDescriptionsByCategory.mapValues { it.value.toList() }
            )
        }
    }

    suspend fun fetchAllTransactionsForDescriptions(
        forceRefresh: Boolean = false,
        onBatchLoaded: ((List<String>, Map<String, List<String>>) -> Unit)? = null
    ): Pair<List<String>, Map<String, List<String>>> {
        if (!forceRefresh && allHistoricalTransactionsFetched && cachedAllDescriptions.isNotEmpty()) {
            return getAllCachedDescriptions()
        }

        var offset = 0
        var hasMore = true

        while (hasMore) {
            val result = getTransactions(limit = 500, offset = offset, forceRefresh = forceRefresh).getOrNull()
            if (result == null || result.transactions.isEmpty()) break
            
            recordTransactions(result.transactions)
            
            val currentCached = getAllCachedDescriptions()
            onBatchLoaded?.invoke(currentCached.first, currentCached.second)

            hasMore = result.hasMore
            offset += 500
        }

        allHistoricalTransactionsFetched = true
        return getAllCachedDescriptions()
    }

    suspend fun getAccounts(forceRefresh: Boolean = false): Result<List<AccountDto>> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            demoDataManager.getAccounts()
        } else {
            if (!forceRefresh && cachedAccounts != null) {
                cachedAccounts!!
            } else {
                api.getAccounts().also {
                    cachedAccounts = it
                    try { listCache.write(KEY_ACCOUNTS, it.map { a -> a.account }) } catch (_: Exception) {}
                }
            }
        }
    }

    suspend fun getTransactions(
        limit: Int = 100,
        offset: Int = 0,
        month: String? = null,
        forceRefresh: Boolean = false
    ): Result<TransactionsResponseDto> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            demoDataManager.getTransactions(limit = limit, offset = offset, month = month).also {
                recordTransactions(it.transactions)
            }
        } else {
            val key = "$limit-$offset-$month"
            if (!forceRefresh && cachedTransactions.containsKey(key)) {
                cachedTransactions[key]!!.also {
                    recordTransactions(it.transactions)
                }
            } else {
                api.getTransactions(limit = limit, offset = offset, month = month).also {
                    cachedTransactions[key] = it
                    recordTransactions(it.transactions)
                }
            }
        }
    }

    suspend fun getCategories(forceRefresh: Boolean = false): Result<List<String>> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            demoDataManager.getCategories()
        } else {
            if (!forceRefresh && cachedCategories != null) {
                cachedCategories!!
            } else {
                api.getCategories().categories.also {
                    cachedCategories = it
                    try { listCache.write(KEY_CATEGORIES, it) } catch (_: Exception) {}
                }
            }
        }
    }

    suspend fun addTransaction(
        type: String,
        category: String,
        amount: Double,
        note: String?,
        accountName: String,
        date: String,
        excludeAnalytics: Boolean
    ): Result<Unit> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            demoDataManager.addTransaction(
                type = type,
                category = category,
                amount = amount,
                note = note,
                accountName = accountName,
                date = date,
                excludeAnalytics = excludeAnalytics
            )
        } else {
            val request = AddTransactionRequestDto(
                account = accountName,
                date = date,
                type = type,
                heading = category,
                description = note ?: "",
                amount = amount,
                excludeAnalytics = excludeAnalytics
            )
            val response = api.addTransaction(request)
            if (!response.success) {
                throw Exception(response.message ?: "Failed to add transaction")
            }
            if (!note.isNullOrBlank()) {
                recordSingleDescription(category = category, note = note)
            }
            clearCache()
            demoDataManager.notifyDataUpdated()
        }
    }

    suspend fun updateTransaction(
        id: Long,
        type: String,
        category: String,
        amount: Double,
        note: String?,
        accountName: String,
        date: String,
        excludeAnalytics: Boolean
    ): Result<Unit> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            demoDataManager.updateTransaction(
                id = id,
                type = type,
                category = category,
                amount = amount,
                note = note,
                accountName = accountName,
                date = date,
                excludeAnalytics = excludeAnalytics
            )
            if (!note.isNullOrBlank()) {
                recordSingleDescription(category = category, note = note)
            }
        } else {
            val request = AddTransactionRequestDto(
                account = accountName,
                date = date,
                type = type,
                heading = category,
                description = note ?: "",
                amount = amount,
                excludeAnalytics = excludeAnalytics
            )
            val response = api.updateTransaction(id, request)
            if (!response.success) {
                throw Exception(response.message ?: "Failed to update transaction")
            }
            if (!note.isNullOrBlank()) {
                recordSingleDescription(category = category, note = note)
            }
            clearCache()
            demoDataManager.notifyDataUpdated()
        }
    }

    suspend fun deleteTransaction(id: Long): Result<Unit> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            demoDataManager.deleteTransaction(id)
        } else {
            val response = api.deleteTransaction(id)
            if (!response.success) {
                throw Exception(response.message ?: "Failed to delete transaction")
            }
            clearCache()
            demoDataManager.notifyDataUpdated()
        }
    }

    suspend fun bulkDeleteTransactions(ids: List<Long>): Result<Unit> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            ids.forEach { demoDataManager.deleteTransaction(it) }
        } else {
            val response = api.bulkDeleteTransactions(ids)
            if (!response.success) {
                throw Exception(response.message ?: "Failed to bulk delete transactions")
            }
            clearCache()
            demoDataManager.notifyDataUpdated()
        }
    }

    suspend fun bulkEditTransactions(updates: List<BulkEditTransactionItemDto>): Result<Unit> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            updates.forEach { item ->
                demoDataManager.updateTransaction(
                    id = item.id,
                    type = item.type,
                    category = item.heading,
                    amount = item.amount,
                    note = item.description,
                    accountName = item.account,
                    date = item.date,
                    excludeAnalytics = item.excludeAnalytics
                )
                if (!item.description.isNullOrBlank()) {
                    recordSingleDescription(category = item.heading, note = item.description)
                }
            }
        } else {
            val response = api.bulkEditTransactions(updates)
            if (!response.success) {
                throw Exception(response.message ?: "Failed to bulk edit transactions")
            }
            updates.forEach { item ->
                if (!item.description.isNullOrBlank()) {
                    recordSingleDescription(category = item.heading, note = item.description)
                }
            }
            clearCache()
            demoDataManager.notifyDataUpdated()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Budgets
    //
    // Demo mode keeps its own budgets in prefs so the section is fully editable
    // offline; suggestions are server-computed and so stay empty there.
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun getBudgets(forceRefresh: Boolean = false): Result<List<BudgetDto>> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            readDemoBudgets()
        } else {
            if (!forceRefresh && cachedBudgets != null) {
                cachedBudgets!!
            } else {
                val response = api.getBudgets()
                if (!response.success) {
                    throw Exception(response.message ?: "Failed to load budgets")
                }
                response.budgets.also { cachedBudgets = it }
            }
        }
    }

    suspend fun getBudgetSuggestions(): Result<Map<String, BudgetSuggestionDto>> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            emptyMap()
        } else {
            val response = api.getBudgetSuggestions()
            if (!response.success) {
                throw Exception(response.message ?: "Failed to load budget suggestions")
            }
            response.suggestions
        }
    }

    /** Upserts the whole set at once; entries at 0 or less are deleted server-side. */
    suspend fun saveBudgets(budgets: List<BudgetDto>): Result<Unit> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            writeDemoBudgets(budgets.filter { it.monthlyLimit > 0.0 })
        } else {
            val response = api.updateBudgetsBulk(budgets)
            if (!response.success) {
                throw Exception(response.message ?: "Failed to save budgets")
            }
            cachedBudgets = null
        }
    }

    private fun readDemoBudgets(): List<BudgetDto> {
        val raw = listCache.read(KEY_DEMO_BUDGETS)
        return raw.mapNotNull { entry ->
            val category = entry.substringBeforeLast('=', "")
            val limit = entry.substringAfterLast('=', "").toDoubleOrNull()
            if (category.isBlank() || limit == null) null else BudgetDto(category, limit)
        }
    }

    private fun writeDemoBudgets(budgets: List<BudgetDto>) {
        listCache.write(KEY_DEMO_BUDGETS, budgets.map { "${it.category}=${it.monthlyLimit}" })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Outbound syncs & reconciliation
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun getPendingSheetSyncCount(): Result<Int> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            0
        } else {
            val response = api.getPendingSheetSyncCount()
            if (!response.success) {
                throw Exception(response.message ?: "Failed to check pending transactions")
            }
            response.count
        }
    }

    /**
     * Drains the Sheets queue. The server pushes a few rows per call, so this
     * loops until it reports nothing left, reporting each batch through
     * [onBatch] so the UI can count up rather than sit on a spinner.
     */
    suspend fun syncTransactionsToSheets(
        onBatch: (syncedSoFar: Int, message: String?) -> Unit = { _, _ -> }
    ): Result<Int> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            return@runCatching 0
        }
        var total = 0
        var batches = 0
        while (true) {
            val response = api.syncTransactionsToSheets()
            if (!response.success) {
                throw Exception(response.message ?: "Sheets sync failed")
            }
            total += response.syncedCount
            batches++
            onBatch(total, response.message)
            if (!response.hasMore || response.syncedCount == 0) break
            // The queue is drained a handful of rows at a time; this cap stops a
            // server that always reports `has_more` from looping forever.
            if (batches >= MAX_SHEET_SYNC_BATCHES) break
        }
        total
    }

    suspend fun syncInvestmentsToSheets(): Result<String> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            return@runCatching "Demo mode — nothing to push."
        }
        val response = api.syncInvestmentsToSheets()
        if (!response.success) {
            throw Exception(response.message ?: "Investment sync failed")
        }
        response.message ?: "Investments synced to Sheets."
    }

    suspend fun reconcileBalancesFromScreenshots(): Result<String> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            return@runCatching "Demo mode — no screenshots to scan."
        }
        val response = api.reconcileBalancesFromScreenshots()
        if (!response.success) {
            throw Exception(response.message ?: "Could not scan screenshots")
        }
        cachedAccounts = null
        clearCache()
        demoDataManager.notifyDataUpdated()
        response.message ?: "Balances reconciled."
    }

    suspend fun checkHealth(): Result<Boolean> = runCatching {
        if (demoDataManager.isDemoModeEnabled()) {
            return@runCatching true
        }
        try {
            val response = api.checkHealth()
            if (response.isSuccessful) {
                response.body()?.close()
                return@runCatching true
            }
        } catch (_: Exception) {
            // If / check threw, proceed to try /test-db
        }

        try {
            val dbResponse = api.testDb()
            if (dbResponse.isSuccessful) {
                dbResponse.body()?.close()
                return@runCatching true
            }
        } catch (_: Exception) {
            // Proceed to fallback
        }

        // Final fallback: if screen hydration works, getAccounts() will confirm the server is reachable
        api.getAccounts()
        true
    }
}
