package com.example.dailytrack_mobile.presentation.screens.sabdekho.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dailytrack_mobile.data.remote.dto.MediaSearchResultDto
import com.example.dailytrack_mobile.data.remote.dto.MediaShowDto
import com.example.dailytrack_mobile.presentation.screens.sabdekho.SabdekhoAction
import com.example.dailytrack_mobile.presentation.screens.sabdekho.SabdekhoState
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.time.LocalDate
import com.example.dailytrack_mobile.presentation.components.rememberSheetHeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailsSheet(
    state: SabdekhoState,
    onAction: (SabdekhoAction) -> Unit
) {
    val show = state.selectedShow ?: return
    val dims = Dimens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isMovie = show.type?.equals("movie", ignoreCase = true) == true
    val details = state.detailsData
    val canEdit = com.example.dailytrack_mobile.presentation.access.LocalAccess.current.canEdit(com.example.dailytrack_mobile.data.local.auth.AccessModule.SABDEKHO)
    // Sub-tab ids: 0 Log • 1 History • 2 Details • 3 Match. Viewers can't log or re-match.
    val visibleSubTabs = if (canEdit) listOf(0, 1, 2, 3) else listOf(1, 2)
    LaunchedEffect(show.id, canEdit) {
        if (state.detailsSheetSubTab !in visibleSubTabs) onAction(SabdekhoAction.SetDetailsSubTab(visibleSubTabs.first()))
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { onAction(SabdekhoAction.CloseMediaDetails) },
        contentWindowInsets = com.example.dailytrack_mobile.presentation.components.SheetContentInsets,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(rememberSheetHeight(0.92f))
                .navigationBarsPadding()
        ) {
            // ── Top Backdrop Header Banner ──────────────────────────────────
            val backdropUrl = remember(details) {
                details?.backdropPath?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w780$it" } ?: ""
            }
            val posterUrl = if (show.posterPath != null) "https://image.tmdb.org/t/p/w342${show.posterPath}" else ""

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    )
            ) {
                if (backdropUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(backdropUrl)
                            .crossfade(400)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Gradient overlays
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Black.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                )

                // Close Button
                IconButton(
                    onClick = { onAction(SabdekhoAction.CloseMediaDetails) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(dims.itemSpacingMedium)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Header Info overlay (Poster + Title + Type + Rating)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = dims.screenHorizontalPadding, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Floating Poster
                    Box(
                        modifier = Modifier
                            .size(width = 75.dp, height = 110.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (posterUrl.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(posterUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = show.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = if (isMovie) Icons.Default.Movie else Icons.Default.Tv,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = show.name ?: "Unknown Title",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            Surface(
                                color = if (isMovie) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isMovie) "🎬 Movie" else "📺 Series",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMovie) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            val tmdbRating = details?.voteAverage
                            if (tmdbRating != null && tmdbRating > 0) {
                                Text(
                                    text = "⭐ ${String.format(java.util.Locale.US, "%.1f", tmdbRating)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldenStarColor
                                )
                            }

                            if (isMovie) {
                                val year = details?.releaseDate?.take(4)
                                if (!year.isNullOrEmpty()) {
                                    Text(
                                        text = year,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (details?.runtime != null && details.runtime > 0) {
                                    val hrs = details.runtime / 60
                                    val mins = details.runtime % 60
                                    val runStr = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
                                    Text(
                                        text = runStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                details?.director?.let { dir ->
                                    Text(
                                        text = "Dir. $dir",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                val sCount = details?.numberOfSeasons ?: details?.seasons?.count { it.season_number > 0 } ?: 1
                                val epCount = details?.numberOfEpisodes
                                val metaStr = buildString {
                                    append("$sCount Season${if (sCount != 1) "s" else ""}")
                                    if (epCount != null && epCount > 0) {
                                        append(" · $epCount Eps")
                                    }
                                }
                                Text(
                                    text = metaStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Status Picker Segmented Row ─────────────────────────────────
            val statuses = if (isMovie) {
                listOf("WATCHED", "TO WATCH")
            } else {
                listOf("WATCHING", "WATCHED", "TO WATCH", "DROPPED")
            }
            val statusDisplayNames = mapOf(
                "WATCHING" to "Watching",
                "TO WATCH" to "To Watch",
                "WATCHED" to "Watched",
                "DROPPED" to "Dropped"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dims.screenHorizontalPadding, vertical = 8.dp)
                    .clip(RoundedCornerShape(dims.buttonCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                statuses.filter { canEdit || show.status.equals(it, ignoreCase = true) }.forEach { st ->
                    val isCurrent = show.status.equals(st, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(dims.buttonCornerRadius - 2.dp))
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable(enabled = canEdit) {
                                onAction(SabdekhoAction.UpdateShowStatus(show.id, isMovie, st))
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = statusDisplayNames[st] ?: st,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Sub-Tabs Switcher (Log • History • Details • Match) ──────────
            val subTabTitles = listOf("Log", "History", "Details", "Match")
            SecondaryTabRow(
                selectedTabIndex = visibleSubTabs.indexOf(state.detailsSheetSubTab).coerceAtLeast(0),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) }
            ) {
                subTabTitles.forEachIndexed { index, title ->
                    if (index in visibleSubTabs) Tab(
                        selected = state.detailsSheetSubTab == index,
                        onClick = { onAction(SabdekhoAction.SetDetailsSubTab(index)) },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (state.detailsSheetSubTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    )
                }
            }

            // ── Sub-Tab Contents ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (if (state.detailsSheetSubTab in visibleSubTabs) state.detailsSheetSubTab else visibleSubTabs.first()) {
                    0 -> LogSubTab(state = state, show = show, isMovie = isMovie, details = details, onAction = onAction)
                    1 -> HistorySubTab(state = state, show = show, onAction = onAction)
                    2 -> DetailsSubTab(details = details, isLoading = state.isLoadingDetails, isMovie = isMovie)
                    3 -> MatchSubTab(
                        state = state,
                        show = show,
                        isMovie = isMovie,
                        onAction = onAction,
                        onDeleteClick = { showDeleteConfirm = true }
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Title?") },
            text = { Text("Are you sure you want to remove \"${show.name}\" from your library and delete its logs?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onAction(SabdekhoAction.DeleteShow(show.id, isMovie))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-Tab 0: Log Watch Entry
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LogSubTab(
    state: SabdekhoState,
    show: MediaShowDto,
    isMovie: Boolean,
    details: com.example.dailytrack_mobile.data.remote.dto.MediaDetailsDataDto?,
    onAction: (SabdekhoAction) -> Unit
) {
    val dims = Dimens.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingMedium),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Star Rating Bar
        Column {
            Text(
                text = "YOUR RATING",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            StarRatingBar(
                rating = state.logRating,
                onRatingChanged = { onAction(SabdekhoAction.UpdateLogForm(rating = it)) },
                starSize = 34.dp
            )
        }

        // Liked & Rewatch Toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = state.logLiked,
                onClick = { onAction(SabdekhoAction.UpdateLogForm(liked = !state.logLiked)) },
                label = { Text("Liked") },
                leadingIcon = {
                    Icon(
                        imageVector = if (state.logLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = if (state.logLiked) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = RoundedCornerShape(dims.buttonCornerRadius)
            )

            FilterChip(
                selected = state.logRewatch,
                onClick = { onAction(SabdekhoAction.UpdateLogForm(rewatch = !state.logRewatch)) },
                label = { Text("Rewatch") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = RoundedCornerShape(dims.buttonCornerRadius)
            )
        }

        // If Series: Interactive Season & Episode Picker
        if (!isMovie) {
            TvSeasonEpisodePicker(
                state = state,
                details = details,
                onAction = onAction
            )
        }

        // Date Picker / Field
        OutlinedTextField(
            value = state.logDate,
            onValueChange = { onAction(SabdekhoAction.UpdateLogForm(date = it)) },
            label = { Text("Date (YYYY-MM-DD)") },
            leadingIcon = {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(dims.buttonCornerRadius),
            singleLine = true
        )

        // Platform / Theatre Quick Tags
        Column {
            Text(
                text = "TAGS / STREAMING PLATFORM",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            val quickTags = listOf("IMAX", "Theatre", "Netflix", "Prime Video", "Apple TV+", "Disney+", "JioCinema")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickTags.forEach { tag ->
                    val isTagActive = state.logPlatformTag.contains(tag, ignoreCase = true)
                    SuggestionChip(
                        onClick = {
                            val newTag = if (isTagActive) {
                                state.logPlatformTag.replace(tag, "").replace(", ,", ",").trim(',', ' ')
                            } else {
                                if (state.logPlatformTag.isBlank()) tag else "${state.logPlatformTag}, $tag"
                            }
                            onAction(SabdekhoAction.UpdateLogForm(platformTag = newTag))
                        },
                        label = {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isTagActive) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (isTagActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = state.logPlatformTag,
                onValueChange = { onAction(SabdekhoAction.UpdateLogForm(platformTag = it)) },
                placeholder = { Text("Custom tags (e.g. IMAX, Rewatch with friends)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(dims.buttonCornerRadius),
                singleLine = true
            )
        }

        // Review Text
        OutlinedTextField(
            value = state.logReview,
            onValueChange = { onAction(SabdekhoAction.UpdateLogForm(review = it)) },
            label = { Text("Review / Notes") },
            placeholder = { Text("What did you think about this film or episode?") },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            shape = RoundedCornerShape(dims.buttonCornerRadius),
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Save Button
        Button(
            onClick = { onAction(SabdekhoAction.SubmitLog) },
            enabled = !state.isSubmittingLog,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (state.isSubmittingLog) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save Watch Log",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-Tab 1: History of this show
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistorySubTab(
    state: SabdekhoState,
    show: MediaShowDto,
    onAction: (SabdekhoAction) -> Unit
) {
    val dims = Dimens.current
    // Films and TV shows are separate tables with their own ids, so the id
    // alone matches the other kind's logs too — that showed up as "copies".
    val showIsMovie = show.type.equals("movie", ignoreCase = true)
    val showLogs = remember(state.diaryLogs, show.id, showIsMovie) {
        state.diaryLogs.filter { it.showId == show.id && it.type.equals("movie", ignoreCase = true) == showIsMovie }
    }

    if (showLogs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "No logs yet for ${show.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Switch to the 'Log' tab above to record your rating and review.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingMedium),
            verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(showLogs, key = { "${it.type}-${it.id}" }) { log ->
                DiaryLogCard(
                    log = log,
                    onCardClick = {},
                    onEditClick = { onAction(SabdekhoAction.OpenEditLog(log)) },
                    onDeleteClick = { onAction(SabdekhoAction.DeleteLog(log.id, log.type.equals("movie", ignoreCase = true))) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvSeasonEpisodePicker(
    state: SabdekhoState,
    details: com.example.dailytrack_mobile.data.remote.dto.MediaDetailsDataDto?,
    onAction: (SabdekhoAction) -> Unit
) {
    val dims = Dimens.current
    val show = state.selectedShow ?: return

    val seasons = remember(details?.seasons) {
        val valid = details?.seasons?.filter { it.season_number > 0 && it.episode_count > 0 } ?: emptyList()
        if (valid.isNotEmpty()) valid
        else listOf(
            com.example.dailytrack_mobile.data.remote.dto.MediaSeasonDto(season_number = 1, episode_count = 10, name = "Season 1"),
            com.example.dailytrack_mobile.data.remote.dto.MediaSeasonDto(season_number = 2, episode_count = 10, name = "Season 2")
        )
    }

    fun isSeasonWatched(seasonNum: Int): Boolean {
        val showLogs = state.diaryLogs.filter { it.showId == show.id && !it.type.equals("movie", ignoreCase = true) }
        if (showLogs.any { it.seasonNumber == null }) return true
        val seasonData = seasons.find { it.season_number == seasonNum }
        val totalEps = seasonData?.episode_count ?: 0
        if (totalEps == 0) return showLogs.any { it.seasonNumber == seasonNum }
        val loggedEps = showLogs.filter { it.seasonNumber == seasonNum && it.episodeNumber != null }.mapNotNull { it.episodeNumber }.toSet()
        val fullSeasonLogged = showLogs.any { it.seasonNumber == seasonNum && it.episodeNumber == null }
        return fullSeasonLogged || loggedEps.size >= totalEps
    }

    fun isEpisodeWatched(ep: Int): Boolean {
        val showLogs = state.diaryLogs.filter { it.showId == show.id && !it.type.equals("movie", ignoreCase = true) }
        return showLogs.any {
            it.seasonNumber == null ||
                    (it.seasonNumber == state.logSeason && (it.episodeNumber == null || it.episodeNumber == ep))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Seasons Section
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SEASONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = if (state.logSeason == null) "Whole Show" else "Season ${state.logSeason}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isAllSeasons = state.logSeason == null
                Surface(
                    color = if (isAllSeasons) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    border = if (isAllSeasons) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.clickable {
                        onAction(SabdekhoAction.SelectLogSeason(null))
                    }
                ) {
                    Text(
                        text = "All",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isAllSeasons) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isAllSeasons) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }

                seasons.forEach { s ->
                    val isSelected = state.logSeason == s.season_number
                    val isWatched = isSeasonWatched(s.season_number)

                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else if (isWatched) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        else if (isWatched) BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.6f))
                        else null,
                        modifier = Modifier.clickable {
                            onAction(SabdekhoAction.SelectLogSeason(s.season_number))
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "S${s.season_number}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else if (isWatched) Color(0xFF4CAF50)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isWatched) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Watched",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Episodes Section (visible when a season is selected)
        if (state.logSeason != null) {
            val seasonData = seasons.find { it.season_number == state.logSeason }
            val totalEps = seasonData?.episode_count ?: 10
            val allEpsList = remember(totalEps) { (1..totalEps).toList() }
            val isAllEpsSelected = state.selectedLogEpisodes.size == totalEps

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dims.buttonCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(dims.cardInnerPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EPISODES FOR S${state.logSeason}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )

                    Text(
                        text = when {
                            state.selectedLogEpisodes.isEmpty() -> "Entire Season"
                            state.selectedLogEpisodes.size == 1 -> "Ep ${state.selectedLogEpisodes.first()}"
                            else -> "${state.selectedLogEpisodes.size} Eps Selected"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = if (isAllEpsSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable {
                            onAction(SabdekhoAction.ToggleAllLogEpisodes)
                        }
                    ) {
                        Text(
                            text = "All",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isAllEpsSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isAllEpsSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }

                    allEpsList.forEach { ep ->
                        val isSelected = state.selectedLogEpisodes.contains(ep)
                        val isWatched = isEpisodeWatched(ep)

                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else if (isWatched) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            border = if (isSelected) null
                            else if (isWatched) BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f))
                            else null,
                            modifier = Modifier.clickable {
                                onAction(SabdekhoAction.ToggleLogEpisode(ep))
                            }
                        ) {
                            Text(
                                text = ep.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else if (isWatched) Color(0xFF4CAF50)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-Tab 2: Synopsis & TMDB Credits
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailsSubTab(
    details: com.example.dailytrack_mobile.data.remote.dto.MediaDetailsDataDto?,
    isLoading: Boolean,
    isMovie: Boolean
) {
    val dims = Dimens.current

    if (isLoading && details == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (details == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Details unavailable from TMDB",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingMedium),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Genres Chips
        if (!details.genres.isNullOrEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                details.genres.forEach { genre ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = genre.name ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Synopsis
        if (!details.overview.isNullOrBlank()) {
            Column {
                Text(
                    text = "SYNOPSIS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = details.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Director
        details.director?.let { dir ->
            Column {
                Text(
                    text = "DIRECTOR",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dir,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Cast List
        val castList = details.allCast
        if (castList.isNotEmpty()) {
            Column {
                Text(
                    text = "TOP CAST",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(castList.take(12)) { castMember ->
                        val profileUrl = if (castMember.profile_path != null) "https://image.tmdb.org/t/p/w185${castMember.profile_path}" else ""
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(72.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (profileUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(profileUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = castMember.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = castMember.name ?: "",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = castMember.displayCharacter,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
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

        // TV Seasons Breakdown (for series)
        if (!isMovie && !details.seasons.isNullOrEmpty()) {
            val validSeasons = details.seasons.filter { it.season_number > 0 }
            if (validSeasons.isNotEmpty()) {
                Column {
                    Text(
                        text = "SEASONS BREAKDOWN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        validSeasons.forEach { s ->
                            Card(
                                shape = RoundedCornerShape(dims.cardCornerRadius),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val posterUrl = if (s.poster_path != null) "https://image.tmdb.org/t/p/w185${s.poster_path}" else ""
                                    Box(
                                        modifier = Modifier
                                            .size(width = 38.dp, height = 54.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (posterUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(posterUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = s.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Tv,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = s.name ?: "Season ${s.season_number}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${s.episode_count} Episodes",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-Tab 3: Match / Rematch via TMDB & Danger Zone
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MatchSubTab(
    state: SabdekhoState,
    show: MediaShowDto,
    isMovie: Boolean,
    onAction: (SabdekhoAction) -> Unit,
    onDeleteClick: () -> Unit
) {
    val dims = Dimens.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingMedium),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "FIX MATCH (TMDB)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )

        // Search Field
        OutlinedTextField(
            value = state.rematchQuery,
            onValueChange = { onAction(SabdekhoAction.SearchRematch(it)) },
            placeholder = { Text("Search TMDB for correct title...") },
            trailingIcon = {
                if (state.isSearchingRematch) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(dims.buttonCornerRadius),
            singleLine = true
        )

        // Rematch Results
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.rematchResults) { res ->
                val pUrl = if (res.posterPath != null) "https://image.tmdb.org/t/p/w200${res.posterPath}" else ""
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 56.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (pUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(pUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = res.displayTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${if (res.isMovie) "Movie" else "Series"} • ${res.year}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { onAction(SabdekhoAction.ApplyRematch(show.id, isMovie, res)) }
                        ) {
                            Text("Match")
                        }
                    }
                }
            }
        }

        // Danger Zone: Delete
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        OutlinedButton(
            onClick = onDeleteClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete from Library")
        }
    }
}
