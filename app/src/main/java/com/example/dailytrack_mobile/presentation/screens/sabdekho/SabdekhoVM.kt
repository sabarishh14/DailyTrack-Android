package com.example.dailytrack_mobile.presentation.screens.sabdekho

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.local.datastore.DemoModeManager
import com.example.dailytrack_mobile.data.remote.dto.MediaDiaryLogDto
import com.example.dailytrack_mobile.data.remote.dto.MediaSearchResultDto
import com.example.dailytrack_mobile.data.remote.dto.MediaShowDto
import com.example.dailytrack_mobile.data.repository.SabdekhoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SabdekhoVM @Inject constructor(
    private val repository: SabdekhoRepository,
    private val demoModeManager: DemoModeManager
) : ViewModel() {

    private val _state = MutableStateFlow(SabdekhoState())
    val state: StateFlow<SabdekhoState> = _state.asStateFlow()

    private var onlineSearchJob: Job? = null
    private var rematchSearchJob: Job? = null

    /** What the open log page has saved so far, so trying again after a failure doesn't save it twice. */
    private val savedThisSession = mutableSetOf<Pair<Int?, Int?>>()

    init {
        viewModelScope.launch {
            demoModeManager.isDemoModeEnabledFlow.collect {
                loadQuickTags()
                reloadShows()
                loadDiary(type = _state.value.diaryTypeFilter)
                loadStats(year = _state.value.selectedStatsYear)
            }
        }
        viewModelScope.launch {
            // Every save announces itself; logging ten episodes is ten saves, and one reload.
            repository.dataUpdateFlow.debounce(300).collect {
                reloadShows()
                loadDiary(type = _state.value.diaryTypeFilter)
                loadStats(year = _state.value.selectedStatsYear)
                if (_state.value.selectedShow != null) loadSelectedShowLogs()
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
                            reloadShows()
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
                val s = _state.value
                loadShows(
                    action.status, action.type, s.yearFilter, s.monthFilter, s.weekFilter, s.languageFilter,
                    s.weekdayFilter, s.ratingFilter
                )
            }
            is SabdekhoAction.SearchQueryChanged -> {
                _state.update { it.copy(searchQuery = action.query) }
                performOnlineSearch(action.query)
            }
            is SabdekhoAction.ChangeFilter -> {
                _state.update { it.copy(activeFilter = action.filter) }
                reloadShows()
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
                reloadShows()
                loadDiary(newType, forceRefresh = true)
            }
            is SabdekhoAction.Refresh -> {
                reloadShows(forceRefresh = true)
                loadDiary(_state.value.diaryTypeFilter, forceRefresh = true)
                loadStats(_state.value.selectedStatsYear)
            }

            // Library "More Filters" bottom sheet
            is SabdekhoAction.ToggleMoreFilters -> {
                _state.update { it.copy(showMoreFilters = !it.showMoreFilters) }
                if (_state.value.showMoreFilters && !_state.value.isFilterOptionsLoaded) {
                    loadFilterOptions()
                }
            }
            is SabdekhoAction.DismissMoreFilters -> {
                _state.update { it.copy(showMoreFilters = false) }
            }
            is SabdekhoAction.ApplyLibraryFilters -> {
                _state.update {
                    it.copy(
                        yearFilter = action.year,
                        monthFilter = action.month,
                        weekFilter = action.week,
                        languageFilter = action.language,
                        weekdayFilter = action.weekday,
                        ratingFilter = action.rating,
                        showMoreFilters = false
                    )
                }
                reloadShows()
            }
            is SabdekhoAction.ClearLibraryFilters -> {
                _state.update {
                    it.copy(
                        yearFilter = "all", monthFilter = "all", weekFilter = "all",
                        languageFilter = "all", weekdayFilter = "all", ratingFilter = "all"
                    )
                }
                reloadShows()
            }
            is SabdekhoAction.RemoveLibraryFilter -> {
                _state.update {
                    when (action.facet) {
                        LibraryFacet.YEAR -> it.copy(yearFilter = "all")
                        LibraryFacet.MONTH -> it.copy(monthFilter = "all")
                        LibraryFacet.WEEK -> it.copy(weekFilter = "all")
                        LibraryFacet.WEEKDAY -> it.copy(weekdayFilter = "all")
                        LibraryFacet.RATING -> it.copy(ratingFilter = "all")
                        LibraryFacet.LANGUAGE -> it.copy(languageFilter = "all")
                    }
                }
                reloadShows()
            }
            is SabdekhoAction.FilterLibraryFromStats -> {
                // The slice shows as chips above the grid; the filter sheet stays shut.
                _state.update {
                    it.copy(
                        currentTab = SabdekhoTab.LIBRARY,
                        yearFilter = action.year,
                        monthFilter = action.month,
                        weekFilter = action.week,
                        languageFilter = action.language,
                        weekdayFilter = action.weekday,
                        ratingFilter = action.rating,
                        mediaTypeFilter = action.mediaType ?: it.mediaTypeFilter,
                        activeFilter = "all", // the tapped slice may hold any status, not just "Watching"
                        searchQuery = "", // a leftover search would hide part of the slice
                        showMoreFilters = false
                    )
                }
                performOnlineSearch("")
                // Language chips show the language's name, which comes with the filter options.
                if (!_state.value.isFilterOptionsLoaded) loadFilterOptions()
                reloadShows()
            }

            // Diary Actions
            is SabdekhoAction.LoadDiary -> {
                loadDiary(action.type, action.forceRefresh)
            }
            is SabdekhoAction.ChangeDiaryTypeFilter -> {
                _state.update { it.copy(diaryTypeFilter = action.type) }
                loadDiary(action.type)
            }
            is SabdekhoAction.LoadMoreDiary -> loadMoreDiary()

            // Stats Actions
            is SabdekhoAction.LoadStats -> {
                loadStats(action.year)
            }
            is SabdekhoAction.SelectStatsYear -> {
                _state.update { it.copy(selectedStatsYear = action.year) }
                loadStats(action.year)
            }

            // A title's page
            is SabdekhoAction.OpenMediaDetails -> {
                selectTitle(action.show)
                _state.update { it.copy(isTitlePageOpen = true) }
            }
            is SabdekhoAction.CloseMediaDetails -> {
                _state.update { it.copy(isTitlePageOpen = false, isFixMatchOpen = false) }
                if (!_state.value.isLogPageOpen) clearTitle()
            }

            // Status & Show Management
            is SabdekhoAction.UpdateShowStatus -> {
                val s = _state.value
                // A show is Watched once all of it is logged, as on the web.
                if (action.newStatus.equals("WATCHED", ignoreCase = true) && !action.isMovie &&
                    s.detailsData != null && !allEpisodesLogged(s.selectedShowLogs, s.detailsData)
                ) {
                    _state.update { it.copy(statusNote = "Log every episode, or the whole show, to mark it watched") }
                    return
                }
                _state.update { it.copy(statusNote = null) }
                updateShowStatus(action.showId, action.isMovie, action.newStatus)
            }
            is SabdekhoAction.DeleteShow -> {
                deleteShow(action.showId, action.isMovie)
            }

            // The log page
            is SabdekhoAction.OpenLogPage -> openLogPage(action.season)
            is SabdekhoAction.OpenEditLog -> openEditLog(action.log)
            is SabdekhoAction.CloseLogPage -> {
                if (_state.value.isSubmittingLog) return
                _state.update { it.copy(isLogPageOpen = false, editingLog = null, logError = null) }
                if (!_state.value.isTitlePageOpen) clearTitle()
            }
            is SabdekhoAction.UpdateLogForm -> {
                _state.update { curr ->
                    curr.copy(
                        logRating = action.rating ?: curr.logRating,
                        logReview = action.review ?: curr.logReview,
                        logLiked = action.liked ?: curr.logLiked,
                        logRewatch = action.rewatch ?: curr.logRewatch,
                        logPlatformTag = action.platformTag ?: curr.logPlatformTag,
                        logDate = action.date ?: curr.logDate,
                        logError = null
                    )
                }
            }
            is SabdekhoAction.SelectLogSeason -> {
                _state.update { curr ->
                    val season = action.season
                    curr.copy(
                        logSeasons = when {
                            season == null -> emptySet()
                            // Picking episodes: another season is where they're picked next.
                            curr.selectedLogEpisodes.isNotEmpty() -> setOf(season)
                            // Whole seasons toggle, so several can be logged at once.
                            season in curr.logSeasons -> curr.logSeasons - season
                            else -> curr.logSeasons + season
                        },
                        selectedLogEpisodes = emptySet(),
                        logError = null
                    )
                }
            }
            is SabdekhoAction.ToggleLogEpisode -> {
                val s = _state.value
                val season = s.logSeason ?: return
                val ep = action.episode
                // An episode logged before (outside the log being edited) makes this a rewatch.
                val editingIds = s.editingLog?.allLogIds.orEmpty().toSet()
                val seenBefore = WatchedEpisodes(s.selectedShowLogs.filterNot { it.id in editingIds }).isWatched(season, ep)
                _state.update { curr ->
                    val adding = ep !in curr.selectedLogEpisodes
                    curr.copy(
                        selectedLogEpisodes = if (adding) curr.selectedLogEpisodes + ep else curr.selectedLogEpisodes - ep,
                        logRewatch = curr.logRewatch || (adding && seenBefore),
                        logError = null
                    )
                }
            }
            is SabdekhoAction.ToggleAllLogEpisodes -> {
                val s = _state.value
                val season = s.logSeason ?: return
                val count = seasonsOf(s.detailsData, s.selectedShowLogs, s.logSeasons, s.selectedLogEpisodes)
                    .firstOrNull { it.number == season }?.episodeCount ?: return
                val all = (1..count).toSet()
                _state.update {
                    it.copy(selectedLogEpisodes = if (it.selectedLogEpisodes.containsAll(all)) emptySet() else all, logError = null)
                }
            }
            is SabdekhoAction.SubmitLog -> submitLog()
            is SabdekhoAction.DeleteLog -> deleteLog(action.logIds, action.isMovie)

            // Fix match
            is SabdekhoAction.OpenFixMatch -> {
                val name = _state.value.selectedShow?.name.orEmpty()
                _state.update {
                    it.copy(isFixMatchOpen = true, rematchQuery = name, rematchResults = emptyList(), rematchError = null)
                }
                performRematchSearch(name)
            }
            is SabdekhoAction.CloseFixMatch -> {
                if (_state.value.isApplyingRematch) return
                _state.update { it.copy(isFixMatchOpen = false, rematchError = null) }
            }
            is SabdekhoAction.SearchRematch -> {
                _state.update { it.copy(rematchQuery = action.query, rematchError = null) }
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

        // Busy from the first keystroke, so "Nothing found" never flashes while typing.
        _state.update { it.copy(isSearchingRematch = true) }
        rematchSearchJob = viewModelScope.launch {
            delay(350)
            val result = repository.searchMedia(trimmed)
            result.onSuccess { list ->
                // A film is re-matched to a film, a show to a show.
                val wantMovie = _state.value.selectedShow?.type.equals("movie", ignoreCase = true)
                val wanted = list.filter { if (wantMovie) it.isMovie else it.mediaType.equals("tv", ignoreCase = true) }
                _state.update { it.copy(isSearchingRematch = false, rematchResults = wanted) }
            }.onFailure {
                _state.update { it.copy(isSearchingRematch = false, rematchResults = emptyList()) }
            }
        }
    }

    private fun loadShows(
        status: String,
        type: String = "all",
        year: String = "all",
        month: String = "all",
        week: String = "all",
        language: String = "all",
        weekday: String = "all",
        rating: String = "all",
        forceRefresh: Boolean = false
    ) {
        viewModelScope.launch {
            if (forceRefresh) {
                repository.clearCache()
                _state.update { it.copy(isRefreshing = true, error = null) }
            } else {
                _state.update { it.copy(isLoading = true, error = null) }
            }
            // Every page, so nothing past the first one goes missing and the
            // title search on top of it sees the whole library.
            runCatching {
                val all = mutableListOf<MediaShowDto>()
                var total = 0
                do {
                    val page = repository.getMediaLibrary(
                        limit = LIBRARY_PAGE, offset = all.size, type = type, status = status,
                        year = year, month = month, week = week, language = language,
                        weekday = weekday, rating = rating,
                        forceRefresh = forceRefresh
                    ).getOrThrow()
                    val shows = page.shows.orEmpty()
                    all += shows
                    total = page.totalCount ?: all.size
                } while (shows.size == LIBRARY_PAGE && all.size < total)
                all.distinctBy { "${it.type}-${it.id}" } to total
            }
                .onSuccess { (shows, total) ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            shows = shows,
                            totalCount = total
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

    /** Reissues the Library fetch using whatever filters are currently in state. */
    private fun reloadShows(forceRefresh: Boolean = false) {
        val s = _state.value
        loadShows(
            status = s.activeFilter,
            type = s.mediaTypeFilter,
            year = s.yearFilter,
            month = s.monthFilter,
            week = s.weekFilter,
            language = s.languageFilter,
            weekday = s.weekdayFilter,
            rating = s.ratingFilter,
            forceRefresh = forceRefresh
        )
    }

    private fun loadQuickTags() {
        viewModelScope.launch {
            repository.quickTags().onSuccess { tags -> _state.update { it.copy(quickTags = tags) } }
        }
    }

    private fun loadFilterOptions() {
        viewModelScope.launch {
            repository.getMediaFilters().onSuccess { response ->
                _state.update {
                    it.copy(
                        filterYears = response.years,
                        filterLanguages = response.languages,
                        isFilterOptionsLoaded = true
                    )
                }
            }
        }
    }

    private var diaryJob: Job? = null

    /** The Diary's first page; [loadMoreDiary] brings the rest as it's scrolled. */
    private fun loadDiary(type: String = "all", forceRefresh: Boolean = false) {
        diaryJob?.cancel()
        diaryJob = viewModelScope.launch {
            _state.update { it.copy(isDiaryLoading = true, isDiaryLoadingMore = false) }
            repository.getMediaDiary(limit = DIARY_PAGE, offset = 0, type = type, forceRefresh = forceRefresh)
                .onSuccess { response ->
                    val logs = response.logs ?: emptyList()
                    _state.update {
                        it.copy(
                            isDiaryLoading = false,
                            diaryLogs = logs,
                            diaryHasMore = hasMore(logs.size, response.totalCount)
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(isDiaryLoading = false) }
                }
        }
    }

    private fun loadMoreDiary() {
        val s = _state.value
        if (!s.diaryHasMore || s.isDiaryLoading || s.isDiaryLoadingMore) return
        val type = s.diaryTypeFilter
        diaryJob = viewModelScope.launch {
            _state.update { it.copy(isDiaryLoadingMore = true) }
            repository.getMediaDiary(limit = DIARY_PAGE, offset = s.diaryLogs.size, type = type)
                .onSuccess { response ->
                    val page = response.logs ?: emptyList()
                    _state.update {
                        // A filter switched meanwhile: this page is for the old one.
                        if (it.diaryTypeFilter != type) return@update it.copy(isDiaryLoadingMore = false)
                        val logs = (it.diaryLogs + page).distinctBy { log -> "${log.type}-${log.id}" }
                        it.copy(
                            isDiaryLoadingMore = false,
                            diaryLogs = logs,
                            diaryHasMore = page.size == DIARY_PAGE && hasMore(logs.size, response.totalCount)
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(isDiaryLoadingMore = false) }
                }
        }
    }

    private fun hasMore(loaded: Int, total: Int?): Boolean = if (total != null) loaded < total else loaded >= DIARY_PAGE

    /** Every log of the open title, for its History, the watched marks and its status. */
    private suspend fun loadSelectedShowLogs(): List<MediaDiaryLogDto> {
        val show = _state.value.selectedShow ?: return emptyList()
        val isMovie = show.type.equals("movie", ignoreCase = true)
        // Offline for a moment: keep what's showing rather than blank it.
        val response = repository.getMediaDiary(limit = 5000, offset = 0, type = if (isMovie) "movie" else "tv", showId = show.id)
            .getOrNull() ?: return _state.value.selectedShowLogs
        val logs = response.logs.orEmpty()
            .filter { it.showId == show.id && it.type.equals("movie", ignoreCase = true) == isMovie }
        _state.update { if (isSameTitle(it.selectedShow, show)) it.copy(selectedShowLogs = logs) else it }
        return logs
    }

    private companion object {
        const val LIBRARY_PAGE = 200
        const val DIARY_PAGE = 100
    }

    // Movie and TV stats load side by side: the stats tab follows the
    // All / Films / TV switch, and flipping it should show data instantly
    // rather than start a fresh request.
    private fun loadStats(year: String = _state.value.selectedStatsYear) {
        _state.update { it.copy(selectedStatsYear = year) }
        viewModelScope.launch {
            _state.update { it.copy(isStatsLoading = true, statsError = null) }
            repository.getMovieStats(year = year)
                .onSuccess { statsResp ->
                    _state.update {
                        it.copy(
                            isStatsLoading = false,
                            stats = statsResp
                        )
                    }
                }
                .onFailure { error ->
                    // Logged so a payload the DTOs can't parse is findable in logcat.
                    Log.w("SabdekhoVM", "Film stats failed to load", error)
                    _state.update {
                        it.copy(isStatsLoading = false, statsError = error.message ?: "Couldn't load film stats")
                    }
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

    private fun isSameTitle(a: MediaShowDto?, b: MediaShowDto): Boolean =
        a != null && a.id == b.id && a.type.equals("movie", ignoreCase = true) == b.type.equals("movie", ignoreCase = true)

    /** Makes [show] the open title and loads its TMDB details and all its logs. */
    private fun selectTitle(show: MediaShowDto) {
        val isMovie = show.type.equals("movie", ignoreCase = true)
        val tmdbId = show.tmdbId?.takeIf { it > 0 }
        _state.update {
            it.copy(
                selectedShow = show,
                isLoadingDetails = tmdbId != null,
                detailsData = null,
                selectedShowLogs = emptyList(),
                statusNote = null,
                isFixMatchOpen = false,
                rematchQuery = show.name ?: "",
                rematchResults = emptyList(),
                rematchError = null
            )
        }
        viewModelScope.launch { loadSelectedShowLogs() }
        if (tmdbId != null) {
            viewModelScope.launch {
                repository.getMediaDetails(tmdbId, isMovie)
                    .onSuccess { details ->
                        _state.update {
                            if (isSameTitle(it.selectedShow, show)) it.copy(isLoadingDetails = false, detailsData = details) else it
                        }
                    }
                    .onFailure {
                        _state.update { it.copy(isLoadingDetails = false) }
                    }
            }
        }
    }

    /** Nothing open any more: the title goes too. */
    private fun clearTitle() {
        _state.update {
            it.copy(
                selectedShow = null,
                detailsData = null,
                isLoadingDetails = false,
                selectedShowLogs = emptyList(),
                statusNote = null,
                isFixMatchOpen = false
            )
        }
    }

    /**
     * A new log of the open title. A show starts on the first episode not
     * logged yet (in [season], when a season's row was tapped); with all of
     * it logged, it starts as a rewatch.
     */
    private fun openLogPage(season: Int? = null) {
        val s = _state.value
        val show = s.selectedShow ?: return
        val isMovie = show.type.equals("movie", ignoreCase = true)
        var seasons = emptySet<Int>()
        var episodes = emptySet<Int>()
        var rewatch = isMovie && s.selectedShowLogs.isNotEmpty()
        if (!isMovie) {
            val watched = WatchedEpisodes(s.selectedShowLogs)
            val next = nextUp(watched, seasonsOf(s.detailsData, s.selectedShowLogs, emptySet(), emptySet()), season)
            when {
                next != null -> {
                    seasons = setOf(next.first)
                    episodes = setOf(next.second)
                }
                season != null -> {
                    seasons = setOf(season)
                    rewatch = true
                }
                s.selectedShowLogs.isNotEmpty() -> rewatch = true
            }
        }
        savedThisSession.clear()
        _state.update {
            it.copy(
                isLogPageOpen = true,
                editingLog = null,
                logError = null,
                isSubmittingLog = false,
                logDate = LocalDate.now().toString(),
                logRating = 0f,
                logReview = "",
                logLiked = false,
                logRewatch = rewatch,
                logPlatformTag = "",
                logSeasons = seasons,
                selectedLogEpisodes = episodes
            )
        }
    }

    /** A logged row, ready to change: its date, what it covers, rating, review and tags. */
    private fun openEditLog(log: MediaDiaryLogDto) {
        val isMovie = log.type.equals("movie", ignoreCase = true)
        val s = _state.value
        val fromDiary = MediaShowDto(
            id = log.showId,
            tmdbId = log.tmdbId,
            name = log.showName,
            posterPath = log.posterPath,
            type = log.type
        )
        if (!isSameTitle(s.selectedShow, fromDiary)) {
            // Straight from the Diary: its show's episodes and logs are needed to change them.
            selectTitle(s.shows.firstOrNull { isSameTitle(it, fromDiary) } ?: fromDiary)
        }
        val season = log.seasonNumber
        savedThisSession.clear()
        _state.update {
            it.copy(
                isLogPageOpen = true,
                editingLog = log,
                logError = null,
                isSubmittingLog = false,
                logDate = log.date?.take(10)?.takeIf { d -> d.length == 10 } ?: LocalDate.now().toString(),
                logRating = log.rating ?: 0f,
                logReview = log.review.orEmpty(),
                logLiked = log.liked,
                logRewatch = log.rewatch,
                logPlatformTag = log.tags.orEmpty(),
                logSeasons = if (isMovie || season == null) emptySet() else setOf(season),
                selectedLogEpisodes = when {
                    isMovie || season == null -> emptySet()
                    log.episodes.isNotEmpty() -> log.episodes.toSet()
                    else -> setOfNotNull(log.episodeNumber)
                }
            )
        }
    }

    private fun updateShowStatus(showId: Int, isMovie: Boolean, newStatus: String) {
        viewModelScope.launch {
            repository.updateMediaStatus(showId, isMovie, newStatus)
                .onSuccess {
                    fun same(item: MediaShowDto) = item.id == showId && item.type.equals("movie", ignoreCase = true) == isMovie
                    _state.update { curr ->
                        curr.copy(
                            selectedShow = curr.selectedShow?.let { if (same(it)) it.copy(status = newStatus) else it },
                            shows = curr.shows.map { if (same(it)) it.copy(status = newStatus) else it }
                        )
                    }
                    reloadShows()
                }
        }
    }

    private fun deleteShow(showId: Int, isMovie: Boolean) {
        viewModelScope.launch {
            repository.deleteMediaShow(showId, isMovie)
                .onSuccess {
                    _state.update { it.copy(isTitlePageOpen = false, isLogPageOpen = false, editingLog = null) }
                    clearTitle()
                    reloadShows()
                    loadDiary(_state.value.diaryTypeFilter)
                    loadStats(_state.value.selectedStatsYear)
                }
        }
    }

    /** What a save writes on every log it touches. */
    private data class LogFields(
        val date: String,
        val rating: Float?,
        val review: String?,
        val liked: Boolean,
        val rewatch: Boolean,
        val tags: String?
    )

    private fun submitLog() {
        val s = _state.value
        if (s.isSubmittingLog) return  // a second tap must not save twice
        val show = s.selectedShow ?: return
        val isMovie = show.type.equals("movie", ignoreCase = true)
        val editing = s.editingLog
        val entries = logEntries(isMovie, s.logSeasons, s.selectedLogEpisodes)
        val fields = LogFields(
            date = s.logDate,
            rating = s.logRating.takeIf { it > 0f },
            review = s.logReview.trim().takeIf { it.isNotEmpty() },
            liked = s.logLiked,
            rewatch = s.logRewatch,
            tags = s.logPlatformTag.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                .joinToString(", ").takeIf { it.isNotEmpty() }
        )

        viewModelScope.launch {
            _state.update { it.copy(isSubmittingLog = true, logError = null) }
            val error = if (editing == null) {
                addLogs(show.id, isMovie, entries, fields)
            } else {
                saveEdit(editing, isMovie, entries, fields)
            }
            if (error != null) {
                // The page stays open with everything as it was, to try again.
                _state.update { it.copy(isSubmittingLog = false, logError = error) }
                return@launch
            }
            _state.update { it.copy(isSubmittingLog = false, isLogPageOpen = false, editingLog = null, logError = null) }
            loadQuickTags() // a new tag may now be one of the most used
            val logs = loadSelectedShowLogs()
            // As on the web: a logged film is Watched, and so is a show once all of it is logged.
            val current = _state.value.selectedShow
            if (current != null && isSameTitle(current, show) && current.status != null &&
                !current.status.equals("WATCHED", ignoreCase = true) &&
                (isMovie || allEpisodesLogged(logs, _state.value.detailsData))
            ) {
                updateShowStatus(show.id, isMovie, "WATCHED")
            }
            if (!_state.value.isTitlePageOpen) clearTitle()
        }
    }

    /** Saves each entry not saved yet; an error message when one fails. */
    private suspend fun addLogs(showId: Int, isMovie: Boolean, entries: List<Pair<Int?, Int?>>, f: LogFields): String? {
        for (entry in entries) {
            if (entry in savedThisSession) continue
            val saved = repository.logDiaryEntry(
                showId = showId,
                isMovie = isMovie,
                date = f.date,
                rating = f.rating,
                review = f.review,
                liked = f.liked,
                rewatch = f.rewatch,
                tags = f.tags,
                seasonNumber = entry.first,
                episodeNumber = entry.second
            ).isSuccess
            if (!saved) {
                return if (savedThisSession.isEmpty()) "Couldn't save. Try again." else "Some of it didn't save. Try again for the rest."
            }
            savedThisSession += entry
        }
        return null
    }

    /**
     * Saves changes to a logged row. A film's log is updated in place. A show's
     * keeps the logs of episodes still picked (updated), adds newly picked ones
     * and removes the ones unpicked, in that order, so a failure loses nothing.
     */
    private suspend fun saveEdit(log: MediaDiaryLogDto, isMovie: Boolean, entries: List<Pair<Int?, Int?>>, f: LogFields): String? {
        if (isMovie) return updateFields(log.allLogIds, isMovie = true, f = f)
        val original = log.entryIds()
        val keepIds = entries.filter { it in original }.flatMap { original.getValue(it) }
        val removeIds = original.filterKeys { it !in entries }.values.flatten()
        if (keepIds.isNotEmpty()) {
            updateFields(keepIds, isMovie = false, f = f)?.let { return it }
        }
        addLogs(log.showId, isMovie = false, entries = entries.filter { it !in original }, f = f)?.let { return it }
        if (removeIds.isNotEmpty()) {
            repository.deleteDiaryLog(removeIds, isMovie = false)
                .exceptionOrNull()?.let { return it.message ?: "Couldn't save changes" }
        }
        return null
    }

    /** Writes [f] onto existing logs; an error message when it fails. */
    private suspend fun updateFields(ids: List<Int>, isMovie: Boolean, f: LogFields): String? =
        repository.updateDiaryLog(
            logIds = ids,
            isMovie = isMovie,
            rating = f.rating,
            review = f.review,
            liked = f.liked,
            rewatch = f.rewatch,
            tags = f.tags,
            date = f.date
        ).exceptionOrNull()?.let { it.message ?: "Couldn't save changes" }

    private fun deleteLog(logIds: List<Int>, isMovie: Boolean) {
        val fromLogPage = _state.value.isLogPageOpen
        viewModelScope.launch {
            if (fromLogPage) _state.update { it.copy(isSubmittingLog = true, logError = null) }
            repository.deleteDiaryLog(logIds, isMovie)
                .onSuccess {
                    if (fromLogPage) {
                        _state.update { it.copy(isSubmittingLog = false, isLogPageOpen = false, editingLog = null) }
                        if (!_state.value.isTitlePageOpen) clearTitle()
                    }
                }
                .onFailure { e ->
                    if (fromLogPage) {
                        _state.update { it.copy(isSubmittingLog = false, logError = e.message ?: "Couldn't delete. Try again.") }
                    }
                }
        }
    }

    private fun applyRematch(showId: Int, isMovie: Boolean, tmdbResult: MediaSearchResultDto) {
        if (_state.value.isApplyingRematch) return
        viewModelScope.launch {
            _state.update { it.copy(isApplyingRematch = true, rematchError = null) }
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
                _state.update {
                    it.copy(
                        selectedShow = updatedShow,
                        isApplyingRematch = false,
                        isFixMatchOpen = false,
                        detailsData = null,
                        isLoadingDetails = updatedShow != null
                    )
                }
                reloadShows()
                loadDiary(_state.value.diaryTypeFilter)
                if (updatedShow != null) {
                    repository.getMediaDetails(tmdbResult.id, isMovie)
                        .onSuccess { details -> _state.update { it.copy(detailsData = details, isLoadingDetails = false) } }
                        .onFailure { _state.update { it.copy(isLoadingDetails = false) } }
                }
            }.onFailure { e ->
                _state.update { it.copy(isApplyingRematch = false, rematchError = e.message ?: "Couldn't re-match. Try again.") }
            }
        }
    }
}

