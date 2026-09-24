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
import com.example.dailytrack_mobile.presentation.components.transaction.EntryHistory
import com.example.dailytrack_mobile.presentation.components.transaction.EntryType
import com.example.dailytrack_mobile.presentation.components.transaction.HistoryRow
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** One entry of a batch being added. [type] is the DB value ("Debit", "Credit", …). */
data class NewTransaction(
    val type: String,
    val category: String,
    val amount: Double,
    val note: String?,
    val accountName: String,
    val date: String,
    val excludeAnalytics: Boolean
)

@Singleton
class MoneyRepository @Inject constructor(
    private val api: DailyTrackApi,
    private val demoDataManager: DemoDataManager,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val KEY_TYPE_CATEGORIES_PREFIX = "cached_type_categories_"
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

    // Every transaction seen so far, by id, for type-aware category and description suggestions.
    private val historyRows = mutableMapOf<Long, HistoryRow>()
    private var localHistoryId = -1L
    private var fullHistoryFetched = false
    private var savedTypeCategories: Map<EntryType, List<String>> = emptyMap()

    init {
        try {
            cachedAccountNames = listCache.read(KEY_ACCOUNTS).toMutableList()
            listCache.read(KEY_CATEGORIES).takeIf { it.isNotEmpty() }?.let { cachedCategories = it }
            savedTypeCategories = EntryType.entries
                .associateWith { listCache.read(KEY_TYPE_CATEGORIES_PREFIX + it.name) }
                .filterValues { it.isNotEmpty() }
        } catch (_: Exception) { }
    }

    fun getCachedAccounts(): List<String> = synchronized(this) { (cachedAccounts?.map { it.account } ?: cachedAccountNames).toList() }
    fun getCachedCategories(): List<String> = synchronized(this) { cachedCategories ?: emptyList() }

    fun clearCache() {
        cachedTransactions.clear()
        cachedBudgets = null
    }

    /** Suggestions from what has been seen so far; until anything has, the per-type categories saved last time. */
    fun entryHistory(): EntryHistory = synchronized(this) {
        if (historyRows.isEmpty()) EntryHistory(categories = savedTypeCategories)
        else EntryHistory.from(historyRows.values.toList())
    }

    private fun recordTransactions(txs: List<TransactionDto>) {
        synchronized(this) {
            txs.forEach { historyRows[it.id] = HistoryRow(it.type, it.heading, it.description, it.date.take(10)) }
        }
    }

    /** A just-saved entry, so it shows up in suggestions before the next fetch. */
    private fun recordLocal(type: String, category: String, note: String?, date: String, id: Long? = null) {
        synchronized(this) {
            historyRows[id ?: localHistoryId--] = HistoryRow(type, category, note, date.take(10))
        }
    }

    private fun forgetTransactions(ids: Collection<Long>) {
        synchronized(this) { ids.forEach { historyRows.remove(it) } }
    }

    /** Pages through every transaction once, reporting suggestions after each page. */
    suspend fun fetchFullHistory(
        forceRefresh: Boolean = false,
        onBatchLoaded: ((EntryHistory) -> Unit)? = null
    ): EntryHistory {
        if (!forceRefresh && fullHistoryFetched) return entryHistory()

        var offset = 0
        var hasMore = true
        while (hasMore) {
            val result = getTransactions(limit = 500, offset = offset, forceRefresh = forceRefresh).getOrNull()
            if (result == null || result.transactions.isEmpty()) break
            onBatchLoaded?.invoke(entryHistory())
            hasMore = result.hasMore
            offset += 500
        }

        fullHistoryFetched = !hasMore
        val history = entryHistory()
        if (fullHistoryFetched) {
            try {
                history.categoriesByType.forEach { (type, list) -> listCache.write(KEY_TYPE_CATEGORIES_PREFIX + type.name, list) }
            } catch (_: Exception) { }
        }
        return history
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

    /** Saves a batch in one request, so either every entry lands or none do. */
    suspend fun addTransactions(entries: List<NewTransaction>): Result<Unit> = runCatching {
        require(entries.isNotEmpty()) { "Nothing to save" }
        if (demoDataManager.isDemoModeEnabled()) {
            entries.forEach { e ->
                demoDataManager.addTransaction(
                    type = e.type,
                    category = e.category,
                    amount = e.amount,
                    note = e.note,
                    accountName = e.accountName,
                    date = e.date,
                    excludeAnalytics = e.excludeAnalytics
                )
            }
        } else {
            val request = entries.map { e ->
                AddTransactionRequestDto(
                    account = e.accountName,
                    date = e.date,
                    type = e.type,
                    heading = e.category,
                    description = e.note ?: "",
                    amount = e.amount,
                    excludeAnalytics = e.excludeAnalytics
                )
            }
            val response = api.addTransactions(request)
            if (!response.success) {
                throw Exception(response.message ?: "Failed to add transactions")
            }
            entries.forEach { e -> recordLocal(e.type, e.category, e.note, e.date) }
            clearCache()
            demoDataManager.notifyDataUpdated()
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
            recordLocal(type, category, note, date)
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
            recordLocal(type, category, note, date, id)
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
            recordLocal(type, category, note, date, id)
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
            forgetTransactions(listOf(id))
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
            forgetTransactions(ids)
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
                recordLocal(item.type, item.heading, item.description, item.date, item.id)
            }
        } else {
            val response = api.bulkEditTransactions(updates)
            if (!response.success) {
                throw Exception(response.message ?: "Failed to bulk edit transactions")
            }
            updates.forEach { item -> recordLocal(item.type, item.heading, item.description, item.date, item.id) }
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
