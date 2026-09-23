package com.example.dailytrack_mobile.presentation.screens.sabdekho.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dailytrack_mobile.data.remote.dto.MediaDiaryLogDto
import com.example.dailytrack_mobile.data.remote.dto.MediaShowDto
import com.example.dailytrack_mobile.presentation.screens.sabdekho.SabdekhoAction
import com.example.dailytrack_mobile.presentation.screens.sabdekho.SabdekhoState
import com.example.dailytrack_mobile.presentation.util.Dimens

@Composable
fun SabdekhoDiaryTab(
    state: SabdekhoState,
    onAction: (SabdekhoAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current

    val filteredLogs = remember(state.diaryLogs, state.diaryTypeFilter) {
        when (state.diaryTypeFilter.lowercase()) {
            "movie" -> state.diaryLogs.filter { it.type.equals("movie", ignoreCase = true) }
            "tv" -> state.diaryLogs.filter { !it.type.equals("movie", ignoreCase = true) }
            else -> state.diaryLogs
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Filter Pills Row (All / Movies / Series)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dims.itemSpacingSmall),
            horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChipView("All Logs", isSelected = state.diaryTypeFilter == "all") {
                onAction(SabdekhoAction.ChangeMediaType("all"))
            }
            FilterChipView("🎬 Films", isSelected = state.diaryTypeFilter == "movie") {
                onAction(SabdekhoAction.ChangeMediaType("movie"))
            }
            FilterChipView("📺 Series", isSelected = state.diaryTypeFilter == "tv") {
                onAction(SabdekhoAction.ChangeMediaType("tv"))
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "${filteredLogs.size} LOGS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(dims.itemSpacingSmall))

        if (state.isDiaryLoading && state.diaryLogs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No diary logs yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Select any movie or show from your library and tap 'Log' to record your watch diary with ratings and reviews.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium),
                contentPadding = PaddingValues(bottom = dims.screenBottomPadding + 56.dp)
            ) {
                // Film and TV log ids can collide, and duplicate keys crash the list.
                items(filteredLogs, key = { "${it.type}-${it.id}" }) { log ->
                    DiaryLogCard(
                        log = log,
                        onCardClick = {
                            // Find corresponding show or construct minimal show to open details
                            val logIsMovie = log.type.equals("movie", ignoreCase = true)
                            val matchedShow = state.shows.find {
                                it.id == log.showId && it.type.equals("movie", ignoreCase = true) == logIsMovie
                            } ?: MediaShowDto(
                                id = log.showId,
                                tmdbId = log.tmdbId,
                                name = log.showName,
                                posterPath = log.posterPath,
                                type = log.type,
                                status = "WATCHED"
                            )
                            onAction(SabdekhoAction.OpenMediaDetails(matchedShow))
                        },
                        onEditClick = { onAction(SabdekhoAction.OpenEditLog(log)) },
                        onDeleteClick = { onAction(SabdekhoAction.DeleteLog(log.id, log.type.equals("movie", ignoreCase = true))) }
                    )
                }
            }
        }
    }
}

@Composable
fun DiaryLogCard(
    log: MediaDiaryLogDto,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dims = Dimens.current
    val canEdit = com.example.dailytrack_mobile.presentation.access.LocalAccess.current.canEdit(com.example.dailytrack_mobile.data.local.auth.AccessModule.SABDEKHO)
    var showMenu by remember { mutableStateOf(false) }
    val isMovie = log.type.equals("movie", ignoreCase = true)
    val posterUrl = if (log.posterPath != null) "https://image.tmdb.org/t/p/w200${log.posterPath}" else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(dims.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.cardInnerPadding)
        ) {
            // Header Row: Date + Season/Episode Badge + Actions Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = log.date ?: "Recently",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (!isMovie) {
                        val epText = when {
                            log.seasonNumber != null && log.episodeNumber != null -> "S%02dE%02d".format(log.seasonNumber, log.episodeNumber)
                            log.seasonNumber != null -> "Season %d".format(log.seasonNumber)
                            else -> "Whole Show"
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = epText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "🎬 Film",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                if (canEdit) Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Log") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEditClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Log", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body Row: Poster Thumbnail + Title, Rating, Likes, Rewatch, Tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Poster
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 78.dp)
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
                            contentDescription = log.showName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (isMovie) Icons.Default.Movie else Icons.Default.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title + Rating + Badges
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = log.showName ?: "Unknown Title",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Rating Stars + Heart + Rewatch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (log.rating != null && log.rating > 0f) {
                            StarDisplay(
                                rating = log.rating,
                                starSize = 13.dp,
                                textStyle = MaterialTheme.typography.labelSmall
                            )
                        }

                        if (log.liked) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Liked",
                                tint = Color(0xFFE91E63),
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        if (log.rewatch) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Autorenew,
                                        contentDescription = "Rewatch",
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "Rewatch",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Tags (IMAX, Theatre, Netflix, etc.)
                    if (!log.tags.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            log.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(3).forEach { tag ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Review Quote Box
            if (!log.review.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(dims.buttonCornerRadius - 4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "\"",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = log.review,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontStyle = FontStyle.Italic,
                                lineHeight = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}
