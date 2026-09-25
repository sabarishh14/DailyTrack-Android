package com.example.dailytrack_mobile.presentation.screens.forms

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.StarHalf
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dailytrack_mobile.data.remote.dto.MediaSearchResultDto
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────────────────────────────────────
// Add Movie / Show Form
// ─────────────────────────────────────────────────────────────────────────────

private enum class WatchStatus(val label: String, val emoji: String, val dbStatus: String) {
    PLAN_TO_WATCH("Plan to Watch", "📋", "TO WATCH"),
    IN_PROGRESS("Watching", "▶️", "WATCHING"),
    WATCHED("Watched", "✅", "WATCHED"),
    DROPPED("Dropped", "❌", "DROPPED")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddMovieScreen(
    formsVM: FormsVM = hiltViewModel(),
    initialMedia: MediaSearchResultDto? = null,
    onDirtyStateChanged: (Boolean) -> Unit = {},
    onSaveSuccess: () -> Unit = {}
) {
    val formState by formsVM.addMovieState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedMedia by remember { mutableStateOf<MediaSearchResultDto?>(initialMedia) }
    var customTitle by remember { mutableStateOf("") }
    var manualMediaType by remember { mutableStateOf("movie") }
    val isMovie = selectedMedia?.isMovie ?: (manualMediaType == "movie")

    var selectedStatus by remember(isMovie) {
        mutableStateOf<WatchStatus?>(if (isMovie) WatchStatus.WATCHED else WatchStatus.IN_PROGRESS)
    }
    var rating by remember { mutableFloatStateOf(0f) }
    var review by remember { mutableStateOf("") }
    var liked by remember { mutableStateOf(false) }
    var rewatch by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf("") }
    var seasonNumber by remember { mutableStateOf("1") }
    var episodeNumber by remember { mutableStateOf("1") }
    var logTvEpisodeNow by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val showLoggingSection = selectedStatus == WatchStatus.WATCHED || (!isMovie && selectedStatus == WatchStatus.IN_PROGRESS && logTvEpisodeNow)
    val quickTags = listOf("Theatre", "IMAX", "Netflix", "Prime Video", "Apple TV+", "Hotstar", "4K UHD")

    LaunchedEffect(initialMedia) {
        if (initialMedia != null) {
            selectedMedia = initialMedia
            searchQuery = ""
            formsVM.clearSearchResults()
        }
    }

