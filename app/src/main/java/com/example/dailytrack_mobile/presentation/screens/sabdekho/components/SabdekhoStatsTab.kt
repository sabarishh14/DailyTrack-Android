package com.example.dailytrack_mobile.presentation.screens.sabdekho.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dailytrack_mobile.data.remote.dto.*
import com.example.dailytrack_mobile.presentation.components.LocalFloatingBarClearance
import com.example.dailytrack_mobile.presentation.screens.sabdekho.SabdekhoAction
import com.example.dailytrack_mobile.presentation.screens.sabdekho.SabdekhoState
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Stats tab — a year-in-review in the spirit of Letterboxd and the web Stats
// page. Follows the All / Films / TV Shows switch above it: film stats, TV
// stats, or both under one year hero. Films use the theme's primary colour and
// TV its tertiary, so every theme keeps its own look.
// ─────────────────────────────────────────────────────────────────────────────

private enum class StatsMode { MOVIES, TV, BOTH }

private val SectionGap = 28.dp
private val PosterWidth = 100.dp
private val TileShape = RoundedCornerShape(16.dp)

private val MonthShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val MonthLong = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)
private val WeekdayShort = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val WeekdayLong = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val RatingKeys = listOf("0.5", "1.0", "1.5", "2.0", "2.5", "3.0", "3.5", "4.0", "4.5", "5.0")

