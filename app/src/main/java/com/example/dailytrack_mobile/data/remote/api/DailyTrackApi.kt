package com.example.dailytrack_mobile.data.remote.api

import com.example.dailytrack_mobile.data.remote.dto.AccessOptionsResponseDto
import com.example.dailytrack_mobile.data.remote.dto.AccessUserRequestDto
import com.example.dailytrack_mobile.data.remote.dto.AccessUserResponseDto
import com.example.dailytrack_mobile.data.remote.dto.AccessUsersResponseDto
import com.example.dailytrack_mobile.data.remote.dto.AccountDto
import com.example.dailytrack_mobile.data.remote.dto.MeResponseDto
import com.example.dailytrack_mobile.data.remote.dto.AddActivityRequestDto
import com.example.dailytrack_mobile.data.remote.dto.AddManualAssetRequestDto
import com.example.dailytrack_mobile.data.remote.dto.AddMediaResponseDto
import com.example.dailytrack_mobile.data.remote.dto.AddMovieDiaryRequestDto
import com.example.dailytrack_mobile.data.remote.dto.AddMovieRequestDto
import com.example.dailytrack_mobile.data.remote.dto.AddTransactionRequestDto
import com.example.dailytrack_mobile.data.remote.dto.BulkEditTransactionItemDto
import com.example.dailytrack_mobile.data.remote.dto.BudgetDto
import com.example.dailytrack_mobile.data.remote.dto.BudgetSuggestionsResponseDto
import com.example.dailytrack_mobile.data.remote.dto.BudgetsResponseDto
import com.example.dailytrack_mobile.data.remote.dto.LetterboxdSyncRequestDto
import com.example.dailytrack_mobile.data.remote.dto.PendingSheetSyncDto
import com.example.dailytrack_mobile.data.remote.dto.SheetsSyncResponseDto
import com.example.dailytrack_mobile.data.remote.dto.AddTvDiaryRequestDto
import com.example.dailytrack_mobile.data.remote.dto.AddTvShowRequestDto
import com.example.dailytrack_mobile.data.remote.dto.ApiResponseDto
import com.example.dailytrack_mobile.data.remote.dto.CategoriesResponseDto
import com.example.dailytrack_mobile.data.remote.dto.EquityHoldingDto
import com.example.dailytrack_mobile.data.remote.dto.ManualAssetDto
import com.example.dailytrack_mobile.data.remote.dto.MediaFiltersResponseDto
import com.example.dailytrack_mobile.data.remote.dto.MediaLibraryResponseDto
import com.example.dailytrack_mobile.data.remote.dto.MediaSearchResponseDto
import com.example.dailytrack_mobile.data.remote.dto.MutualFundHoldingDto
import com.example.dailytrack_mobile.data.remote.dto.PhysicalActivityDto
import com.example.dailytrack_mobile.data.remote.dto.PortfolioSnapshotDto
import com.example.dailytrack_mobile.data.remote.dto.TransactionsResponseDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface DailyTrackApi {

    @GET("/api/accounts")
    suspend fun getAccounts(): List<AccountDto>

    @GET("/api/transactions")
    suspend fun getTransactions(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("month") month: String? = null
    ): TransactionsResponseDto

    @POST("/api/transactions")
    suspend fun addTransaction(
        @Body transaction: AddTransactionRequestDto
    ): ApiResponseDto

    /** Same endpoint with a list: the server saves the whole batch in one commit, or none of it. */
    @POST("/api/transactions")
    suspend fun addTransactions(
        @Body transactions: List<AddTransactionRequestDto>
    ): ApiResponseDto

    @PUT("/api/transactions/{id}")
    suspend fun updateTransaction(
        @Path("id") id: Long,
        @Body transaction: AddTransactionRequestDto
    ): ApiResponseDto

    @DELETE("/api/transactions/{id}")
    suspend fun deleteTransaction(
        @Path("id") id: Long
    ): ApiResponseDto

    @PUT("/api/transactions/bulk-edit")
    suspend fun bulkEditTransactions(
        @Body updates: List<BulkEditTransactionItemDto>
    ): ApiResponseDto

    @POST("/api/transactions/bulk-delete")
    suspend fun bulkDeleteTransactions(
        @Body ids: List<Long>
    ): ApiResponseDto

    @GET("/api/transactions/categories")
    suspend fun getCategories(): CategoriesResponseDto

    // ── Budgets ──────────────────────────────────────────────────────────────

    @GET("/api/budgets")
    suspend fun getBudgets(): BudgetsResponseDto

    /** Upserts every budget in one call; a limit of 0 or less deletes that budget. */
    @PUT("/api/budgets/bulk")
    suspend fun updateBudgetsBulk(
        @Body budgets: List<BudgetDto>
    ): ApiResponseDto

    @GET("/api/budgets/suggestions")
    suspend fun getBudgetSuggestions(): BudgetSuggestionsResponseDto

    @GET("/api/physical")
    suspend fun getPhysicalActivities(): List<PhysicalActivityDto>

    @POST("/api/physical")
    suspend fun addPhysicalActivity(
        @Body activity: AddActivityRequestDto
    ): ApiResponseDto

    @GET("/api/investments")
    suspend fun getInvestments(): List<PortfolioSnapshotDto>

    @GET("/api/equity")
    suspend fun getEquityHoldings(): List<EquityHoldingDto>

    @GET("/api/investments/{date}/holdings")
    suspend fun getMutualFundHoldings(@Path("date") date: String): List<MutualFundHoldingDto>

    @GET("/api/investments/{date}/equity_holdings")
    suspend fun getEquityHoldingsForDate(@Path("date") date: String): List<EquityHoldingDto>

    @GET("/api/manual_assets")
    suspend fun getManualAssets(): List<ManualAssetDto>

    @POST("/api/manual_assets")
    suspend fun addManualAsset(
        @Body asset: AddManualAssetRequestDto
    ): ApiResponseDto

    @GET("/api/media/library")
    suspend fun getMediaLibrary(
        @Query("limit") limit: Int = 60,
        @Query("offset") offset: Int = 0,
        @Query("type") type: String = "all",
        @Query("status") status: String = "WATCHING",
        @Query("year") year: String = "all",
        @Query("month") month: String = "all",
        @Query("week") week: String = "all",
        @Query("language") language: String = "all"
    ): MediaLibraryResponseDto

    @GET("/api/media/filters")
    suspend fun getMediaFilters(): MediaFiltersResponseDto

    @GET("/api/media/search")
    suspend fun searchMedia(
        @Query("q") query: String
    ): MediaSearchResponseDto

    @POST("/api/movies")
    suspend fun addMovie(
        @Body request: AddMovieRequestDto
    ): AddMediaResponseDto

    @POST("/api/tv/shows")
    suspend fun addTvShow(
        @Body request: AddTvShowRequestDto
    ): AddMediaResponseDto

    @POST("/api/movies/diary")
    suspend fun addMovieDiary(
        @Body request: AddMovieDiaryRequestDto
    ): ApiResponseDto

    @POST("/api/tv/diary")
    suspend fun addTvDiary(
        @Body request: AddTvDiaryRequestDto
    ): ApiResponseDto

    @GET("/api/media/diary")
    suspend fun getMediaDiary(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("type") type: String = "all",
        @Query("show_id") showId: Int? = null
    ): com.example.dailytrack_mobile.data.remote.dto.MediaDiaryResponseDto

    @GET("/api/movies/stats")
    suspend fun getMovieStats(
        @Query("year") year: String? = null
    ): com.example.dailytrack_mobile.data.remote.dto.MediaStatsResponseDto

    @GET("/api/tv/stats")
    suspend fun getTvStats(
        @Query("year") year: String? = null
    ): com.example.dailytrack_mobile.data.remote.dto.TvStatsResponseDto

    @GET("/api/movies/details/{tmdb_id}")
    suspend fun getMovieDetails(
        @Path("tmdb_id") tmdbId: Int
    ): com.example.dailytrack_mobile.data.remote.dto.MediaDetailsResponseDto

    @GET("/api/tv/details/{tmdb_id}")
    suspend fun getTvDetails(
        @Path("tmdb_id") tmdbId: Int
    ): com.example.dailytrack_mobile.data.remote.dto.MediaDetailsResponseDto

    @PUT("/api/movies/{id}")
    suspend fun updateMovieStatus(
        @Path("id") id: Int,
        @Body request: com.example.dailytrack_mobile.data.remote.dto.UpdateMediaStatusRequestDto
    ): ApiResponseDto

    @PUT("/api/tv/shows/{id}")
    suspend fun updateTvShowStatus(
        @Path("id") id: Int,
        @Body request: com.example.dailytrack_mobile.data.remote.dto.UpdateMediaStatusRequestDto
    ): ApiResponseDto

    @DELETE("/api/movies/{id}")
    suspend fun deleteMovie(
        @Path("id") id: Int
    ): ApiResponseDto

    @DELETE("/api/tv/shows/{id}")
    suspend fun deleteTvShow(
        @Path("id") id: Int
    ): ApiResponseDto

    @PUT("/api/movies/diary")
    suspend fun updateMovieDiary(
        @Body request: com.example.dailytrack_mobile.data.remote.dto.UpdateDiaryLogRequestDto
    ): ApiResponseDto

    @PUT("/api/tv/diary")
    suspend fun updateTvDiary(
        @Body request: com.example.dailytrack_mobile.data.remote.dto.UpdateDiaryLogRequestDto
    ): ApiResponseDto

    @retrofit2.http.HTTP(method = "DELETE", path = "/api/movies/diary", hasBody = true)
    suspend fun deleteMovieDiary(
        @Body request: com.example.dailytrack_mobile.data.remote.dto.DeleteDiaryLogRequestDto
    ): ApiResponseDto

    @retrofit2.http.HTTP(method = "DELETE", path = "/api/tv/diary", hasBody = true)
    suspend fun deleteTvDiary(
        @Body request: com.example.dailytrack_mobile.data.remote.dto.DeleteDiaryLogRequestDto
    ): ApiResponseDto

    @POST("/api/movies/{id}/rematch")
    suspend fun rematchMovie(
        @Path("id") id: Int,
        @Body request: com.example.dailytrack_mobile.data.remote.dto.RematchMediaRequestDto
    ): ApiResponseDto

    @POST("/api/tv/shows/{id}/rematch")
    suspend fun rematchTvShow(
        @Path("id") id: Int,
        @Body request: com.example.dailytrack_mobile.data.remote.dto.RematchMediaRequestDto
    ): ApiResponseDto

    // ── Outbound syncs & reconciliation ──────────────────────────────────────

    @GET("/api/sync/check-transactions")
    suspend fun getPendingSheetSyncCount(): PendingSheetSyncDto

    /** Pushes one batch of unsynced transactions; repeat while `hasMore` is true. */
    @POST("/api/sync/db-to-sheets")
    suspend fun syncTransactionsToSheets(): SheetsSyncResponseDto

    @POST("/api/sync/investments-to-sheets")
    suspend fun syncInvestmentsToSheets(): ApiResponseDto

    /** Runs OCR over screenshots in Drive and writes back verified balances. Slow. */
    @POST("/api/sync/ocr-balances")
    suspend fun reconcileBalancesFromScreenshots(): ApiResponseDto

    /** Streams newline-delimited JSON progress events; the last line is the summary. */
    @Streaming
    @POST("/api/movies/sync/rss")
    suspend fun syncLetterboxdRss(
        @Body request: LetterboxdSyncRequestDto
    ): Response<ResponseBody>

    @GET("/")
    suspend fun checkHealth(): Response<ResponseBody>

    @GET("/test-db")
    suspend fun testDb(): Response<ResponseBody>

    @POST("/api/auth/firebase-login")
    suspend fun firebaseLogin(
        @Body request: com.example.dailytrack_mobile.data.remote.dto.FirebaseLoginRequestDto
    ): Response<com.example.dailytrack_mobile.data.remote.dto.FirebaseLoginResponseDto>

    // ---- Access control (see DT-Web/ACCESS_CONTROL.md) ----
    @GET("/api/auth/me")
    suspend fun getMyAccess(): Response<MeResponseDto>

    @GET("/api/admin/users")
    suspend fun getAccessUsers(): Response<AccessUsersResponseDto>

    @GET("/api/admin/access-options")
    suspend fun getAccessOptions(): Response<AccessOptionsResponseDto>

    @POST("/api/admin/users")
    suspend fun createAccessUser(@Body request: AccessUserRequestDto): Response<AccessUserResponseDto>

    @PUT("/api/admin/users/{email}")
    suspend fun updateAccessUser(
        @Path("email") email: String,
        @Body request: AccessUserRequestDto
    ): Response<AccessUserResponseDto>

    @DELETE("/api/admin/users/{email}")
    suspend fun deleteAccessUser(@Path("email") email: String): Response<AccessUserResponseDto>
}