    val dims = Dimens.current
    val cardBg = MaterialTheme.colorScheme.surfaceContainer
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }

    val effectiveTitle = selectedMedia?.displayTitle ?: customTitle

    val isDirty = remember(selectedMedia, customTitle, selectedStatus, rating, review, selectedDate, liked, rewatch, tags) {
        selectedMedia != null ||
                customTitle.isNotBlank() ||
                rating > 0f ||
                review.isNotBlank() ||
                liked ||
                rewatch ||
                tags.isNotBlank() ||
                selectedDate != LocalDate.now()
    }

    LaunchedEffect(isDirty) {
        onDirtyStateChanged(isDirty)
    }

    // Trigger search when query changes
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && selectedMedia == null) {
            formsVM.searchMedia(searchQuery)
        } else if (searchQuery.isBlank()) {
            formsVM.clearSearchResults()
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) { Text("OK", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dims.screenHorizontalPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Error Banner ──────────────────────────────────────────────
        formState.errorMessage?.let { errorMsg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { formsVM.clearAddMovieError() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // ── Search TMDB / Enter Title ──────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "SEARCH OR ENTER TITLE",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            if (selectedMedia == null) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        customTitle = it
                    },
                    placeholder = { Text("Search movie or TV show (e.g. Inception, Breaking Bad)", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(dims.iconSizeMedium)
                        )
                    },
                    trailingIcon = {
                        if (formState.isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                customTitle = ""
                                formsVM.clearSearchResults()
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    modifier = Modifier.fillMaxWidth()
                )

                // TMDB Search Results Dropdown List
                if (formState.searchResults.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = cardBorder,
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                            Text(
                                text = "TMDB MATCHES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                            formState.searchResults.take(6).forEach { result ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedMedia = result
                                            searchQuery = result.displayTitle
                                            customTitle = result.displayTitle
                                            formsVM.clearSearchResults()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Poster thumbnail
                                    val posterUrl = if (result.posterPath != null) "https://image.tmdb.org/t/p/w92${result.posterPath}" else ""
                                    Box(
                                        modifier = Modifier
                                            .size(width = 38.dp, height = 56.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (posterUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(posterUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = result.displayTitle,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (result.isMovie) Icons.Default.Movie else Icons.Default.Tv,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Title and Details
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.displayTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (result.year.isNotEmpty()) {
                                                Text(
                                                    text = result.year,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (result.isMovie) "MOVIE" else "TV SERIES",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                            if (result.voteAverage != null && result.voteAverage > 0) {
                                                Text(
                                                    text = "★ ${String.format("%.1f", result.voteAverage)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color(0xFFF59E0B)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ── Selected Media Showcase Card ───────────────────────────
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val posterUrl = if (selectedMedia?.posterPath != null) "https://image.tmdb.org/t/p/w185${selectedMedia?.posterPath}" else ""
                        Box(
                            modifier = Modifier
                                .size(width = 54.dp, height = 80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (posterUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(posterUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = selectedMedia?.displayTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = if (selectedMedia?.isMovie == true) Icons.Default.Movie else Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (selectedMedia?.isMovie == true) "MOVIE" else "TV SERIES",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (!selectedMedia?.year.isNullOrEmpty()) {
                                    Text(
                                        text = selectedMedia!!.year,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = selectedMedia?.displayTitle ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (selectedMedia?.voteAverage != null && selectedMedia!!.voteAverage!! > 0) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "TMDB Rating: ★ ${String.format("%.1f", selectedMedia!!.voteAverage)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFF59E0B),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                selectedMedia = null
                                searchQuery = ""
                                customTitle = ""
                                formsVM.clearSearchResults()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── Manual Type Toggle (if adding custom title) ─────────────
        if (selectedMedia == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TYPE:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilterChip(
                    selected = manualMediaType == "movie",
                    onClick = { manualMediaType = "movie" },
                    label = { Text("🎬 Film") },
                    shape = RoundedCornerShape(dims.buttonCornerRadius)
                )
                FilterChip(
                    selected = manualMediaType == "tv",
                    onClick = { manualMediaType = "tv" },
                    label = { Text("📺 TV Show") },
                    shape = RoundedCornerShape(dims.buttonCornerRadius)
                )
            }
        }

        // ── Watch Status ─────────────────────────────────────────────
        val availableStatuses = remember(isMovie) {
            if (isMovie) {
                listOf(WatchStatus.PLAN_TO_WATCH, WatchStatus.WATCHED, WatchStatus.DROPPED)
            } else {
                listOf(WatchStatus.IN_PROGRESS, WatchStatus.PLAN_TO_WATCH, WatchStatus.WATCHED, WatchStatus.DROPPED)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "WATCH STATUS",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableStatuses.forEach { status ->
                    FilterChip(
                        selected = selectedStatus == status,
                        onClick = { selectedStatus = status },
                        label = { Text("${status.emoji} ${status.label}", style = MaterialTheme.typography.bodyMedium) },
                        shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
                    )
                }
            }
        }

        // ── Plan to Watch Informative Card ───────────────────────────
        if (selectedStatus == WatchStatus.PLAN_TO_WATCH) {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = cardBorder,
                shape = RoundedCornerShape(dims.buttonCornerRadius),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Adding to Watchlist",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ratings, reviews, and watch logs can be added whenever you watch it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ── TV In-Progress Episode Log Toggle ─────────────────────────
        if (selectedStatus == WatchStatus.IN_PROGRESS && !isMovie) {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = cardBorder,
                shape = RoundedCornerShape(dims.buttonCornerRadius),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { logTvEpisodeNow = !logTvEpisodeNow }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Log an episode now?",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Record a watch log in your diary for an episode you just watched",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = logTvEpisodeNow,
                        onCheckedChange = { logTvEpisodeNow = it }
                    )
                }
            }
        }

        // ── Logging Details (Watched / Episode Log) ────────────────────
        AnimatedVisibility(
            visible = showLoggingSection,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "WATCH LOG DETAILS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }

                // TV Series Season & Episode Pickers
                if (!isMovie) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = seasonNumber,
                            onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 3) seasonNumber = it },
                            label = { Text("Season") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(dims.buttonCornerRadius),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = episodeNumber,
                            onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) episodeNumber = it },
                            label = { Text("Episode") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(dims.buttonCornerRadius),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ── Rating (Star Rating) ──────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RATING",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        if (rating > 0f) {
                            Text(
                                text = "★ ${String.format("%.1f", rating)} / 5.0",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = cardBorder,
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (1..5).forEach { star ->
                                    val starFloat = star.toFloat()
                                    val isFull = rating >= starFloat
                                    val isHalf = !isFull && rating >= (starFloat - 0.5f)

                                    IconButton(
                                        onClick = {
                                            rating = if (rating == starFloat) {
                                                starFloat - 0.5f
                                            } else if (rating == (starFloat - 0.5f)) {
                                                0f
                                            } else {
                                                starFloat
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                isFull -> Icons.Outlined.Star
                                                isHalf -> Icons.AutoMirrored.Outlined.StarHalf
                                                else -> Icons.Outlined.StarOutline
                                            },
                                            contentDescription = "$star star",
                                            tint = if (isFull || isHalf) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outlineVariant,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                            }

                            if (rating > 0f) {
                                TextButton(
                                    onClick = { rating = 0f },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // ── Toggles Row: Liked & Rewatch ──────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = liked,
                        onClick = { liked = !liked },
                        label = { Text("Liked") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = if (liked) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = rewatch,
                        onClick = { rewatch = !rewatch },
                        label = { Text("Rewatch") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Autorenew,
                                contentDescription = null,
                                tint = if (rewatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        modifier = Modifier.weight(1f)
                    )
                }

                // ── Watch Date ───────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "DATE LOGGED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = cardBorder,
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = "Select Date",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = selectedDate.format(dateFormatter),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "Change",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // ── Experience / Platform Tags ───────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "TAGS / PLATFORM",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickTags.forEach { tagItem ->
                            val isSelected = tags.contains(tagItem, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    tags = if (isSelected) {
                                        tags.split(",")
                                            .map { it.trim() }
                                            .filterNot { it.equals(tagItem, ignoreCase = true) }
                                            .joinToString(", ")
                                    } else {
                                        if (tags.isBlank()) tagItem else "$tags, $tagItem"
                                    }
                                },
                                label = { Text(tagItem, style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        placeholder = { Text("Custom tags (e.g. IMAX Laser, Netflix, PVR)", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }

                // ── Review Notes ─────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "QUICK REVIEW (OPTIONAL)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    OutlinedTextField(
                        value = review,
                        onValueChange = { review = it },
                        placeholder = { Text("What did you think of the story, acting, direction?", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Outlined.Notes, contentDescription = null, modifier = Modifier.size(dims.iconSizeMedium))
                        },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Save Button ──────────────────────────────────────────────
        val saveButtonText = when (selectedStatus) {
            WatchStatus.PLAN_TO_WATCH -> "Add to Watchlist"
            WatchStatus.WATCHED -> "Save & Log Watch"
            WatchStatus.IN_PROGRESS -> if (logTvEpisodeNow) "Save & Log Episode" else "Start Watching"
            WatchStatus.DROPPED -> "Save as Dropped"
            null -> "Save to Library"
        }

        val saveButtonIcon = when (selectedStatus) {
            WatchStatus.PLAN_TO_WATCH -> Icons.Default.BookmarkAdd
            WatchStatus.DROPPED -> Icons.Default.Close
            else -> Icons.Default.Check
        }

        Button(
            onClick = {
                val relYear = selectedMedia?.year?.toIntOrNull()
                val autoType = if (selectedMedia != null) {
                    if (selectedMedia?.mediaType == "tv") "tv" else "movie"
                } else {
                    manualMediaType
                }

                formsVM.saveMediaShow(
                    tmdbId = selectedMedia?.id,
                    title = effectiveTitle.trim(),
                    type = autoType,
                    status = selectedStatus?.dbStatus ?: "WATCHING",
                    posterPath = selectedMedia?.posterPath,
                    releaseYear = relYear,
                    platform = tags.takeIf { it.isNotBlank() },
                    rating = if (showLoggingSection) rating.takeIf { it > 0f } else null,
                    review = if (showLoggingSection) review.takeIf { it.isNotBlank() } else null,
                    date = selectedDate.toString(),
                    liked = showLoggingSection && liked,
                    rewatch = showLoggingSection && rewatch,
                    seasonNumber = if (showLoggingSection && !isMovie) seasonNumber.toIntOrNull() else null,
                    episodeNumber = if (showLoggingSection && !isMovie) episodeNumber.toIntOrNull() else null,
                    onSuccess = onSaveSuccess
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(dims.searchBarHeight),
                enabled = effectiveTitle.isNotBlank() && selectedStatus != null && !formState.isSaving
        ) {
            if (formState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(saveButtonIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        saveButtonText,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