@Composable
fun SabdekhoStatsTab(
    state: SabdekhoState,
    onAction: (SabdekhoAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val mode = when (state.mediaTypeFilter.lowercase()) {
        "movie" -> StatsMode.MOVIES
        "tv" -> StatsMode.TV
        else -> StatsMode.BOTH
    }
    val showMovies = mode != StatsMode.TV
    val showTv = mode != StatsMode.MOVIES
    val stats = state.stats
    val tvStats = state.tvStats
    val selectedYear = state.selectedStatsYear

    val waitingForMovies = showMovies && stats == null && state.isStatsLoading
    val waitingForTv = showTv && tvStats == null && state.isTvStatsLoading
    if (waitingForMovies || waitingForTv) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Loading your stats…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val retry = { onAction(SabdekhoAction.LoadStats(selectedYear)) }
    val nothingToShow = (!showMovies || stats == null) && (!showTv || tvStats == null)
    if (nothingToShow) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyStatsCard(
                icon = Icons.Default.BarChart,
                message = "No stats available",
                detail = (if (showMovies) state.statsError else null) ?: (if (showTv) state.tvStatsError else null),
                onRetry = retry
            )
        }
        return
    }

    val currentYear = LocalDate.now().year.toString()
    val isCurrentOrAll = selectedYear == "all" || selectedYear == currentYear
    val years = remember(stats?.available_years, tvStats?.available_years, showMovies, showTv) {
        val found = buildSet {
            if (showMovies) stats?.available_years?.forEach { add(it.toString()) }
            if (showTv) tvStats?.available_years?.forEach { add(it.toString()) }
            add(currentYear)
        }
        listOf("all") + found.sortedDescending()
    }
    val refreshing = (showMovies && state.isStatsLoading) || (showTv && state.isTvStatsLoading)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
        contentPadding = PaddingValues(bottom = Dimens.current.screenBottomPadding + LocalFloatingBarClearance.current)
    ) {
        item(key = "hero") {
            StatsHero(
                year = selectedYear,
                subtitle = heroSubtitle(selectedYear, mode),
                years = years,
                refreshing = refreshing,
                onSelectYear = { onAction(SabdekhoAction.SelectStatsYear(it)) }
            )
        }

        if (showMovies) {
            if (mode == StatsMode.BOTH) {
                item(key = "divider-movies") {
                    MediaDivider(Icons.Default.Movie, "Films", MaterialTheme.colorScheme.primary)
                }
            }
            if (stats != null) {
                movieStatsItems(stats, selectedYear, onAction)
            } else {
                item(key = "movies-error") {
                    EmptyStatsCard(Icons.Default.Movie, "Couldn't load film stats", state.statsError, retry)
                }
            }
        }

        if (showTv) {
            if (mode == StatsMode.BOTH) {
                item(key = "divider-tv") {
                    MediaDivider(Icons.Default.Tv, "TV Shows", MaterialTheme.colorScheme.tertiary)
                }
            }
            if (tvStats != null) {
                tvStatsItems(tvStats, selectedYear, isCurrentOrAll, onAction)
            } else {
                item(key = "tv-error") {
                    EmptyStatsCard(Icons.Default.Tv, "Couldn't load TV stats", state.tvStatsError, retry)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Films
// ─────────────────────────────────────────────────────────────────────────────

private fun LazyListScope.movieStatsItems(
    stats: MediaStatsResponseDto,
    selectedYear: String,
    onAction: (SabdekhoAction) -> Unit
) {
    val year = stats.year ?: selectedYear
    val openMovie: (Int, Int?, String?, String?) -> Unit = { id, tmdbId, name, poster ->
        onAction(
            SabdekhoAction.OpenMediaDetails(
                MediaShowDto(id = id, tmdbId = tmdbId, name = name, posterPath = poster, type = "movie", status = "WATCHED")
            )
        )
    }
    val filterLibrary: (month: String, week: String, language: String, inYear: String) -> Unit =
        { month, week, language, inYear ->
            onAction(
                SabdekhoAction.FilterLibraryFromStats(
                    year = inYear, month = month, week = week, language = language, mediaType = "movie"
                )
            )
        }

    if (stats.films_logged == 0) {
        item(key = "movie-empty") {
            EmptyStatsCard(
                Icons.Default.Movie,
                if (selectedYear == "all") "No films logged yet" else "No films logged in $selectedYear"
            )
        }
        return
    }

    item(key = "movie-headline") {
        HeadlineGrid(
            listOf(
                Headline(stats.films_logged.toDouble(), if (stats.films_logged == 1) "Film" else "Films"),
                Headline(stats.total_hours, "Hours", decimals = if (stats.total_hours % 1.0 == 0.0) 0 else 1),
                Headline(stats.total_likes.toDouble(), if (stats.total_likes == 1) "Like" else "Likes"),
                Headline(stats.total_reviews.toDouble(), if (stats.total_reviews == 1) "Review" else "Reviews")
            )
        )
    }

    // ── Highest rated — split into this year's releases and older ones when a
    // year is picked, like the web page.
    if (stats.highest_rated.isNotEmpty()) {
        item(key = "movie-highest-rated") {
            val splitByRelease = selectedYear != "all"
            var showOlder by remember(selectedYear, stats) {
                mutableStateOf(stats.highest_rated_current.isEmpty() && stats.highest_rated_older.isNotEmpty())
            }
            val films = when {
                !splitByRelease -> stats.highest_rated
                showOlder -> stats.highest_rated_older
                else -> stats.highest_rated_current
            }
            Column {
                SectionHeader("Highest rated") {
                    if (splitByRelease) {
                        SegmentToggle(
                            options = listOf("$selectedYear releases", "Older"),
                            selected = if (showOlder) 1 else 0,
                            onSelect = { showOlder = it == 1 }
                        )
                    }
                }
                if (films.isEmpty()) {
                    QuietNote(if (showOlder) "No older films rated yet" else "No $selectedYear releases rated yet")
                } else {
                    PosterRail(films.map { m ->
                        PosterItem(
                            key = m.movie_id,
                            title = m.name ?: "Unknown",
                            posterPath = m.poster_path,
                            rating = m.rating?.toDouble(),
                            onClick = { openMovie(m.movie_id, m.tmdb_id, m.name, m.poster_path) }
                        )
                    })
                }
            }
        }
    }

    if (stats.by_week.isNotEmpty()) {
        item(key = "movie-weeks") {
            Column {
                WeekActivity(
                    byWeek = stats.by_week,
                    accent = MaterialTheme.colorScheme.primary,
                    one = "film",
                    many = "films",
                    onOpenWeek = { week -> filterLibrary("all", week.toString(), "all", year) }
                )
                Spacer(Modifier.height(14.dp))
                PaceStrip(stats.films_logged, "films", stats.avg_per_month, stats.avg_per_week)
            }
        }
    }

    if (stats.rating_distribution.values.any { it > 0 }) {
        item(key = "movie-ratings") { RatingSection(stats.rating_distribution) }
    }

    // ── Theatre — one poster per film, with how many visits it took.
    val theatre = stats.theatre_stats
    if (theatre != null && theatre.movies.isNotEmpty()) {
        item(key = "movie-theatre") {
            var tag by remember(theatre) { mutableStateOf<String?>(null) }
            val tags = remember(theatre) { theatre.supplementary_tags.entries.sortedByDescending { it.value } }
            val visits = remember(theatre, tag) {
                theatre.movies
                    .filter { tag == null || it.tags.contains(tag) }
                    .groupBy { it.movie_id }
                    .values
                    .toList()
            }
            Column {
                SectionHeader("At the theatre") {
                    HeaderNote(plural(theatre.total_visits, "visit", "visits"))
                }
                if (tags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "all") {
                            TagPill("All", theatre.total_visits, selected = tag == null) { tag = null }
                        }
                        items(tags, key = { it.key }) { entry ->
                            TagPill(entry.key.uppercase(), entry.value, selected = tag == entry.key) {
                                tag = if (tag == entry.key) null else entry.key
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                PosterRail(visits.map { group ->
                    val m = group.first()
                    PosterItem(
                        key = m.movie_id,
                        title = m.name ?: "Unknown",
                        posterPath = m.poster_path,
                        rating = group.firstNotNullOfOrNull { it.rating }?.toDouble(),
                        badge = if (group.size > 1) "×${group.size}" else null,
                        onClick = { openMovie(m.movie_id, m.tmdb_id, m.name, m.poster_path) }
                    )
                })
            }
        }
    }

    if (stats.by_month.isNotEmpty()) {
        item(key = "movie-months") {
            MonthSection(stats.by_month, MaterialTheme.colorScheme.primary) { month ->
                filterLibrary(month.toString(), "all", "all", year)
            }
        }
    }

    if (stats.by_day.size == 7) {
        item(key = "movie-weekdays") { WeekdaySection(stats.by_day, MaterialTheme.colorScheme.primary) }
    }

    if (stats.films_by_language.isNotEmpty()) {
        item(key = "movie-languages") {
            Column {
                SectionHeader("Languages")
                LanguageBars(stats.films_by_language, MaterialTheme.colorScheme.primary) { item ->
                    filterLibrary("all", "all", item.code ?: "all", year)
                }
            }
        }
    }

    if (selectedYear == "all" && stats.films_by_year.size > 1) {
        item(key = "movie-years") {
            YearsSection(stats.films_by_year, MaterialTheme.colorScheme.primary) { y ->
                filterLibrary("all", "all", "all", y.toString())
            }
        }
    }

    if (stats.most_rewatched.isNotEmpty()) {
        item(key = "movie-rewatched") {
            Column {
                SectionHeader("Most rewatched")
                PosterRail(stats.most_rewatched.map { m ->
                    PosterItem(
                        key = m.movie_id,
                        title = m.name ?: "Unknown",
                        posterPath = m.poster_path,
                        badge = "×${m.watch_count}",
                        onClick = { openMovie(m.movie_id, m.tmdb_id, m.name, m.poster_path) }
                    )
                })
            }
        }
    }

    val extremes = stats.extremes
    val streak = stats.longest_streak?.takeIf { it.length > 0 }
    val tiles = buildList {
        extremes?.longest?.let {
            add(ExtremeItem("Longest", it.name ?: "Unknown", "${it.runtime ?: 0} min", it.poster_path, Icons.Default.HourglassBottom) {
                openMovie(it.id, it.tmdb_id, it.name, it.poster_path)
            })
        }
        extremes?.shortest?.let {
            add(ExtremeItem("Shortest", it.name ?: "Unknown", "${it.runtime ?: 0} min", it.poster_path, Icons.Default.Timer) {
                openMovie(it.id, it.tmdb_id, it.name, it.poster_path)
            })
        }
        extremes?.oldest?.let {
            add(ExtremeItem("Oldest", it.name ?: "Unknown", releaseLabel(it), it.poster_path, Icons.Default.History) {
                openMovie(it.id, it.tmdb_id, it.name, it.poster_path)
            })
        }
        extremes?.newest?.let {
            add(ExtremeItem("Newest", it.name ?: "Unknown", releaseLabel(it), it.poster_path, Icons.Default.NewReleases) {
                openMovie(it.id, it.tmdb_id, it.name, it.poster_path)
            })
        }
        streak?.let {
            add(ExtremeItem("Longest streak", plural(it.length, "day", "days"), streakRange(it), null, Icons.Default.LocalFireDepartment))
        }
    }
    if (tiles.isNotEmpty()) {
        item(key = "movie-extremes") {
            Column {
                SectionHeader("The extremes")
                ExtremesGrid(tiles, MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TV Shows
// ─────────────────────────────────────────────────────────────────────────────

private fun LazyListScope.tvStatsItems(
    tv: TvStatsResponseDto,
    selectedYear: String,
    isCurrentOrAll: Boolean,
    onAction: (SabdekhoAction) -> Unit
) {
    val year = tv.year ?: selectedYear
    val openShow: (Int, Int?, String?, String?, String?) -> Unit = { id, tmdbId, name, poster, status ->
        onAction(
            SabdekhoAction.OpenMediaDetails(
                MediaShowDto(id = id, tmdbId = tmdbId, name = name, posterPath = poster, type = "tv", status = status)
            )
        )
    }
    val open: (TvStatsShowDto) -> Unit = { openShow(it.show_id, it.tmdb_id, it.name, it.poster_path, it.status) }
    val filterLibrary: (month: String, week: String, language: String, inYear: String) -> Unit =
        { month, week, language, inYear ->
            onAction(
                SabdekhoAction.FilterLibraryFromStats(
                    year = inYear, month = month, week = week, language = language, mediaType = "tv"
                )
            )
        }

    // Currently watching is a live snapshot, so only for this year / all time.
    val watching = if (isCurrentOrAll) tv.in_progress else emptyList()
    val watchingSection: LazyListScope.() -> Unit = {
        if (watching.isNotEmpty()) {
            item(key = "tv-in-progress") {
                Column {
                    SectionHeader("Currently watching") { HeaderNote("${watching.size}") }
                    PosterRail(watching.map { show ->
                        PosterItem(
                            key = show.show_id,
                            title = show.name ?: "Unknown",
                            posterPath = show.poster_path,
                            badge = show.episodes_watched.takeIf { it > 0 }?.let { "$it ep" },
                            caption = show.last_watched?.let { "Last ${shortDate(it)}" } ?: "Not logged yet",
                            onClick = { open(show) }
                        )
                    })
                }
            }
        }
    }

    if (tv.total_entries == 0) {
        watchingSection(this)
        item(key = "tv-empty") {
            EmptyStatsCard(
                Icons.Default.Tv,
                if (selectedYear == "all") "No TV logged yet" else "No TV logged in $selectedYear"
            )
        }
        return
    }

    item(key = "tv-headline") {
        HeadlineGrid(
            listOf(
                Headline(tv.episodes_watched.toDouble(), if (tv.episodes_watched == 1) "Episode" else "Episodes"),
                Headline(tv.shows_watched.toDouble(), if (tv.shows_watched == 1) "Show" else "Shows"),
                Headline(tv.shows_completed.toDouble(), "Completed"),
                Headline(tv.average_rating, "Avg rating", decimals = 1, icon = Icons.Filled.Star, iconTint = GoldenStarColor)
            )
        )
    }

    watchingSection(this)

    if (tv.most_watched.isNotEmpty()) {
        item(key = "tv-most-watched") {
            Column {
                SectionHeader("Most watched") { HeaderNote("by episodes") }
                PosterRail(tv.most_watched.map { show ->
                    PosterItem(
                        key = show.show_id,
                        title = show.name ?: "Unknown",
                        posterPath = show.poster_path,
                        badge = if (show.episodes > 0) "${show.episodes} ep" else plural(show.logs, "log", "logs"),
                        onClick = { open(show) }
                    )
                })
            }
        }
    }

    if (tv.highest_rated.isNotEmpty()) {
        item(key = "tv-highest-rated") {
            Column {
                SectionHeader("Highest rated") { HeaderNote("your average") }
                PosterRail(tv.highest_rated.map { show ->
                    PosterItem(
                        key = show.show_id,
                        title = show.name ?: "Unknown",
                        posterPath = show.poster_path,
                        rating = show.rating,
                        caption = plural(show.ratings_count, "rating", "ratings"),
                        onClick = { open(show) }
                    )
                })
            }
        }
    }

    if (tv.by_week.isNotEmpty()) {
        item(key = "tv-weeks") {
            Column {
                WeekActivity(
                    byWeek = tv.by_week,
                    accent = MaterialTheme.colorScheme.tertiary,
                    one = "episode",
                    many = "episodes",
                    onOpenWeek = { week -> filterLibrary("all", week.toString(), "all", year) }
                )
                Spacer(Modifier.height(14.dp))
                PaceStrip(tv.episodes_watched, "episodes", tv.avg_per_month, tv.avg_per_week)
            }
        }
    }

    val binge = tv.biggest_binge
    val streak = tv.longest_streak?.takeIf { it.length > 0 }
    val highlights = buildList {
        binge?.let {
            add(
                ExtremeItem(
                    "Biggest binge",
                    it.name ?: "Unknown",
                    "${plural(it.episodes, "episode", "episodes")} · ${shortDate(it.date)}",
                    it.poster_path,
                    Icons.Default.Whatshot
                ) { openShow(it.show_id, it.tmdb_id, it.name, it.poster_path, null) }
            )
        }
        streak?.let {
            add(ExtremeItem("Longest streak", plural(it.length, "day", "days"), streakRange(it), null, Icons.Default.LocalFireDepartment))
        }
    }
    val hasCounts = tv.seasons_watched > 0 || tv.total_reviews > 0 || tv.total_rewatches > 0
    if (highlights.isNotEmpty() || hasCounts) {
        item(key = "tv-highlights") {
            Column {
                SectionHeader("Highlights")
                if (highlights.isNotEmpty()) ExtremesGrid(highlights, MaterialTheme.colorScheme.tertiary)
                if (hasCounts) {
                    if (highlights.isNotEmpty()) Spacer(Modifier.height(10.dp))
                    MiniStatsStrip(
                        listOf(
                            "Seasons" to tv.seasons_watched,
                            "Reviews" to tv.total_reviews,
                            "Rewatches" to tv.total_rewatches
                        )
                    )
                }
            }
        }
    }

    if (tv.completed.isNotEmpty()) {
        item(key = "tv-completed") {
            Column {
                SectionHeader("Finished") {
                    HeaderNote(if (selectedYear == "all") "all time" else "in $selectedYear")
                }
                PosterRail(tv.completed.map { show ->
                    PosterItem(
                        key = show.show_id,
                        title = show.name ?: "Unknown",
                        posterPath = show.poster_path,
                        caption = show.completed_on?.let { shortDate(it) },
                        onClick = { open(show) }
                    )
                })
            }
        }
    }

    if (tv.by_month.isNotEmpty()) {
        item(key = "tv-months") {
            MonthSection(tv.by_month, MaterialTheme.colorScheme.tertiary) { month ->
                filterLibrary(month.toString(), "all", "all", year)
            }
        }
    }

    if (tv.by_day.size == 7) {
        item(key = "tv-weekdays") { WeekdaySection(tv.by_day, MaterialTheme.colorScheme.tertiary) }
    }

    if (tv.shows_by_language.isNotEmpty()) {
        item(key = "tv-languages") {
            Column {
                SectionHeader("Languages")
                LanguageBars(tv.shows_by_language, MaterialTheme.colorScheme.tertiary) { item ->
                    filterLibrary("all", "all", item.code ?: "all", year)
                }
            }
        }
    }

    if (selectedYear == "all" && tv.episodes_by_year.size > 1) {
        item(key = "tv-years") {
            YearsSection(tv.episodes_by_year, MaterialTheme.colorScheme.tertiary) { y ->
                filterLibrary("all", "all", "all", y.toString())
            }
        }
    }

    if (tv.rating_distribution.values.any { it > 0 }) {
        item(key = "tv-ratings") { RatingSection(tv.rating_distribution) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero & section chrome
// ─────────────────────────────────────────────────────────────────────────────

/** The big year, a line on what's shown, and the year picker — with faint film-strip lines behind. */
@Composable
private fun StatsHero(
    year: String,
    subtitle: String,
    years: List<String>,
    refreshing: Boolean,
    onSelectYear: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val stripe = colors.onPrimaryContainer.copy(alpha = 0.05f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer)))
            .drawBehind {
                val step = 9.dp.toPx()
                val stroke = 1.dp.toPx()
                var x = step
                while (x < size.width) {
                    drawLine(stripe, Offset(x, 0f), Offset(x, size.height), stroke)
                    x += step
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = year,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 3 })
                },
                label = "statsHeroYear"
            ) { shown ->
                Text(
                    text = if (shown == "all") "All Time" else shown,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-2).sp
                    ),
                    color = colors.onPrimaryContainer,
                    maxLines = 1
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onPrimaryContainer.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(18.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                items(years, key = { it }) { y ->
                    YearPill(label = if (y == "all") "All Time" else y, selected = y == year) { onSelectYear(y) }
                }
            }
        }
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(16.dp),
                strokeWidth = 2.dp,
                color = colors.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun YearPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) colors.onPrimary else colors.onPrimaryContainer,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surface.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

@Composable
private fun MediaDivider(icon: ImageVector, label: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    }
}

/** Small-caps title over a hairline rule, with room on the right for a note or control. */
@Composable
private fun SectionHeader(title: String, trailing: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun HeaderNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        maxLines = 1
    )
}

@Composable
private fun QuietNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SegmentToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .padding(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                color = if (on) colors.onPrimary else colors.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (on) colors.primary else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun TagPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onPrimary else colors.onSurface
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = content)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = content.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun EmptyStatsCard(
    icon: ImageVector,
    message: String,
    detail: String? = null,
    onRetry: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(colors.surfaceContainer)
            .padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface,
            textAlign = TextAlign.Center
        )
        if (!detail.isNullOrBlank() && detail != message) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Numbers
// ─────────────────────────────────────────────────────────────────────────────

private data class Headline(
    val value: Double?,
    val label: String,
    val decimals: Int = 0,
    val icon: ImageVector? = null,
    val iconTint: Color = Color.Unspecified
)

@Composable
private fun HeadlineGrid(items: List<Headline>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { HeadlineTile(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeadlineTile(item: Headline, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val numberStyle = MaterialTheme.typography.headlineLarge.copy(
        fontWeight = FontWeight.Black,
        letterSpacing = (-1).sp,
        fontFeatureSettings = "tnum"
    )
    Column(
        modifier = modifier
            .clip(TileShape)
            .background(colors.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.value == null) {
                Text(text = "—", style = numberStyle, color = colors.onSurface)
            } else {
                CountUpText(item.value, item.decimals, numberStyle, colors.onSurface)
            }
            if (item.icon != null) {
                Spacer(Modifier.width(6.dp))
                Icon(imageVector = item.icon, contentDescription = null, tint = item.iconTint, modifier = Modifier.size(22.dp))
            }
        }
        Text(
            text = item.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            color = colors.onSurfaceVariant
        )
    }
}

/** Counts up the first time a value is shown; scrolling back to it doesn't replay. */
@Composable
private fun CountUpText(target: Double, decimals: Int, style: TextStyle, color: Color) {
    var played by rememberSaveable(target) { mutableStateOf(false) }
    val value = remember(target) { Animatable(if (played) target.toFloat() else 0f) }
    LaunchedEffect(target) {
        if (!played) {
            value.animateTo(target.toFloat(), tween(durationMillis = 900, easing = FastOutSlowInEasing))
            played = true
        }
    }
    Text(text = formatNumber(value.value.toDouble(), decimals), style = style, color = color, maxLines = 1)
}

/** "142 films → 11.8 per month → 2.7 per week". */
@Composable
private fun PaceStrip(total: Int, unit: String, perMonth: Double, perWeek: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PaceValue(formatNumber(total.toDouble(), 0), unit, Modifier.weight(1f))
        PaceArrow()
        PaceValue(String.format(Locale.US, "%.1f", perMonth), "per month", Modifier.weight(1f))
        PaceArrow()
        PaceValue(String.format(Locale.US, "%.1f", perWeek), "per week", Modifier.weight(1f))
    }
}

@Composable
private fun PaceValue(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PaceArrow() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.size(16.dp)
    )
}

@Composable
private fun MiniStatsStrip(stats: List<Pair<String, Int>>) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(TileShape)
            .background(colors.surfaceContainer)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        stats.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 4.dp),
                    color = colors.outlineVariant.copy(alpha = 0.5f)
                )
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatNumber(value.toDouble(), 0),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = colors.onSurface
                )
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Posters
// ─────────────────────────────────────────────────────────────────────────────

private data class PosterItem(
    val key: Any,
    val title: String,
    val posterPath: String?,
    val rating: Double? = null,
    val badge: String? = null,
    val caption: String? = null,
    val onClick: () -> Unit
)

@Composable
private fun PosterRail(items: List<PosterItem>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.key }) { PosterTile(it) }
    }
}

/** Poster first, stars under it — the Letterboxd way; no titles to crowd the art. */
@Composable
private fun PosterTile(item: PosterItem) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Column(modifier = Modifier.width(PosterWidth)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(colors.surfaceVariant)
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.35f), shape)
                .clickable(onClick = item.onClick)
        ) {
            val url = posterUrl(item.posterPath, "w342")
            if (url != null) {
                PosterImage(url = url, description = item.title)
            } else {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(8.dp)
                )
            }
            if (item.badge != null) {
                Text(
                    text = item.badge,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.72f), CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
        val rating = item.rating?.takeIf { it > 0 }
        if (rating != null) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = starText(rating),
                color = GoldenStarColor,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
                maxLines = 1
            )
        }
        if (!item.caption.isNullOrBlank()) {
            Spacer(Modifier.height(if (rating != null) 1.dp else 5.dp))
            Text(
                text = item.caption,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PosterImage(url: String, description: String?) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}

private data class ExtremeItem(
    val label: String,
    val title: String,
    val value: String,
    val posterPath: String?,
    val icon: ImageVector,
    val onClick: (() -> Unit)? = null
)

/** Two per row; an odd one out takes the full width. */
@Composable
private fun ExtremesGrid(items: List<ExtremeItem>, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach {
                    ExtremeTile(
                        it, accent,
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtremeTile(item: ExtremeItem, accent: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val url = posterUrl(item.posterPath, "w185")
    Row(
        modifier = modifier
            .clip(TileShape)
            .background(colors.surfaceContainer)
            .then(if (item.onClick != null) Modifier.clickable(onClick = item.onClick) else Modifier)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 66.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (url == null) accent.copy(alpha = 0.14f) else colors.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (url != null) {
                PosterImage(url = url, description = item.title)
            } else {
                Icon(imageVector = item.icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = item.icon, contentDescription = null, tint = accent, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = item.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = accent,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.value,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Charts
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bar heights as fractions of the tallest, animated: bars grow in the first time
 * a chart appears and ease to new values after (another year). Saved so scrolling
 * back to a chart doesn't replay the grow-in.
 */
@Composable
private fun animatedFractions(values: List<Int>, max: Int, stagger: Int): List<Float> {
    var appeared by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    return values.mapIndexed { index, count ->
        key(index) {
            val target = if (!appeared || count <= 0) 0f else (count.toFloat() / max).coerceIn(0.05f, 1f)
            animateFloatAsState(
                targetValue = target,
                animationSpec = tween(durationMillis = 600, delayMillis = index * stagger, easing = FastOutSlowInEasing),
                label = "statsBar"
            ).value
        }
    }
}

/** Vertical bars with counts above; the busiest bar is drawn solid, the rest softer. */
@Composable
private fun ColumnChart(
    values: List<Int>,
    accent: Color,
    modifier: Modifier = Modifier,
    labels: List<String>? = null,
    barAreaHeight: Dp = 96.dp,
    onBarClick: ((Int) -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val peak = values.indices.maxByOrNull { values[it] }?.takeIf { values[it] > 0 }
    val fractions = animatedFractions(values, max, stagger = 30)
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEachIndexed { index, count ->
            val isPeak = index == peak
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (onBarClick != null && count > 0) Modifier.clickable { onBarClick(index) }
                        else Modifier
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barAreaHeight + 16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (count > 0) {
                            Text(
                                text = "$count",
                                fontSize = 10.sp,
                                fontWeight = if (isPeak) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isPeak) accent else colors.onSurfaceVariant,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.66f)
                                .height(if (count > 0) barAreaHeight * fractions[index] else 3.dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 1.dp, bottomEnd = 1.dp))
                                .background(
                                    when {
                                        count == 0 -> colors.surfaceContainerHighest
                                        isPeak -> accent
                                        else -> accent.copy(alpha = 0.55f)
                                    }
                                )
                        )
                    }
                }
                if (labels != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = labels.getOrElse(index) { "" },
                        fontSize = 10.sp,
                        fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Normal,
                        color = if (count > 0) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * All 52 weeks as thin bars, Letterboxd style. Tapping a week names it in the
 * header, and tapping that opens the Library filtered to it.
 */
@Composable
private fun WeekActivity(
    byWeek: List<Int>,
    accent: Color,
    one: String,
    many: String,
    onOpenWeek: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val weeks = byWeek.take(52)
    val max = weeks.maxOrNull()?.coerceAtLeast(1) ?: 1
    val peak = weeks.indices.maxByOrNull { weeks[it] }?.takeIf { weeks[it] > 0 }
    var selected by remember(weeks) { mutableStateOf<Int?>(null) }
    val fractions = animatedFractions(weeks, max, stagger = 8)
    val track = colors.surfaceContainerHighest

    Column {
        SectionHeader("By week") {
            val week = selected
            if (week != null) {
                val canOpen = weeks[week] > 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .then(if (canOpen) Modifier.clickable { onOpenWeek(week + 1) } else Modifier)
                        .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = "Week ${week + 1} · ${plural(weeks[week], one, many)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    if (canOpen) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Show week ${week + 1} in Library",
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else if (peak != null) {
                HeaderNote("Busiest: week ${peak + 1} · ${weeks[peak]}")
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .pointerInput(weeks) {
                    detectTapGestures { offset ->
                        val index = (offset.x / (size.width.toFloat() / weeks.size)).toInt().coerceIn(0, weeks.lastIndex)
                        selected = if (selected == index) null else index
                    }
                }
        ) {
            val slot = size.width / weeks.size
            val barWidth = slot * 0.68f
            val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
            val stub = 2.dp.toPx()
            weeks.forEachIndexed { index, count ->
                val height = if (count > 0) (size.height * fractions[index]).coerceAtLeast(stub) else stub
                val color = when {
                    count == 0 -> track
                    selected == null || selected == index -> accent
                    else -> accent.copy(alpha = 0.3f)
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(index * slot + (slot - barWidth) / 2f, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = radius
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Jan", "Apr", "Jul", "Oct", "Dec").forEach {
                Text(text = it, fontSize = 10.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MonthSection(byMonth: List<Int>, accent: Color, onMonthClick: (Int) -> Unit) {
    val months = byMonth.take(12)
    val peak = months.indices.maxByOrNull { months[it] }?.takeIf { months[it] > 0 }
    Column {
        SectionHeader("By month") { peak?.let { HeaderNote("Busiest: ${MonthLong[it]}") } }
        ColumnChart(values = months, accent = accent, labels = MonthShort, onBarClick = { onMonthClick(it + 1) })
    }
}

@Composable
private fun WeekdaySection(byDay: List<Int>, accent: Color) {
    val peak = byDay.indices.maxByOrNull { byDay[it] }?.takeIf { byDay[it] > 0 }
    Column {
        SectionHeader("By day") { peak?.let { HeaderNote("Busiest: ${WeekdayLong[it]}") } }
        ColumnChart(values = byDay, accent = accent, labels = WeekdayShort, barAreaHeight = 80.dp)
    }
}

@Composable
private fun YearsSection(entries: List<TvYearCountDto>, accent: Color, onYearClick: (Int) -> Unit) {
    val labels = entries.map { if (entries.size > 6) "'" + (it.year % 100).toString().padStart(2, '0') else "${it.year}" }
    Column {
        SectionHeader("By year")
        ColumnChart(
            values = entries.map { it.count },
            accent = accent,
            labels = labels,
            onBarClick = { onYearClick(entries[it].year) }
        )
    }
}

/** Letterboxd's histogram: half-star steps between one star and five. */
@Composable
private fun RatingSection(distribution: Map<String, Int>) {
    val counts = remember(distribution) {
        RatingKeys.map { key -> distribution[key] ?: distribution[key.removeSuffix(".0")] ?: 0 }
    }
    val total = counts.sum()
    val average = if (total > 0) counts.indices.sumOf { (it + 1) * 0.5 * counts[it] } / total else 0.0
    Column {
        SectionHeader("Ratings") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = GoldenStarColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                HeaderNote(String.format(Locale.US, "%.1f avg · %s", average, plural(total, "rating", "ratings")))
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = "★", color = GoldenStarColor, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            ColumnChart(values = counts, accent = GoldenStarColor, modifier = Modifier.weight(1f), barAreaHeight = 88.dp)
            Spacer(Modifier.width(8.dp))
            Text(text = "★★★★★", color = GoldenStarColor, fontSize = 9.sp)
        }
    }
}

/** Horizontal bars read better than vertical ones once labels are words. */
@Composable
private fun LanguageBars(languages: List<LanguageCountDto>, accent: Color, onClick: (LanguageCountDto) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val max = languages.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val fractions = animatedFractions(languages.map { it.count }, max, stagger = 40)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        languages.forEachIndexed { index, item ->
            // The "Other" bucket (code == null) folds several languages into
            // one row, so it isn't a single value the Library can filter by.
            val canOpen = item.code != null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (canOpen) Modifier.clickable { onClick(item) } else Modifier)
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.language ?: "Unknown",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (canOpen) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(92.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHighest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fractions[index])
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(if (canOpen) accent else accent.copy(alpha = 0.4f))
                    )
                }
                Text(
                    text = "${item.count}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Formatting
// ─────────────────────────────────────────────────────────────────────────────

private fun heroSubtitle(year: String, mode: StatsMode): String {
    if (year == "all") {
        val what = when (mode) {
            StatsMode.MOVIES -> "film"
            StatsMode.TV -> "TV"
            StatsMode.BOTH -> "film & TV"
        }
        return "Your all-time $what journey"
    }
    return when (mode) {
        StatsMode.MOVIES -> "Your $year year in film"
        StatsMode.TV -> "Your $year year in TV"
        StatsMode.BOTH -> "Your $year year on screen"
    }
}

/** TMDB paths get the image host; full URLs (demo data) are used as-is. */
private fun posterUrl(path: String?, size: String): String? = when {
    path.isNullOrBlank() -> null
    path.startsWith("http") -> path
    else -> "https://image.tmdb.org/t/p/$size$path"
}

/** "★★★★½" — rounded to the nearest half star. */
private fun starText(rating: Double): String {
    val halves = (rating * 2).roundToInt().coerceIn(0, 10)
    return "★".repeat(halves / 2) + if (halves % 2 == 1) "½" else ""
}

private fun formatNumber(value: Double, decimals: Int): String =
    if (decimals == 0) String.format(Locale.US, "%,d", value.roundToInt())
    else String.format(Locale.US, "%,.${decimals}f", value)

private val fullDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val shortDateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** "14 Mar 2026" when the full release date is known, otherwise the year. */
private fun releaseLabel(item: MediaExtremeItemDto): String {
    val parsed = item.release_date
        ?.takeIf { it.length >= 10 }
        ?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    return parsed?.format(fullDateFormat) ?: item.release_year.orEmpty()
}

/** "12 Sep"; includes the year only when it isn't the current one. */
private fun shortDate(iso: String?): String {
    val parsed = iso?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return iso.orEmpty()
    return if (parsed.year == LocalDate.now().year) parsed.format(shortDateFormat) else parsed.format(fullDateFormat)
}

private fun streakRange(streak: MediaStreakDto): String =
    if (streak.start == streak.end) streak.start.orEmpty() else "${streak.start.orEmpty()} – ${streak.end.orEmpty()}"

private fun plural(count: Int, one: String, many: String): String = "$count ${if (count == 1) one else many}"
