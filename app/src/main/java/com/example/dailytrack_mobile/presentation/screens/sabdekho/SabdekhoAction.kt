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

    // Library "More Filters" bottom sheet — year/month/week/language, each "all" clears that facet.
    object ToggleMoreFilters : SabdekhoAction()
    object DismissMoreFilters : SabdekhoAction()
    data class ApplyLibraryFilters(
        val year: String,
        val month: String,
        val week: String,
        val language: String
    ) : SabdekhoAction()
    object ClearLibraryFilters : SabdekhoAction()

    /** A Stats chart bar was tapped: jump to Library with exactly this slice applied. */
    data class FilterLibraryFromStats(
        val year: String = "all",
        val month: String = "all",
        val week: String = "all",
        val language: String = "all",
        val mediaType: String? = null
    ) : SabdekhoAction()

    // Diary Tab Actions
    data class LoadDiary(val type: String = "all", val forceRefresh: Boolean = false) : SabdekhoAction()
    data class ChangeDiaryTypeFilter(val type: String) : SabdekhoAction()

    // Stats Tab Actions
    data class LoadStats(val year: String = java.time.LocalDate.now().year.toString()) : SabdekhoAction()
    data class SelectStatsYear(val year: String) : SabdekhoAction()

    // Media Details & Log Bottom Sheet Actions
    data class OpenMediaDetails(val show: MediaShowDto) : SabdekhoAction()
    object CloseMediaDetails : SabdekhoAction()
    data class SetDetailsSubTab(val tabIndex: Int) : SabdekhoAction()

    // Status & Show Management
    data class UpdateShowStatus(val showId: Int, val isMovie: Boolean, val newStatus: String) : SabdekhoAction()
    data class DeleteShow(val showId: Int, val isMovie: Boolean) : SabdekhoAction()

    // Log Form Actions
    data class UpdateLogForm(
        val rating: Float? = null,
        val review: String? = null,
        val liked: Boolean? = null,
        val rewatch: Boolean? = null,
        val platformTag: String? = null,
        val date: String? = null,
        val season: Int? = null,
        val episode: Int? = null
    ) : SabdekhoAction()
    data class SelectLogSeason(val season: Int?) : SabdekhoAction()
    data class ToggleLogEpisode(val episode: Int) : SabdekhoAction()
    object ToggleAllLogEpisodes : SabdekhoAction()
    object SubmitLog : SabdekhoAction()

    // Edit Log
    data class OpenEditLog(val log: MediaDiaryLogDto) : SabdekhoAction()
    object CloseEditLog : SabdekhoAction()
    data class SubmitEditLog(
        val logId: Int,
        val isMovie: Boolean,
        val rating: Float?,
        val review: String?,
        val liked: Boolean?,
        val rewatch: Boolean?,
        val tag: String?,
        val date: String? = null,
        val season: Int? = null,
        val episode: Int? = null
    ) : SabdekhoAction()
    data class DeleteLog(val logId: Int, val isMovie: Boolean) : SabdekhoAction()

    // Rematch TMDB
    data class SearchRematch(val query: String) : SabdekhoAction()
    data class ApplyRematch(val showId: Int, val isMovie: Boolean, val tmdbResult: MediaSearchResultDto) : SabdekhoAction()
}

