package com.example.dailytrack_mobile.presentation.screens.sabdekho

import com.example.dailytrack_mobile.data.remote.dto.MediaDiaryLogDto
import com.example.dailytrack_mobile.data.remote.dto.MediaSearchResultDto
import com.example.dailytrack_mobile.data.remote.dto.MediaShowDto

sealed class SabdekhoAction {
    // Navigation & Layout
    data class SelectTab(val tab: SabdekhoTab) : SabdekhoAction()
    data class SetGridColumns(val columns: Int) : SabdekhoAction()

    // Library Tab Actions
    data class LoadShows(val status: String = "WATCHING", val type: String = "all") : SabdekhoAction()
    data class SearchQueryChanged(val query: String) : SabdekhoAction()
    data class ChangeFilter(val filter: String) : SabdekhoAction()
    data class ChangeMediaType(val mediaType: String) : SabdekhoAction()
    object Refresh : SabdekhoAction()

    // Library "More Filters" bottom sheet — year/month/week/day/rating/language, each "all" clears that facet.
    object ToggleMoreFilters : SabdekhoAction()
    object DismissMoreFilters : SabdekhoAction()
    data class ApplyLibraryFilters(
        val year: String,
        val month: String,
        val week: String,
        val language: String,
        val weekday: String,
        val rating: String
    ) : SabdekhoAction()
    object ClearLibraryFilters : SabdekhoAction()
    /** One active-filter chip's ✕. */
    data class RemoveLibraryFilter(val facet: LibraryFacet) : SabdekhoAction()

    /** A Stats chart bar was tapped: jump to Library with exactly this slice applied. */
    data class FilterLibraryFromStats(
        val year: String = "all",
        val month: String = "all",
        val week: String = "all",
        val language: String = "all",
        val weekday: String = "all",
        val rating: String = "all",
        val mediaType: String? = null
    ) : SabdekhoAction()

    // Diary Tab Actions
    data class LoadDiary(val type: String = "all", val forceRefresh: Boolean = false) : SabdekhoAction()
    data class ChangeDiaryTypeFilter(val type: String) : SabdekhoAction()
    /** The Diary was scrolled near its end: fetch the next page. */
    object LoadMoreDiary : SabdekhoAction()

    // Stats Tab Actions
    data class LoadStats(val year: String = java.time.LocalDate.now().year.toString()) : SabdekhoAction()
    data class SelectStatsYear(val year: String) : SabdekhoAction()

    // A title's page
    data class OpenMediaDetails(val show: MediaShowDto) : SabdekhoAction()
    object CloseMediaDetails : SabdekhoAction()

    // Status & Show Management
    data class UpdateShowStatus(val showId: Int, val isMovie: Boolean, val newStatus: String) : SabdekhoAction()
    data class DeleteShow(val showId: Int, val isMovie: Boolean) : SabdekhoAction()

    // The log page: a new log of the open title, or a log being changed
    /** A new log; [season] when a season's row was tapped, to start in it. */
    data class OpenLogPage(val season: Int? = null) : SabdekhoAction()
    data class OpenEditLog(val log: MediaDiaryLogDto) : SabdekhoAction()
    object CloseLogPage : SabdekhoAction()
    data class UpdateLogForm(
        val rating: Float? = null,
        val review: String? = null,
        val liked: Boolean? = null,
        val rewatch: Boolean? = null,
        val platformTag: String? = null,
        val date: String? = null
    ) : SabdekhoAction()
    /** A season tapped: picked or unpicked (several can be), or moved to while picking episodes. Null is the whole show. */
    data class SelectLogSeason(val season: Int?) : SabdekhoAction()
    data class ToggleLogEpisode(val episode: Int) : SabdekhoAction()
    object ToggleAllLogEpisodes : SabdekhoAction()
    /** Saves the log page: a new log, or the changes to the one being edited. */
    object SubmitLog : SabdekhoAction()
    data class DeleteLog(val logIds: List<Int>, val isMovie: Boolean) : SabdekhoAction()

    // Fix match
    object OpenFixMatch : SabdekhoAction()
    object CloseFixMatch : SabdekhoAction()
    data class SearchRematch(val query: String) : SabdekhoAction()
    data class ApplyRematch(val showId: Int, val isMovie: Boolean, val tmdbResult: MediaSearchResultDto) : SabdekhoAction()
}
