package com.example.dailytrack_mobile.presentation.screens.sabdekho

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.local.datastore.DemoModeManager
import com.example.dailytrack_mobile.data.remote.dto.MediaDiaryLogDto
import com.example.dailytrack_mobile.data.remote.dto.MediaSearchResultDto
import com.example.dailytrack_mobile.data.remote.dto.MediaShowDto
import com.example.dailytrack_mobile.data.repository.SabdekhoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SabdekhoVM @Inject constructor(
    private val repository: SabdekhoRepository,
    private val demoModeManager: DemoModeManager
) : ViewModel() {

    private val _state = MutableStateFlow(SabdekhoState())
    val state: StateFlow<SabdekhoState> = _state.asStateFlow()

    private var onlineSearchJob: Job? = null
    private var rematchSearchJob: Job? = null

    init {
        viewModelScope.launch {
            demoModeManager.isDemoModeEnabledFlow.collect {
                loadShows(status = _state.value.activeFilter, type = _state.value.mediaTypeFilter)
                loadDiary(type = _state.value.diaryTypeFilter)
                loadStats(year = _state.value.selectedStatsYear)
            }
        }
        viewModelScope.launch {
            repository.dataUpdateFlow.collect {
                loadShows(status = _state.value.activeFilter, type = _state.value.mediaTypeFilter)
                loadDiary(type = _state.value.diaryTypeFilter)
                loadStats(year = _state.value.selectedStatsYear)
            }
        }
    }

    fun onAction(action: SabdekhoAction) {
        when (action) {
            is SabdekhoAction.SelectTab -> {
                _state.update { it.copy(currentTab = action.tab) }
                when (action.tab) {
                    SabdekhoTab.LIBRARY -> {
                        if (_state.value.shows.isEmpty()) {
                            loadShows(_state.value.activeFilter, _state.value.mediaTypeFilter)
                        }
                    }
                    SabdekhoTab.DIARY -> {
                        if (_state.value.diaryLogs.isEmpty()) {
                            loadDiary(_state.value.diaryTypeFilter)
                        }
                    }
                    SabdekhoTab.STATS -> {
                        if (_state.value.stats == null || _state.value.tvStats == null) {
                            loadStats(_state.value.selectedStatsYear)
                        }
                    }
                }
            }
            is SabdekhoAction.SetGridColumns -> {
                val cols = action.columns.coerceIn(2, 4)
                _state.update { it.copy(gridColumns = cols) }
            }
            is SabdekhoAction.LoadShows -> {
                loadShows(action.status, action.type)
            }
            is SabdekhoAction.SearchQueryChanged -> {
                _state.update { it.copy(searchQuery = action.query) }
                performOnlineSearch(action.query)
            }
            is SabdekhoAction.ChangeFilter -> {
                _state.update { it.copy(activeFilter = action.filter) }
                loadShows(action.filter, _state.value.mediaTypeFilter)
            }
            is SabdekhoAction.ChangeMediaType -> {
                val newType = action.mediaType
                val adjustedFilter = if (newType.equals("movie", ignoreCase = true) && _state.value.activeFilter.equals("DROPPED", ignoreCase = true)) {
                    "WATCHED"
                } else {
                    _state.value.activeFilter
                }
                _state.update {
                    it.copy(
                        mediaTypeFilter = newType,
                        diaryTypeFilter = newType,
                        activeFilter = adjustedFilter
                    )
                }
                loadShows(adjustedFilter, newType)
                loadDiary(newType, forceRefresh = true)
            }
            is SabdekhoAction.Refresh -> {
                loadShows(_state.value.activeFilter, _state.value.mediaTypeFilter, forceRefresh = true)
                loadDiary(_state.value.diaryTypeFilter, forceRefresh = true)
                loadStats(_state.value.selectedStatsYear)
            }

            // Diary Actions
            is SabdekhoAction.LoadDiary -> {
                loadDiary(action.type, action.forceRefresh)
            }
            is SabdekhoAction.ChangeDiaryTypeFilter -> {
                _state.update { it.copy(diaryTypeFilter = action.type) }
                loadDiary(action.type)
            }

            // Stats Actions
            is SabdekhoAction.LoadStats -> {
                loadStats(action.year)
            }
            is SabdekhoAction.SelectStatsYear -> {
                _state.update { it.copy(selectedStatsYear = action.year) }
                loadStats(action.year)
            }

            // Media Details Sheet Actions
            is SabdekhoAction.OpenMediaDetails -> {
                openMediaDetails(action.show)
            }
            is SabdekhoAction.CloseMediaDetails -> {
                _state.update { it.copy(isDetailsSheetOpen = false, selectedShow = null, detailsData = null) }
            }
            is SabdekhoAction.SetDetailsSubTab -> {
                _state.update { it.copy(detailsSheetSubTab = action.tabIndex) }
            }

            // Status & Show Management
            is SabdekhoAction.UpdateShowStatus -> {
                updateShowStatus(action.showId, action.isMovie, action.newStatus)
            }
            is SabdekhoAction.DeleteShow -> {
                deleteShow(action.showId, action.isMovie)
            }

            // Log Form
            is SabdekhoAction.UpdateLogForm -> {
                _state.update { curr ->
                    curr.copy(
                        logRating = action.rating ?: curr.logRating,
                        logReview = action.review ?: curr.logReview,
                        logLiked = action.liked ?: curr.logLiked,
                        logRewatch = action.rewatch ?: curr.logRewatch,
                        logPlatformTag = action.platformTag ?: curr.logPlatformTag,
                        logDate = action.date ?: curr.logDate,
                        logSeason = action.season ?: curr.logSeason,
                        logEpisode = action.episode ?: curr.logEpisode
                    )
                }
            }
            is SabdekhoAction.SelectLogSeason -> {
                _state.update { curr ->
                    curr.copy(
                        logSeason = action.season,
                        logEpisode = null,
                        selectedLogEpisodes = emptySet()
                    )
                }
            }
            is SabdekhoAction.ToggleLogEpisode -> {
                val ep = action.episode
                val showId = _state.value.selectedShow?.id
                val season = _state.value.logSeason
                val isAlreadyWatched = _state.value.diaryLogs.any { log ->
                    log.showId == showId && !log.type.equals("movie", ignoreCase = true) &&
                            log.seasonNumber == season && (log.episodeNumber == ep || log.episodeNumber == null)
                }
                _state.update { curr ->
                    val updatedEps = if (curr.selectedLogEpisodes.contains(ep)) {
                        curr.selectedLogEpisodes - ep
                    } else {
                        curr.selectedLogEpisodes + ep
                    }
                    curr.copy(
                        selectedLogEpisodes = updatedEps,
                        logEpisode = if (updatedEps.size == 1) updatedEps.first() else null,
                        logRewatch = if (isAlreadyWatched) true else curr.logRewatch
                    )
                }
            }
            is SabdekhoAction.ToggleAllLogEpisodes -> {
                val season = _state.value.logSeason
                val seasonData = _state.value.detailsData?.seasons?.find { it.season_number == season }
                val count = seasonData?.episode_count ?: 10
                val allEps = (1..count).toSet()

                _state.update { curr ->
                    val newSet = if (curr.selectedLogEpisodes.size == allEps.size) emptySet() else allEps
                    curr.copy(
                        selectedLogEpisodes = newSet,
                        logEpisode = if (newSet.size == 1) newSet.first() else null
                    )
                }
            }
            is SabdekhoAction.SubmitLog -> {
                submitLog()
            }

            // Edit Log
            is SabdekhoAction.OpenEditLog -> {
                _state.update { it.copy(editingLog = action.log, isEditDialogOpen = true) }
            }
            is SabdekhoAction.CloseEditLog -> {
                _state.update { it.copy(editingLog = null, isEditDialogOpen = false) }
            }
            is SabdekhoAction.SubmitEditLog -> {
                submitEditLog(
                    action.logId,
                    action.isMovie,
                    action.rating,
                    action.review,
                    action.liked,
                    action.rewatch,
                    action.tag,
                    action.date,
                    action.season,
                    action.episode
                )
            }
            is SabdekhoAction.DeleteLog -> {
                deleteLog(action.logId, action.isMovie)
            }

            // Rematch TMDB
            is SabdekhoAction.SearchRematch -> {
                _state.update { it.copy(rematchQuery = action.query) }
                performRematchSearch(action.query)
            }
            is SabdekhoAction.ApplyRematch -> {
                applyRematch(action.showId, action.isMovie, action.tmdbResult)
            }
        }
    }

    private fun performOnlineSearch(query: String) {
        onlineSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _state.update { it.copy(isSearchingOnline = false, onlineResults = emptyList()) }
            return
        }

        onlineSearchJob = viewModelScope.launch {
            delay(350) // Debounce
            _state.update { it.copy(isSearchingOnline = true) }
            val result = repository.searchMedia(trimmed)
            result.onSuccess { list ->
                _state.update { it.copy(isSearchingOnline = false, onlineResults = list) }
            }.onFailure {
                _state.update { it.copy(isSearchingOnline = false, onlineResults = emptyList()) }
            }
        }
    }

    private fun performRematchSearch(query: String) {
        rematchSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _state.update { it.copy(isSearchingRematch = false, rematchResults = emptyList()) }
            return
        }

        rematchSearchJob = viewModelScope.launch {
            delay(350)
            _state.update { it.copy(isSearchingRematch = true) }
            val result = repository.searchMedia(trimmed)
            result.onSuccess { list ->
                _state.update { it.copy(isSearchingRematch = false, rematchResults = list) }
            }.onFailure {
                _state.update { it.copy(isSearchingRematch = false, rematchResults = emptyList()) }
            }
        }
    }

    private fun loadShows(status: String, type: String = "all", forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) {
                repository.clearCache()
                _state.update { it.copy(isRefreshing = true, error = null) }
            } else {
                _state.update { it.copy(isLoading = true, error = null) }
            }
            repository.getMediaLibrary(limit = 100, offset = 0, type = type, status = status, forceRefresh = forceRefresh)
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            shows = response.shows ?: emptyList(),
                            totalCount = response.totalCount ?: (response.shows?.size ?: 0)
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = e.message ?: "Failed to load shows"
                        )
                    }
                }
        }
    }

    private fun loadDiary(type: String = "all", forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isDiaryLoading = true) }
            repository.getMediaDiary(limit = 100, offset = 0, type = type, forceRefresh = forceRefresh)
                .onSuccess { response ->
                    _state.update {
                        it.copy(
                            isDiaryLoading = false,
                            diaryLogs = response.logs ?: emptyList()
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(isDiaryLoading = false) }
                }
        }
    }

    // Movie and TV stats load side by side: the stats tab follows the
    // All / Films / TV switch, and flipping it should show data instantly
    // rather than start a fresh request.
    private fun loadStats(year: String = _state.value.selectedStatsYear) {
        _state.update { it.copy(selectedStatsYear = year) }
        viewModelScope.launch {
            _state.update { it.copy(isStatsLoading = true) }
            repository.getMovieStats(year = year)
                .onSuccess { statsResp ->
                    _state.update {
                        it.copy(
                            isStatsLoading = false,
                            stats = statsResp
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(isStatsLoading = false) }
                }
        }
        viewModelScope.launch {
            _state.update { it.copy(isTvStatsLoading = true, tvStatsError = null) }
            repository.getTvStats(year = year)
                .onSuccess { tvResp ->
                    _state.update { it.copy(isTvStatsLoading = false, tvStats = tvResp) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isTvStatsLoading = false, tvStatsError = error.message ?: "Couldn't load TV stats")
                    }
                }
        }
    }

    private fun openMediaDetails(show: MediaShowDto) {
        val isMovie = show.type.equals("movie", ignoreCase = true)
        _state.update {
            it.copy(
                selectedShow = show,
                isDetailsSheetOpen = true,
                detailsSheetSubTab = 0,
                isLoadingDetails = show.tmdbId != null && show.tmdbId > 0,
                detailsData = null,
                logDate = LocalDate.now().toString(),
                logRating = 0f,
                logReview = "",
                logLiked = false,
                logRewatch = false,
                logPlatformTag = "",
                logSeason = if (!isMovie) 1 else null,
                logEpisode = null,
                selectedLogEpisodes = emptySet(),
                rematchQuery = show.name ?: "",
                rematchResults = emptyList()
            )
        }

        if (show.tmdbId != null && show.tmdbId > 0) {
            viewModelScope.launch {
                repository.getMediaDetails(show.tmdbId, isMovie)
                    .onSuccess { details ->
                        val defaultSeason = if (!isMovie) {
                            details.seasons?.filter { it.season_number > 0 }?.maxByOrNull { it.season_number }?.season_number ?: 1
                        } else null
                        _state.update {
                            it.copy(
                                isLoadingDetails = false,
                                detailsData = details,
                                logSeason = defaultSeason ?: it.logSeason
                            )
                        }
                    }
                    .onFailure {
                        _state.update { it.copy(isLoadingDetails = false) }
                    }
            }
        }
    }

    private fun updateShowStatus(showId: Int, isMovie: Boolean, newStatus: String) {
        viewModelScope.launch {
            repository.updateMediaStatus(showId, isMovie, newStatus)
                .onSuccess {
                    _state.update { curr ->
                        curr.copy(
                            selectedShow = curr.selectedShow?.let { if (it.id == showId) it.copy(status = newStatus) else it },
                            shows = curr.shows.map { if (it.id == showId) it.copy(status = newStatus) else it }
                        )
                    }
                    loadShows(_state.value.activeFilter, _state.value.mediaTypeFilter)
                }
        }
    }

    private fun deleteShow(showId: Int, isMovie: Boolean) {
        viewModelScope.launch {
            repository.deleteMediaShow(showId, isMovie)
                .onSuccess {
                    _state.update { it.copy(isDetailsSheetOpen = false, selectedShow = null) }
                    loadShows(_state.value.activeFilter, _state.value.mediaTypeFilter)
                    loadDiary(_state.value.diaryTypeFilter)
                    loadStats(_state.value.selectedStatsYear)
                }
        }
    }

    private fun submitLog() {
        val show = _state.value.selectedShow ?: return
        val isMovie = show.type.equals("movie", ignoreCase = true)
        val s = _state.value

        viewModelScope.launch {
            _state.update { it.copy(isSubmittingLog = true) }

            if (!isMovie && s.selectedLogEpisodes.size > 1) {
                for (ep in s.selectedLogEpisodes.sorted()) {
                    repository.logDiaryEntry(
                        showId = show.id,
                        isMovie = false,
                        date = s.logDate,
                        rating = s.logRating.takeIf { it > 0f },
                        review = s.logReview.takeIf { it.isNotBlank() },
                        liked = s.logLiked,
                        rewatch = s.logRewatch,
                        tags = s.logPlatformTag.takeIf { it.isNotBlank() },
                        seasonNumber = s.logSeason,
                        episodeNumber = ep
                    )
                }
                _state.update {
                    it.copy(
                        isSubmittingLog = false,
                        detailsSheetSubTab = 1,
                        logRating = 0f,
                        logReview = "",
                        selectedLogEpisodes = emptySet(),
                        logEpisode = null
                    )
                }
                loadDiary(_state.value.diaryTypeFilter)
                loadStats(_state.value.selectedStatsYear)
            } else {
                val epNumber = if (!isMovie) {
                    if (s.selectedLogEpisodes.size == 1) s.selectedLogEpisodes.first() else s.logEpisode
                } else null

                val res = repository.logDiaryEntry(
                    showId = show.id,
                    isMovie = isMovie,
                    date = s.logDate,
                    rating = s.logRating.takeIf { it > 0f },
                    review = s.logReview.takeIf { it.isNotBlank() },
                    liked = s.logLiked,
                    rewatch = s.logRewatch,
                    tags = s.logPlatformTag.takeIf { it.isNotBlank() },
                    seasonNumber = if (!isMovie) s.logSeason else null,
                    episodeNumber = epNumber
                )

                res.onSuccess {
                    _state.update {
                        it.copy(
                            isSubmittingLog = false,
                            detailsSheetSubTab = 1,
                            logRating = 0f,
                            logReview = "",
                            selectedLogEpisodes = emptySet(),
                            logEpisode = null
                        )
                    }
                    loadDiary(_state.value.diaryTypeFilter)
                    loadStats(_state.value.selectedStatsYear)
                }.onFailure {
                    _state.update { it.copy(isSubmittingLog = false) }
                }
            }
        }
    }

    private fun submitEditLog(
        logId: Int,
        isMovie: Boolean,
        rating: Float?,
        review: String?,
        liked: Boolean?,
        rewatch: Boolean?,
        tag: String?,
        date: String? = null,
        season: Int? = null,
        episode: Int? = null
    ) {
        viewModelScope.launch {
            repository.updateDiaryLog(
                logId = logId,
                isMovie = isMovie,
                rating = rating,
                review = review,
                liked = liked,
                rewatch = rewatch,
                tags = tag,
                date = date,
                seasonNumber = season,
                episodeNumber = episode
            ).onSuccess {
                _state.update { it.copy(isEditDialogOpen = false, editingLog = null) }
                loadDiary(_state.value.diaryTypeFilter)
                loadStats(_state.value.selectedStatsYear)
            }
        }
    }

    private fun deleteLog(logId: Int, isMovie: Boolean) {
        viewModelScope.launch {
            repository.deleteDiaryLog(logId, isMovie)
                .onSuccess {
                    _state.update { it.copy(isEditDialogOpen = false, editingLog = null) }
                    loadDiary(_state.value.diaryTypeFilter)
                    loadStats(_state.value.selectedStatsYear)
                }
        }
    }

    private fun applyRematch(showId: Int, isMovie: Boolean, tmdbResult: MediaSearchResultDto) {
        viewModelScope.launch {
            repository.rematchMedia(
                showId = showId,
                isMovie = isMovie,
                tmdbId = tmdbResult.id,
                name = tmdbResult.displayTitle,
                posterPath = tmdbResult.posterPath,
                year = tmdbResult.year
            ).onSuccess {
                val updatedShow = _state.value.selectedShow?.copy(
                    tmdbId = tmdbResult.id,
                    name = tmdbResult.displayTitle,
                    posterPath = tmdbResult.posterPath
                )
                _state.update { it.copy(selectedShow = updatedShow, detailsSheetSubTab = 2) }
                loadShows(_state.value.activeFilter, _state.value.mediaTypeFilter)
                loadDiary(_state.value.diaryTypeFilter)
                if (updatedShow != null) {
                    repository.getMediaDetails(tmdbResult.id, isMovie).onSuccess { details ->
                        _state.update { it.copy(detailsData = details) }
                    }
                }
            }
        }
    }
}

