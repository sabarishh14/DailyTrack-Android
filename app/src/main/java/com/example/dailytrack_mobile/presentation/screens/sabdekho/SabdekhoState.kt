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
    // From the Stats "By day" and "Ratings" bars: a weekday (0 = Monday) and a log's rating.
    val weekdayFilter: String = "all",
    val ratingFilter: String = "all",
    val showMoreFilters: Boolean = false,
    val filterYears: List<Int> = emptyList(),
    val filterLanguages: List<MediaFilterLanguageDto> = emptyList(),
    val isFilterOptionsLoaded: Boolean = false,

    // Diary Tab — a page at a time, more as it's scrolled
    val isDiaryLoading: Boolean = false,
    val diaryLogs: List<MediaDiaryLogDto> = emptyList(),
    val diaryTypeFilter: String = "all", // "all", "movie", "tv"
    val diaryHasMore: Boolean = false,
    val isDiaryLoadingMore: Boolean = false,

    // Stats Tab
    val isStatsLoading: Boolean = false,
    val stats: MediaStatsResponseDto? = null,
    val statsError: String? = null,
    val selectedStatsYear: String = LocalDate.now().year.toString(),
    val isTvStatsLoading: Boolean = false,
    val tvStats: TvStatsResponseDto? = null,
    val tvStatsError: String? = null,

    // A title's page. The title stays selected while its log page is open,
    // even when that was opened straight from the Diary.
    val isTitlePageOpen: Boolean = false,
    val selectedShow: MediaShowDto? = null,
    val isLoadingDetails: Boolean = false,
    val detailsData: MediaDetailsDataDto? = null,
    /** Every log of the open title, fetched for it alone (the Diary tab only holds pages). */
    val selectedShowLogs: List<MediaDiaryLogDto> = emptyList(),
    /** Why a status didn't change (a show is Watched only once all of it is logged). */
    val statusNote: String? = null,

    // The log page: a new log, or [editingLog] being changed
    val isLogPageOpen: Boolean = false,
    val editingLog: MediaDiaryLogDto? = null,
    val logDate: String = LocalDate.now().toString(),
    val logRating: Float = 0f,
    val logReview: String = "",
    val logLiked: Boolean = false,
    val logRewatch: Boolean = false,
    val logPlatformTag: String = "",
    /** One-tap tags in the log forms: the ones most used before, most used first. */
    val quickTags: List<String> = emptyList(),
    /** Seasons picked to log; none is the whole show, several log each season whole. */
    val logSeasons: Set<Int> = emptySet(),
    /** One season's picked episodes; none logs that season whole. */
    val selectedLogEpisodes: Set<Int> = emptySet(),
    val isSubmittingLog: Boolean = false,
    val logError: String? = null,

    // Fix match
    val isFixMatchOpen: Boolean = false,
    val rematchQuery: String = "",
    val isSearchingRematch: Boolean = false,
    val rematchResults: List<MediaSearchResultDto> = emptyList(),
    val isApplyingRematch: Boolean = false,
    val rematchError: String? = null
) {
    val activeLibraryFilterCount: Int
        get() = listOf(yearFilter, monthFilter, weekFilter, languageFilter, weekdayFilter, ratingFilter).count { it != "all" }

    val hasActiveLibraryFilters: Boolean
        get() = activeLibraryFilterCount > 0

    /** The one season picked, when it's just one: its episodes can be picked too. */
    val logSeason: Int? get() = logSeasons.singleOrNull()
}

/** Whether the comma-separated [text] already holds [tag], as a whole tag. */
internal fun hasTag(text: String, tag: String): Boolean =
    text.split(',').any { it.trim().equals(tag, ignoreCase = true) }

/** [text] with [tag] added, or taken out if it's already there. */
internal fun toggleTag(text: String, tag: String): String {
    val tags = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    return (if (hasTag(text, tag)) tags.filterNot { it.equals(tag, ignoreCase = true) } else tags + tag)
        .joinToString(", ")
}

/** [text] with each comma-separated tag typed in [typed] added, unless it's there already. */
internal fun addTags(text: String, typed: String): String =
    typed.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        .fold(text) { acc, tag -> if (hasTag(acc, tag)) acc else toggleTag(acc, tag) }

/**
 * Whether [logs] cover every episode of a show: a whole-show log, or whole
 * seasons and single episodes adding up to all of them. False without
 * [details] to count against.
 */
internal fun allEpisodesLogged(logs: List<MediaDiaryLogDto>, details: MediaDetailsDataDto?): Boolean {
    if (logs.any { it.seasonNumber == null }) return true
    val seasons = details?.seasons?.filter { it.season_number > 0 && it.episode_count > 0 } ?: return false
    if (seasons.isEmpty()) return false
    val wholeSeasons = logs.filter { it.episodeNumber == null }.mapNotNull { it.seasonNumber }.toSet()
    return seasons.all { s ->
        s.season_number in wholeSeasons ||
            logs.filter { it.seasonNumber == s.season_number }.mapNotNull { it.episodeNumber }.toSet().size >= s.episode_count
    }
}
