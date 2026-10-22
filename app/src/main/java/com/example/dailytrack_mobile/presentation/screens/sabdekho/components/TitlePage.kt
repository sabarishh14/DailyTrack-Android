package com.example.dailytrack_mobile.presentation.screens.sabdekho.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dailytrack_mobile.data.local.auth.AccessModule
import com.example.dailytrack_mobile.data.remote.dto.MediaCastMemberDto
import com.example.dailytrack_mobile.data.remote.dto.MediaDetailsDataDto
import com.example.dailytrack_mobile.data.remote.dto.MediaDiaryLogDto
import com.example.dailytrack_mobile.data.remote.dto.MediaShowDto
import com.example.dailytrack_mobile.presentation.access.LocalAccess
import com.example.dailytrack_mobile.presentation.components.FullScreenPage
import com.example.dailytrack_mobile.presentation.components.navigationBarPadding
import com.example.dailytrack_mobile.presentation.screens.sabdekho.SabdekhoAction
import com.example.dailytrack_mobile.presentation.screens.sabdekho.SabdekhoState
import com.example.dailytrack_mobile.presentation.screens.sabdekho.SeasonInfo
import com.example.dailytrack_mobile.presentation.screens.sabdekho.WatchedEpisodes
import com.example.dailytrack_mobile.presentation.screens.sabdekho.episodeLabel
import com.example.dailytrack_mobile.presentation.screens.sabdekho.groupDiaryLogs
import com.example.dailytrack_mobile.presentation.screens.sabdekho.seasonsOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BackdropHeight = 220.dp
private val HeroHeight = 300.dp
private const val DiaryPreview = 5
private val BadgeMonth = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

private val MovieStatuses = listOf("WATCHED", "TO WATCH")
private val ShowStatuses = listOf("WATCHING", "WATCHED", "TO WATCH", "DROPPED")

