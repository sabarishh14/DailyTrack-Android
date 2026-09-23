package com.example.dailytrack_mobile.presentation.screens.sabdekho

import com.example.dailytrack_mobile.data.remote.dto.*
import java.time.LocalDate

enum class SabdekhoTab {
    LIBRARY,
    DIARY,
    STATS
}

data class SabdekhoState(
    // Global Tab
    val currentTab: SabdekhoTab = SabdekhoTab.LIBRARY,

    // Library Tab
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val shows: List<MediaShowDto> = emptyList(),
    val error: String? = null,
    val searchQuery: String = "",
    val activeFilter: String = "WATCHING",
    val mediaTypeFilter: String = "all",
    val totalCount: Int = 0,
    val isSearchingOnline: Boolean = false,
    val onlineResults: List<MediaSearchResultDto> = emptyList(),
    val gridColumns: Int = 4,

    // Library "More Filters" — watched-year/month/week (from diary logs, same
    // as what the Stats charts count) and language, on top of status/type.
    val yearFilter: String = "all",
    val monthFilter: String = "all",
    val weekFilter: String = "all",
    val languageFilter: String = "all",
    val showMoreFilters: Boolean = false,
    val filterYears: List<Int> = emptyList(),
    val filterLanguages: List<MediaFilterLanguageDto> = emptyList(),
    val isFilterOptionsLoaded: Boolean = false,

    // Diary Tab
    val isDiaryLoading: Boolean = false,
    val diaryLogs: List<MediaDiaryLogDto> = emptyList(),
    val diaryTypeFilter: String = "all", // "all", "movie", "tv"

    // Stats Tab
    val isStatsLoading: Boolean = false,
    val stats: MediaStatsResponseDto? = null,
    val selectedStatsYear: String = LocalDate.now().year.toString(),
    val isTvStatsLoading: Boolean = false,
    val tvStats: TvStatsResponseDto? = null,
    val tvStatsError: String? = null,

    // Media Details & Log Sheet
    val isDetailsSheetOpen: Boolean = false,
    val selectedShow: MediaShowDto? = null,
    val isLoadingDetails: Boolean = false,
    val detailsData: MediaDetailsDataDto? = null,
    val detailsSheetSubTab: Int = 0, // 0 = Log, 1 = History, 2 = Details, 3 = Match

    // Logging Form State
    val logDate: String = LocalDate.now().toString(),
    val logRating: Float = 0f,
    val logReview: String = "",
    val logLiked: Boolean = false,
    val logRewatch: Boolean = false,
    val logPlatformTag: String = "",
    val logSeason: Int? = 1,
    val logEpisode: Int? = null,
    val selectedLogEpisodes: Set<Int> = emptySet(),
    val isSubmittingLog: Boolean = false,
    val isSavingEditLog: Boolean = false,
    val editLogError: String? = null,

    // Rematch Search State
    val rematchQuery: String = "",
    val isSearchingRematch: Boolean = false,
    val rematchResults: List<MediaSearchResultDto> = emptyList(),

    // Edit Log Modal
    val editingLog: MediaDiaryLogDto? = null,
    val isEditDialogOpen: Boolean = false
) {
    val hasActiveLibraryFilters: Boolean
        get() = yearFilter != "all" || monthFilter != "all" || weekFilter != "all" || languageFilter != "all"
}