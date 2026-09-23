package com.example.dailytrack_mobile.presentation.screens.sabdekho.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dailytrack_mobile.data.remote.dto.MediaDiaryLogDto
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.example.dailytrack_mobile.presentation.components.rememberSheetHeight

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditDiaryLogSheet(
    log: MediaDiaryLogDto,
    onDismiss: () -> Unit,
    isSaving: Boolean = false,
    errorMessage: String? = null,
    onSave: (
        rating: Float?,
        review: String?,
        liked: Boolean?,
        rewatch: Boolean?,
        tag: String?,
        date: String?,
        season: Int?,
        episode: Int?
    ) -> Unit,
    onDelete: () -> Unit
) {
    val dims = Dimens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var rating by remember { mutableFloatStateOf(log.rating ?: 0f) }
    var review by remember { mutableStateOf(log.review ?: "") }
    var liked by remember { mutableStateOf(log.liked) }
    var rewatch by remember { mutableStateOf(log.rewatch) }
    var tags by remember { mutableStateOf(log.tags ?: "") }
    var seasonNumber by remember { mutableStateOf(log.seasonNumber?.toString() ?: "") }
    var episodeNumber by remember { mutableStateOf(log.episodeNumber?.toString() ?: "") }

    val initialDate = remember(log.date) {
        try {
            if (!log.date.isNullOrBlank()) LocalDate.parse(log.date.take(10)) else LocalDate.now()
        } catch (e: Exception) {
            LocalDate.now()
        }
    }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, dd MMM yyyy") }
    val isMovie = log.type.equals("movie", ignoreCase = true)

    val quickTags = listOf("Theatre", "IMAX", "Netflix", "Prime Video", "Apple TV+", "Hotstar", "4K UHD")

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Watch Log?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this diary entry? This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(dims.buttonCornerRadius)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    shape = RoundedCornerShape(dims.buttonCornerRadius)
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(dims.cardCornerRadius)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(rememberSheetHeight(0.9f))
                .navigationBarsPadding()
                .padding(horizontal = dims.screenHorizontalPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header Row: Title + Delete + Close ───────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Watch Log",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Log",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ── Media Info Card ──────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(dims.cardCornerRadius),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val posterUrl = if (log.posterPath != null) "https://image.tmdb.org/t/p/w185${log.posterPath}" else ""
                    Box(
                        modifier = Modifier
                            .size(width = 50.dp, height = 75.dp)
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
                                contentDescription = log.showName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = if (isMovie) Icons.Default.Movie else Icons.Default.Tv,
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
                                    text = if (isMovie) "🎬 FILM" else "📺 SERIES",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            val dispSeason = seasonNumber.toIntOrNull()
                            val dispEpisode = episodeNumber.toIntOrNull()
                            if (!isMovie && (dispSeason != null || dispEpisode != null)) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    val epText = buildString {
                                        if (dispSeason != null) append("S$dispSeason")
                                        if (dispEpisode != null) append(" E$dispEpisode")
                                    }
                                    Text(
                                        text = epText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = log.showName ?: "Unknown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ── Watch Date Row ───────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "DATE WATCHED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Date",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
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
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ── Season & Episode (For Series) ─────────────────────────────────
            if (!isMovie) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "SEASON & EPISODE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = seasonNumber,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() } && input.length <= 3) {
                                    seasonNumber = input
                                }
                            },
                            label = { Text("Season") },
                            placeholder = { Text("e.g. 1") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(dims.buttonCornerRadius),
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )

                        OutlinedTextField(
                            value = episodeNumber,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() } && input.length <= 4) {
                                    episodeNumber = input
                                }
                            },
                            label = { Text("Episode") },
                            placeholder = { Text("e.g. 5") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(dims.buttonCornerRadius),
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // ── Star Rating Bar ──────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RATING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StarRatingBar(
                            rating = rating,
                            onRatingChanged = { rating = it },
                            starSize = 32.dp
                        )

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

            // ── Liked & Rewatch Toggles ──────────────────────────────────────
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

            // ── Platform / Experience Quick Tags ─────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "EXPERIENCE / PLATFORM TAGS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

            // ── Review / Notes ───────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "REVIEW / NOTES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = review,
                    onValueChange = { review = it },
                    placeholder = { Text("What did you think of the story, acting, direction?", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(dims.iconSizeMedium)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 160.dp),
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // ── Save Changes Primary Button ──────────────────────────────────
            Button(
                enabled = !isSaving,
                onClick = {
                    onSave(
                        rating.takeIf { it > 0f },
                        review.takeIf { it.isNotBlank() },
                        liked,
                        rewatch,
                        tags.takeIf { it.isNotBlank() },
                        selectedDate.toString(),
                        seasonNumber.toIntOrNull(),
                        episodeNumber.toIntOrNull()
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dims.searchBarHeight),
                shape = RoundedCornerShape(dims.buttonCornerRadius)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = if (isSaving) "Saving…" else "Save Changes",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
