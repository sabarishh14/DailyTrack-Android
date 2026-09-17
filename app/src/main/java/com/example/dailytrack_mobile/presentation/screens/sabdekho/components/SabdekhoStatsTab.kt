package com.example.dailytrack_mobile.presentation.screens.sabdekho.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dailytrack_mobile.data.remote.dto.*
import com.example.dailytrack_mobile.presentation.screens.sabdekho.SabdekhoAction
import com.example.dailytrack_mobile.presentation.screens.sabdekho.SabdekhoState
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Stats tab — follows the All / Films / TV Shows switch above it: film stats,
// TV stats, or both under one year picker.
// ─────────────────────────────────────────────────────────────────────────────

private enum class StatsMode { MOVIES, TV, BOTH }

private val TvAccent = Color(0xFF7C4DFF)
private val BingeAccent = Color(0xFFFF7043)

@Composable
fun SabdekhoStatsTab(
    state: SabdekhoState,
    onAction: (SabdekhoAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current
    val mode = when (state.mediaTypeFilter.lowercase()) {
        "movie" -> StatsMode.MOVIES
        "tv" -> StatsMode.TV
        else -> StatsMode.BOTH
    }
    val showMovies = mode != StatsMode.TV
    val showTv = mode != StatsMode.MOVIES
    val stats = state.stats
    val tvStats = state.tvStats

    val waitingForMovies = showMovies && stats == null && state.isStatsLoading
    val waitingForTv = showTv && tvStats == null && state.isTvStatsLoading
    if (waitingForMovies || waitingForTv) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val nothingToShow = (!showMovies || stats == null) && (!showTv || tvStats == null)
    if (nothingToShow) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "No stats available",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { onAction(SabdekhoAction.LoadStats(state.selectedStatsYear)) }) {
                    Text("Try again")
                }
            }
        }
        return
    }

    val currentYear = LocalDate.now().year.toString()
    val isCurrentOrAll = state.selectedStatsYear == "all" || state.selectedStatsYear == currentYear

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge),
        contentPadding = PaddingValues(bottom = dims.screenBottomPadding + 56.dp)
    ) {
        // ── Year Selector Row ──────────────────────────────────────────
        item(key = "years") {
            val availableYears = remember(stats?.available_years, tvStats?.available_years, showMovies, showTv) {
                val years = buildSet {
                    if (showMovies) stats?.available_years?.forEach { add(it.toString()) }
                    if (showTv) tvStats?.available_years?.forEach { add(it.toString()) }
                    add(currentYear)
                }
                listOf("all") + years.sortedDescending()
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)) {
                items(availableYears, key = { it }) { yr ->
                    FilterChipView(
                        label = if (yr == "all") "All Time" else yr,
                        isSelected = state.selectedStatsYear.equals(yr, ignoreCase = true)
                    ) {
                        onAction(SabdekhoAction.SelectStatsYear(yr))
                    }
                }
            }
        }

        if (showMovies) {
            if (mode == StatsMode.BOTH) item(key = "divider-movies") { MediaDivider(Icons.Default.Movie, "Films") }
            if (stats != null) {
                movieStatsItems(stats, onAction)
            } else {
                item(key = "movies-error") { EmptyStatsCard(Icons.Default.Movie, "Couldn't load film stats") }
            }
        }

        if (showTv) {
            if (mode == StatsMode.BOTH) item(key = "divider-tv") { MediaDivider(Icons.Default.Tv, "TV Shows") }
            if (tvStats != null) {
                tvStatsItems(tvStats, state.selectedStatsYear, isCurrentOrAll, onAction)
            } else {
                item(key = "tv-error") {
                    EmptyStatsCard(Icons.Default.Tv, state.tvStatsError ?: "Couldn't load TV stats")
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
    onAction: (SabdekhoAction) -> Unit
) {
    // ── KPI Summary Cards (2x2 Grid) ───────────────────────────────
    item(key = "movie-kpis") {
        val dims = Dimens.current
        Column(verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
            ) {
                KpiStatCard(
                    title = "Films Logged",
                    value = "${stats.films_logged}",
                    icon = Icons.Default.Movie,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                KpiStatCard(
                    title = "Likes",
                    value = "${stats.total_likes}",
                    icon = Icons.Default.Favorite,
                    iconTint = Color(0xFFE91E63),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
            ) {
                KpiStatCard(
                    title = "Hours Watched",
                    value = String.format(Locale.US, "%.1f", stats.total_hours),
                    icon = Icons.Default.Schedule,
                    iconTint = Color(0xFF00B0FF),
                    modifier = Modifier.weight(1f)
                )
                KpiStatCard(
                    title = "Theatre Visits",
                    value = "${stats.theatre_stats?.total_visits ?: 0}",
                    icon = Icons.Default.ConfirmationNumber,
                    iconTint = Color(0xFFFF9800),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // ── Rating Distribution Bar Chart ──────────────────────────────
    item(key = "movie-ratings") { RatingDistributionCard(stats.rating_distribution) }

    // ── Monthly Breakdown Bar Chart ────────────────────────────────
    if (stats.by_month.isNotEmpty()) {
        item(key = "movie-months") {
            MonthlyActivityCard(title = "MONTHLY ACTIVITY", byMonth = stats.by_month, accent = MaterialTheme.colorScheme.primary)
        }
    }

    // ── Highest Rated Carousel ────────────────────────────────────
    if (stats.highest_rated.isNotEmpty()) {
        item(key = "movie-highest-rated") {
            val dims = Dimens.current
            Column {
                SectionLabel("HIGHEST RATED FILMS")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)) {
                    items(stats.highest_rated, key = { it.movie_id }) { movie ->
                        PosterStatCard(
                            title = movie.name ?: "Unknown",
                            posterPath = movie.poster_path,
                            badgeIcon = Icons.Filled.Star,
                            badgeIconTint = GoldenStarColor,
                            badgeText = String.format(Locale.US, "%.1f", movie.rating),
                            caption = movie.release_year,
                            onClick = {
                                onAction(
                                    SabdekhoAction.OpenMediaDetails(
                                        MediaShowDto(
                                            id = movie.movie_id,
                                            tmdbId = movie.tmdb_id,
                                            name = movie.name,
                                            posterPath = movie.poster_path,
                                            type = "movie",
                                            status = "WATCHED"
                                        )
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // ── Theatre Experience Section ────────────────────────────────
    stats.theatre_stats?.let { theatre ->
        if (theatre.movies.isNotEmpty()) {
            item(key = "movie-theatre") {
                val dims = Dimens.current
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(dims.cardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(dims.cardInnerPadding)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "THEATRE VISITS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                            CountPill(text = "${theatre.total_visits} Visits", color = Color(0xFFFF9800))
                        }

                        Spacer(modifier = Modifier.height(dims.itemSpacingMedium))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(theatre.movies) { m ->
                                Box(
                                    modifier = Modifier
                                        .size(width = 60.dp, height = 90.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    posterUrl(m.poster_path, "w200")?.let { url ->
                                        PosterImage(url = url, description = m.name)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── The Extremes Section ──────────────────────────────────────
    stats.extremes?.let { extremes ->
        item(key = "movie-extremes") {
            val dims = Dimens.current
            Column {
                SectionLabel("THE EXTREMES")

                Column(verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
                    ) {
                        extremes.longest?.let {
                            ExtremeCard(
                                label = "Longest Runtime",
                                title = it.name ?: "Unknown",
                                subtitle = "${it.runtime ?: 0} mins",
                                posterPath = it.poster_path,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        extremes.shortest?.let {
                            ExtremeCard(
                                label = "Shortest Runtime",
                                title = it.name ?: "Unknown",
                                subtitle = "${it.runtime ?: 0} mins",
                                posterPath = it.poster_path,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
                    ) {
                        extremes.oldest?.let {
                            ExtremeCard(
                                label = "Oldest Release",
                                title = it.name ?: "Unknown",
                                subtitle = releaseLabel(it),
                                posterPath = it.poster_path,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        extremes.newest?.let {
                            ExtremeCard(
                                label = "Newest Release",
                                title = it.name ?: "Unknown",
                                subtitle = releaseLabel(it),
                                posterPath = it.poster_path,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
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
    val openShow: (TvStatsShowDto) -> Unit = { show ->
        onAction(
            SabdekhoAction.OpenMediaDetails(
                MediaShowDto(
                    id = show.show_id,
                    tmdbId = show.tmdb_id,
                    name = show.name,
                    posterPath = show.poster_path,
                    type = "tv",
                    status = show.status
                )
            )
        )
    }

    // ── KPI Summary Cards (2x2 Grid) ───────────────────────────────
    item(key = "tv-kpis") {
        val dims = Dimens.current
        Column(verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
            ) {
                KpiStatCard(
                    title = "Episodes",
                    value = "${tv.episodes_watched}",
                    icon = Icons.Default.PlayCircle,
                    iconTint = TvAccent,
                    modifier = Modifier.weight(1f)
                )
                KpiStatCard(
                    title = "Shows",
                    value = "${tv.shows_watched}",
                    icon = Icons.Default.Tv,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
            ) {
                KpiStatCard(
                    title = "Completed",
                    value = "${tv.shows_completed}",
                    icon = Icons.Default.CheckCircle,
                    iconTint = Color(0xFF2ECC71),
                    modifier = Modifier.weight(1f)
                )
                KpiStatCard(
                    title = "Avg Rating",
                    value = tv.average_rating?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    icon = Icons.Default.Star,
                    iconTint = GoldenStarColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // ── Currently Watching — a live snapshot, so only for this year / all time
    if (isCurrentOrAll && tv.in_progress.isNotEmpty()) {
        item(key = "tv-in-progress") {
            val dims = Dimens.current
            Column {
                SectionLabel("CURRENTLY WATCHING", trailing = "${tv.in_progress.size}")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)) {
                    items(tv.in_progress, key = { it.show_id }) { show ->
                        PosterStatCard(
                            title = show.name ?: "Unknown",
                            posterPath = show.poster_path,
                            badgeIcon = Icons.Default.PlayArrow,
                            badgeIconTint = Color.White,
                            badgeText = show.episodes_watched.takeIf { it > 0 }?.let { "$it ep" },
                            caption = show.last_watched?.let { "Last ${shortDate(it)}" } ?: "Not logged yet",
                            onClick = { openShow(show) }
                        )
                    }
                }
            }
        }
    }

    if (tv.total_entries == 0) {
        item(key = "tv-empty") {
            EmptyStatsCard(
                Icons.Default.Tv,
                if (selectedYear == "all") "No TV logged yet" else "No TV logged in $selectedYear"
            )
        }
        return
    }

    // ── Most Watched ───────────────────────────────────────────────
    if (tv.most_watched.isNotEmpty()) {
        item(key = "tv-most-watched") {
            val dims = Dimens.current
            Column {
                SectionLabel("MOST WATCHED SHOWS", trailing = "by episodes")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)) {
                    items(tv.most_watched, key = { it.show_id }) { show ->
                        PosterStatCard(
                            title = show.name ?: "Unknown",
                            posterPath = show.poster_path,
                            badgeIcon = Icons.Default.PlayArrow,
                            badgeIconTint = Color.White,
                            badgeText = if (show.episodes > 0) "${show.episodes}" else null,
                            caption = if (show.episodes > 0) plural(show.episodes, "episode", "episodes")
                                      else plural(show.logs, "log", "logs"),
                            onClick = { openShow(show) }
                        )
                    }
                }
            }
        }
    }

    // ── Highest Rated ──────────────────────────────────────────────
    if (tv.highest_rated.isNotEmpty()) {
        item(key = "tv-highest-rated") {
            val dims = Dimens.current
            Column {
                SectionLabel("HIGHEST RATED SHOWS", trailing = "your average")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)) {
                    items(tv.highest_rated, key = { it.show_id }) { show ->
                        PosterStatCard(
                            title = show.name ?: "Unknown",
                            posterPath = show.poster_path,
                            badgeIcon = Icons.Filled.Star,
                            badgeIconTint = GoldenStarColor,
                            badgeText = show.rating?.let { String.format(Locale.US, "%.1f", it) },
                            caption = plural(show.ratings_count, "rating", "ratings"),
                            onClick = { openShow(show) }
                        )
                    }
                }
            }
        }
    }

    // ── Highlights ─────────────────────────────────────────────────
    val binge = tv.biggest_binge
    val streak = tv.longest_streak?.takeIf { it.length > 0 }
    if (binge != null || streak != null) {
        item(key = "tv-highlights") {
            val dims = Dimens.current
            Column {
                SectionLabel("HIGHLIGHTS")
                Column(verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
                    ) {
                        if (binge != null) {
                            ExtremeCard(
                                label = "Biggest Binge",
                                title = binge.name ?: "Unknown",
                                subtitle = "${plural(binge.episodes, "episode", "episodes")} · ${shortDate(binge.date)}",
                                posterPath = binge.poster_path,
                                accent = BingeAccent,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (streak != null) {
                            ExtremeCard(
                                label = "Longest Streak",
                                title = plural(streak.length, "day", "days"),
                                subtitle = if (streak.start == streak.end) streak.start.orEmpty()
                                           else "${streak.start.orEmpty()} – ${streak.end.orEmpty()}",
                                posterPath = null,
                                fallbackIcon = Icons.Default.LocalFireDepartment,
                                accent = BingeAccent,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (tv.seasons_watched > 0 || tv.total_reviews > 0 || tv.total_rewatches > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
                        ) {
                            MiniStat("Seasons", "${tv.seasons_watched}", Modifier.weight(1f))
                            MiniStat("Reviews", "${tv.total_reviews}", Modifier.weight(1f))
                            MiniStat("Rewatches", "${tv.total_rewatches}", Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    // ── Finished ───────────────────────────────────────────────────
    if (tv.completed.isNotEmpty()) {
        item(key = "tv-completed") {
            val dims = Dimens.current
            Column {
                SectionLabel("FINISHED", trailing = if (selectedYear == "all") "all time" else "in $selectedYear")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)) {
                    items(tv.completed, key = { it.show_id }) { show ->
                        PosterStatCard(
                            title = show.name ?: "Unknown",
                            posterPath = show.poster_path,
                            badgeIcon = Icons.Default.Check,
                            badgeIconTint = Color(0xFF2ECC71),
                            badgeText = null,
                            caption = show.completed_on?.let { shortDate(it) },
                            onClick = { openShow(show) }
                        )
                    }
                }
            }
        }
    }

    // ── Pace ───────────────────────────────────────────────────────
    item(key = "tv-pace") {
        val dims = Dimens.current
        StatsCard {
            SectionLabel("PACE", bottomPadding = dims.itemSpacingMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PaceValue("${tv.episodes_watched}", "episodes", Modifier.weight(1f))
                PaceArrow()
                PaceValue(String.format(Locale.US, "%.1f", tv.avg_per_month), "per month", Modifier.weight(1f))
                PaceArrow()
                PaceValue(String.format(Locale.US, "%.1f", tv.avg_per_week), "per week", Modifier.weight(1f))
            }
        }
    }

    if (tv.by_month.isNotEmpty()) {
        item(key = "tv-months") {
            MonthlyActivityCard(title = "MONTHLY ACTIVITY", byMonth = tv.by_month, accent = TvAccent)
        }
    }

    if (tv.by_day.size == 7) {
        item(key = "tv-weekdays") { WeekdayActivityCard(tv.by_day, TvAccent) }
    }

    if (tv.rating_distribution.isNotEmpty()) {
        item(key = "tv-ratings") { RatingDistributionCard(tv.rating_distribution) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared pieces
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MediaDivider(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SectionLabel(
    text: String,
    trailing: String? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = Dimens.current.itemSpacingMedium
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun StatsCard(content: @Composable ColumnScope.() -> Unit) {
    val dims = Dimens.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dims.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(dims.cardInnerPadding), content = content)
    }
}

@Composable
private fun EmptyStatsCard(icon: ImageVector, message: String) {
    StatsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CountPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
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

/** A poster with an optional corner badge and a caption — used by every carousel. */
@Composable
private fun PosterStatCard(
    title: String,
    posterPath: String?,
    badgeIcon: ImageVector?,
    badgeIconTint: Color,
    badgeText: String?,
    caption: String?,
    onClick: () -> Unit
) {
    val dims = Dimens.current
    Card(
        modifier = Modifier
            .width(115.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(165.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val url = posterUrl(posterPath, "w342")
                if (url != null) {
                    PosterImage(url = url, description = title)
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.Center)
                    )
                }

                if (badgeText != null || badgeIcon != null) {
                    Row(
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.TopEnd)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (badgeIcon != null) {
                            Icon(
                                imageVector = badgeIcon,
                                contentDescription = null,
                                tint = badgeIconTint,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        if (badgeText != null) {
                            if (badgeIcon != null) Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!caption.isNullOrBlank()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingDistributionCard(distribution: Map<String, Int>) {
    val dims = Dimens.current
    StatsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RATING DISTRIBUTION",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = GoldenStarColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Scale 0.5 - 5.0",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(dims.itemSpacingLarge))

        val ratingKeys = listOf("0.5", "1.0", "1.5", "2.0", "2.5", "3.0", "3.5", "4.0", "4.5", "5.0")
        val counts = remember(distribution) {
            ratingKeys.map { key -> distribution[key] ?: distribution[key.toFloat().toString()] ?: 0 }
        }
        val maxVal = counts.maxOrNull()?.coerceAtLeast(1) ?: 1

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            ratingKeys.forEachIndexed { index, key ->
                BarColumn(
                    count = counts[index],
                    max = maxVal,
                    label = key,
                    labelSize = 8.5.sp,
                    countColor = GoldenStarColor,
                    fill = Brush.verticalGradient(listOf(Color(0xFFFFD54F), GoldenStarColor)),
                    barWidthFraction = 0.58f,
                    animationLabel = "ratingBar_$key",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MonthlyActivityCard(title: String, byMonth: List<Int>, accent: Color) {
    val dims = Dimens.current
    StatsCard {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(dims.itemSpacingLarge))

        val monthLabels = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
        val maxMonth = remember(byMonth) { byMonth.maxOrNull()?.coerceAtLeast(1) ?: 1 }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(125.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            byMonth.take(12).forEachIndexed { index, count ->
                BarColumn(
                    count = count,
                    max = maxMonth,
                    label = monthLabels.getOrElse(index) { "" },
                    labelSize = 9.sp,
                    countColor = accent,
                    fill = Brush.verticalGradient(listOf(accent.copy(alpha = 0.7f), accent)),
                    barWidthFraction = 0.55f,
                    animationLabel = "monthBar_${title}_$index",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun WeekdayActivityCard(byDay: List<Int>, accent: Color) {
    val dims = Dimens.current
    StatsCard {
        SectionLabel("BY DAY OF WEEK", trailing = byDay.withIndex().maxByOrNull { it.value }
            ?.takeIf { it.value > 0 }
            ?.let { "Busiest: ${listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[it.index]}" })

        Spacer(modifier = Modifier.height(dims.itemSpacingSmall))

        val labels = listOf("M", "T", "W", "T", "F", "S", "S")
        val maxDay = remember(byDay) { byDay.maxOrNull()?.coerceAtLeast(1) ?: 1 }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            byDay.forEachIndexed { index, count ->
                val isWeekend = index >= 5
                BarColumn(
                    count = count,
                    max = maxDay,
                    label = labels[index],
                    labelSize = 9.sp,
                    countColor = accent,
                    fill = Brush.verticalGradient(
                        listOf(accent.copy(alpha = if (isWeekend) 0.45f else 0.7f), accent.copy(alpha = if (isWeekend) 0.75f else 1f))
                    ),
                    barWidthFraction = 0.5f,
                    animationLabel = "dayBar_$index",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** One animated vertical bar with its count above and label below. */
@Composable
private fun BarColumn(
    count: Int,
    max: Int,
    label: String,
    labelSize: androidx.compose.ui.unit.TextUnit,
    countColor: Color,
    fill: Brush,
    barWidthFraction: Float,
    animationLabel: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxHeight()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            val animatedFraction by animateFloatAsState(
                targetValue = if (count > 0) (count.toFloat() / max).coerceIn(0.12f, 1f) else 0.04f,
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                label = animationLabel
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.fillMaxHeight(animatedFraction)
            ) {
                if (count > 0) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = countColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(barWidthFraction)
                        .weight(1f, fill = true)
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .background(
                            if (count > 0) fill
                            else SolidColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = labelSize),
            fontWeight = if (count > 0) FontWeight.Bold else FontWeight.Normal,
            color = if (count > 0) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PaceValue(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
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
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    val dims = Dimens.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(dims.buttonCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun KpiStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(dims.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.cardInnerPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(dims.buttonCornerRadius - 2.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ExtremeCard(
    label: String,
    title: String,
    subtitle: String,
    posterPath: String?,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val dims = Dimens.current
    val pUrl = posterUrl(posterPath, "w200")

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(dims.buttonCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 58.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (pUrl == null && fallbackIcon != null) accent.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (pUrl != null) {
                    PosterImage(url = pUrl, description = title)
                } else if (fallbackIcon != null) {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Formatting
// ─────────────────────────────────────────────────────────────────────────────

/** TMDB paths get the image host; full URLs (demo data) are used as-is. */
private fun posterUrl(path: String?, size: String): String? = when {
    path.isNullOrBlank() -> null
    path.startsWith("http") -> path
    else -> "https://image.tmdb.org/t/p/$size$path"
}

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

private fun plural(count: Int, one: String, many: String): String = "$count ${if (count == 1) one else many}"