private fun statusLabel(status: String): String = when (status.uppercase()) {
    "WATCHING" -> "Watching"
    "WATCHED" -> "Watched"
    "TO WATCH" -> "To watch"
    "DROPPED" -> "Dropped"
    else -> status.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * A film's or show's own page: what it is, where it stands, your diary of it,
 * how far through a show you are, and a Log button always in reach.
 */
@Composable
fun TitlePage(state: SabdekhoState, onAction: (SabdekhoAction) -> Unit) {
    val show = state.selectedShow ?: return
    val isMovie = show.type.equals("movie", ignoreCase = true)
    val canEdit = LocalAccess.current.canEdit(AccessModule.SABDEKHO)
    val details = state.detailsData
    val close = { onAction(SabdekhoAction.CloseMediaDetails) }
    var confirmRemove by remember { mutableStateOf(false) }

    val rows = remember(state.selectedShowLogs) { groupDiaryLogs(state.selectedShowLogs) }
    val summary = remember(state.selectedShowLogs, rows) {
        if (rows.isEmpty()) null else buildString {
            append(if (rows.size == 1) "1 log" else "${rows.size} logs")
            val ratings = state.selectedShowLogs.mapNotNull { it.rating?.takeIf { r -> r > 0f } }
            if (ratings.isNotEmpty()) append(" · ★ ${String.format(Locale.US, "%.1f", ratings.average())} avg")
        }
    }
    val watched = remember(state.selectedShowLogs) { WatchedEpisodes(state.selectedShowLogs) }
    // Progress only against TMDB's real seasons, never the fallback guesses.
    val seasons = remember(details, state.selectedShowLogs) {
        if (isMovie || details?.seasons.isNullOrEmpty()) emptyList<SeasonInfo>()
        else seasonsOf(details, state.selectedShowLogs, emptySet(), emptySet())
    }

    FullScreenPage(onDismiss = close) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = if (canEdit) 24.dp else 24.dp + navigationBarPadding())
        ) {
            item(key = "hero") {
                TitleHero(
                    show = show,
                    details = details,
                    isMovie = isMovie,
                    summary = summary,
                    canEdit = canEdit,
                    onBack = close,
                    onFixMatch = { onAction(SabdekhoAction.OpenFixMatch) },
                    onRemove = { confirmRemove = true }
                )
            }
            item(key = "status") {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
                    if (canEdit) {
                        StatusButtons(
                            statuses = if (isMovie) MovieStatuses else ShowStatuses,
                            current = show.status,
                            onSelect = { onAction(SabdekhoAction.UpdateShowStatus(show.id, isMovie, it)) }
                        )
                    } else if (!show.status.isNullOrBlank()) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                text = statusLabel(show.status),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                    state.statusNote?.let { note ->
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            if (rows.isNotEmpty()) {
                item(key = "diary") {
                    TitleDiary(
                        rows = rows,
                        isMovie = isMovie,
                        onOpen = if (canEdit) ({ log: MediaDiaryLogDto -> onAction(SabdekhoAction.OpenEditLog(log)) }) else null
                    )
                }
            }
            if (seasons.isNotEmpty()) {
                item(key = "seasons") {
                    TitleSeasons(
                        seasons = seasons,
                        watched = watched,
                        onLogSeason = if (canEdit) ({ season: Int -> onAction(SabdekhoAction.OpenLogPage(season)) }) else null
                    )
                }
            }
            when {
                details != null -> {
                    item(key = "about") { TitleAbout(details = details, isMovie = isMovie) }
                    val cast = details.allCast
                    if (cast.isNotEmpty()) item(key = "cast") { TitleCast(cast = cast) }
                }
                state.isLoadingDetails -> item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
            }
        }
        if (canEdit) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = { onAction(SabdekhoAction.OpenLogPage()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Log", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${show.name ?: "this"}?") },
            text = { Text("Its logs go too.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onAction(SabdekhoAction.DeleteShow(show.id, isMovie))
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TitleHero(
    show: MediaShowDto,
    details: MediaDetailsDataDto?,
    isMovie: Boolean,
    summary: String?,
    canEdit: Boolean,
    onBack: () -> Unit,
    onFixMatch: () -> Unit,
    onRemove: () -> Unit
) {
    val surface = MaterialTheme.colorScheme.surface
    val backdrop = details?.backdropPath?.takeIf { it.isNotBlank() }
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeroHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BackdropHeight)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (backdrop != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("$TmdbImages/w780$backdrop")
                        .crossfade(300)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.4f to Color.Transparent,
                            1f to surface
                        )
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScrimIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.weight(1f))
            if (canEdit) {
                Box {
                    ScrimIconButton(Icons.Default.MoreVert, "More") { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Fix match") },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onFixMatch()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove from library", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TitlePoster(
                path = show.posterPath,
                isMovie = isMovie,
                modifier = Modifier.size(width = 100.dp, height = 150.dp),
                corner = 12.dp
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = show.name ?: details?.displayTitle ?: "Untitled",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                heroMeta(details, isMovie)?.let { meta ->
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                summary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** "2021 · 2h 35m · TMDB 8.1" or "2017 · 3 seasons · TMDB 8.4". */
private fun heroMeta(details: MediaDetailsDataDto?, isMovie: Boolean): String? {
    details ?: return null
    val parts = mutableListOf<String>()
    (if (isMovie) details.releaseDate else details.firstAirDate)?.take(4)?.takeIf { it.length == 4 }?.let { parts += it }
    if (isMovie) {
        details.runtime?.takeIf { it > 0 }?.let { parts += if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" }
    } else {
        val count = details.numberOfSeasons ?: details.seasons?.count { it.season_number > 0 }
        count?.takeIf { it > 0 }?.let { parts += if (it == 1) "1 season" else "$it seasons" }
    }
    details.voteAverage?.takeIf { it > 0 }?.let { parts += "TMDB ${String.format(Locale.US, "%.1f", it)}" }
    return parts.joinToString(" · ").ifEmpty { null }
}

/** An icon over the backdrop, on a dark circle so it shows on any picture. */
@Composable
private fun ScrimIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.4f),
            contentColor = Color.White
        )
    ) {
        Icon(icon, contentDescription = description)
    }
}

/** Where a title stands, one tap to change it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusButtons(statuses: List<String>, current: String?, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically
    ) {
        statuses.forEachIndexed { index, status ->
            val isCurrent = current.equals(status, ignoreCase = true)
            ToggleButton(
                checked = isCurrent,
                onCheckedChange = { if (!isCurrent) onSelect(status) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    statuses.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton }
            ) {
                Text(
                    text = statusLabel(status),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Your diary
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TitleDiary(rows: List<MediaDiaryLogDto>, isMovie: Boolean, onOpen: ((MediaDiaryLogDto) -> Unit)?) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val shown = if (showAll) rows else rows.take(DiaryPreview)

    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp)) {
        PageSectionTitle(title = "Your diary", trailing = if (rows.size > 1) "${rows.size}" else null)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            shown.forEachIndexed { i, log ->
                if (i > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 66.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
                TitleDiaryRow(log = log, isMovie = isMovie, onOpen = onOpen)
            }
        }
        if (rows.size > DiaryPreview) {
            TextButton(onClick = { showAll = !showAll }) {
                Text(if (showAll) "Show less" else "Show all ${rows.size}")
            }
        }
    }
}

@Composable
private fun TitleDiaryRow(log: MediaDiaryLogDto, isMovie: Boolean, onOpen: ((MediaDiaryLogDto) -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val tags = remember(log.tags) { log.tags?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable { onOpen(log) } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DateBadge(log.date)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = whatWasLogged(log, isMovie),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                StarDisplay(rating = log.rating, starSize = 13.dp, showNumeric = false)
                if (log.liked) {
                    Icon(Icons.Filled.Favorite, contentDescription = "Liked", tint = LikedPink, modifier = Modifier.size(14.dp))
                }
                if (log.rewatch && !isMovie) {
                    Icon(Icons.Default.Autorenew, contentDescription = "Rewatch", tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                }
            }
            log.review?.trim()?.takeIf { it.isNotEmpty() }?.let { review ->
                Text(
                    text = review,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (tags.isNotEmpty()) {
                Text(
                    text = tags.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onOpen != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Edit",
                tint = colors.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/** The day big, the month small, the year only when it isn't this one. */
@Composable
private fun DateBadge(date: String?) {
    val day = remember(date) { date?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
    Column(modifier = Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = day?.dayOfMonth?.toString() ?: "–",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (day != null) {
            Text(
                text = day.format(BadgeMonth).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (day.year != LocalDate.now().year) {
                Text(
                    text = "${day.year}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun whatWasLogged(log: MediaDiaryLogDto, isMovie: Boolean): String {
    if (isMovie) return if (log.rewatch) "Rewatched" else "Watched"
    val season = log.seasonNumber ?: return "Whole show"
    val episode = log.episodeNumber ?: return "Season $season"
    return episodeLabel(season, log.episodes.ifEmpty { listOf(episode) })
}

// ─────────────────────────────────────────────────────────────────────────────
// Seasons
// ─────────────────────────────────────────────────────────────────────────────

/** How far through each season you are; a tap logs in it. */
@Composable
private fun TitleSeasons(seasons: List<SeasonInfo>, watched: WatchedEpisodes, onLogSeason: ((Int) -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val totalSeen = seasons.sumOf { watched.watchedIn(it.number, it.episodeCount) }
    val total = seasons.sumOf { it.episodeCount }

    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp)) {
        PageSectionTitle(title = "Seasons", trailing = "$totalSeen/$total")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceContainer)
        ) {
            seasons.forEachIndexed { i, s ->
                if (i > 0) {
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f))
                }
                val seen = watched.watchedIn(s.number, s.episodeCount)
                val done = seen >= s.episodeCount
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (onLogSeason != null) Modifier.clickable { onLogSeason(s.number) } else Modifier)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Season ${s.number}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (done) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = WatchedGreen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = "$seen/${s.episodeCount}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (done) WatchedGreen else colors.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (s.episodeCount > 0) seen.toFloat() / s.episodeCount else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = if (done) WatchedGreen else colors.primary,
                            trackColor = colors.surfaceContainerHighest,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                    }
                    if (onLogSeason != null) {
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Default.Add, contentDescription = "Log season ${s.number}", tint = colors.primary)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// About & cast
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TitleAbout(details: MediaDetailsDataDto, isMovie: Boolean) {
    val overview = details.overview?.trim().orEmpty()
    val genres = details.genres.orEmpty().mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
    val director = if (isMovie) details.director else null
    if (overview.isEmpty() && genres.isEmpty() && director == null) return
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp)) {
        PageSectionTitle(title = "About")
        if (genres.isNotEmpty()) {
            Text(
                text = genres.joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        if (overview.isNotEmpty()) {
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .animateContentSize()
                    .clickable { expanded = !expanded }
            )
        }
        director?.let {
            Text(
                text = "Directed by $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun TitleCast(cast: List<MediaCastMemberDto>) {
    Column(modifier = Modifier.padding(top = 28.dp)) {
        PageSectionTitle(title = "Cast", modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(cast.take(15)) { member ->
                Column(
                    modifier = Modifier.width(72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!member.profile_path.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data("$TmdbImages/w185${member.profile_path}")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = member.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = member.name.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    if (member.displayCharacter.isNotBlank()) {
                        Text(
                            text = member.displayCharacter,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
