package com.example.dailytrack_mobile.presentation.screens.sabdekho.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dailytrack_mobile.presentation.components.FullScreenPage
import com.example.dailytrack_mobile.presentation.screens.sabdekho.SabdekhoAction
import com.example.dailytrack_mobile.presentation.screens.sabdekho.SabdekhoState
import com.example.dailytrack_mobile.presentation.screens.sabdekho.WatchedEpisodes
import com.example.dailytrack_mobile.presentation.screens.sabdekho.addTags
import com.example.dailytrack_mobile.presentation.screens.sabdekho.allLogIds
import com.example.dailytrack_mobile.presentation.screens.sabdekho.hasTag
import com.example.dailytrack_mobile.presentation.screens.sabdekho.logTargetLabel
import com.example.dailytrack_mobile.presentation.screens.sabdekho.seasonsOf
import com.example.dailytrack_mobile.presentation.screens.sabdekho.toggleTag
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ThisYearDay = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val OtherYearDay = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val EpisodeTileSize = 44.dp

/**
 * Logging a title, or changing a log: when, what of a show, the rating, a
 * review and tags. A page of its own, so scrolling can never close it, with
 * Save always in reach above the keyboard.
 */
@Composable
fun LogPage(state: SabdekhoState, onAction: (SabdekhoAction) -> Unit) {
    val show = state.selectedShow ?: return
    val isMovie = show.type.equals("movie", ignoreCase = true)
    val editing = state.editingLog
    val close = { onAction(SabdekhoAction.CloseLogPage) }
    var tagDraft by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    val saveLabel = when {
        editing != null -> "Save"
        isMovie -> "Log"
        state.logSeasons.isEmpty() -> "Log whole show"
        else -> "Log ${logTargetLabel(state.logSeasons, state.selectedLogEpisodes)}"
    }

    FullScreenPage(onDismiss = close) {
        LogTopBar(
            name = show.name ?: "Untitled",
            posterPath = show.posterPath,
            isMovie = isMovie,
            isEditing = editing != null,
            onClose = close,
            onDelete = { confirmDelete = true }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            WhenSection(
                date = state.logDate,
                onDate = { onAction(SabdekhoAction.UpdateLogForm(date = it)) }
            )
            if (!isMovie) EpisodesSection(state = state, onAction = onAction)
            RatingSection(
                rating = state.logRating,
                liked = state.logLiked,
                rewatch = state.logRewatch,
                onRating = { onAction(SabdekhoAction.UpdateLogForm(rating = it)) },
                onLiked = { onAction(SabdekhoAction.UpdateLogForm(liked = it)) },
                onRewatch = { onAction(SabdekhoAction.UpdateLogForm(rewatch = it)) }
            )
            ReviewSection(
                review = state.logReview,
                onReview = { onAction(SabdekhoAction.UpdateLogForm(review = it)) }
            )
            TagsSection(
                tags = state.logPlatformTag,
                quickTags = state.quickTags,
                draft = tagDraft,
                onDraft = { tagDraft = it },
                onTags = { onAction(SabdekhoAction.UpdateLogForm(platformTag = it)) }
            )
        }
        LogSaveBar(
            label = saveLabel,
            isSaving = state.isSubmittingLog,
            error = state.logError,
            onSave = {
                // A tag still being typed goes in with the rest.
                if (tagDraft.isNotBlank()) {
                    onAction(SabdekhoAction.UpdateLogForm(platformTag = addTags(state.logPlatformTag, tagDraft)))
                    tagDraft = ""
                }
                onAction(SabdekhoAction.SubmitLog)
            }
        )
    }

    if (confirmDelete && editing != null) {
        val logCount = editing.allLogIds.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this log?") },
            text = if (logCount > 1) {
                { Text("All $logCount episodes in it go.") }
            } else null,
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onAction(SabdekhoAction.DeleteLog(editing.allLogIds, isMovie))
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LogTopBar(
    name: String,
    posterPath: String?,
    isMovie: Boolean,
    isEditing: Boolean,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
        TitlePoster(
            path = posterPath,
            isMovie = isMovie,
            modifier = Modifier.size(width = 30.dp, height = 45.dp),
            corner = 6.dp,
            width = "w92"
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isEditing) "Edit log" else "New log",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isEditing) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete log", tint = MaterialTheme.colorScheme.error)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// When
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WhenSection(date: String, onDate: (String) -> Unit) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val picked = remember(date) { runCatching { LocalDate.parse(date) }.getOrNull() } ?: today
    val isOther = picked != today && picked != yesterday
    var pickerOpen by remember { mutableStateOf(false) }

    Column {
        PageSectionTitle(title = "When")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = picked == today,
                onClick = { onDate(today.toString()) },
                label = { Text("Today") }
            )
            FilterChip(
                selected = picked == yesterday,
                onClick = { onDate(yesterday.toString()) },
                label = { Text("Yesterday") }
            )
            FilterChip(
                selected = isOther,
                onClick = { pickerOpen = true },
                label = { Text(if (isOther) picked.format(if (picked.year == today.year) ThisYearDay else OtherYearDay) else "Earlier") },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            )
        }
    }

    if (pickerOpen) {
        LogDatePicker(
            initial = picked,
            onPick = {
                onDate(it.toString())
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false }
        )
    }
}

/** The calendar, up to today. Its dates are UTC midnights, so they're read back in UTC. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogDatePicker(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val todayUtc = remember { LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = remember(todayUtc) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtc
            }
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis != null) onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) else onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = pickerState)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// What of a show
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The whole show, whole seasons, or one season's episodes. Episodes logged
 * before carry a green dot; picking one again makes the log a rewatch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodesSection(state: SabdekhoState, onAction: (SabdekhoAction) -> Unit) {
    // The log being edited doesn't count as logged before.
    val editingIds = remember(state.editingLog) { state.editingLog?.allLogIds.orEmpty().toSet() }
    val watched = remember(state.selectedShowLogs, editingIds) {
        WatchedEpisodes(state.selectedShowLogs.filterNot { it.id in editingIds })
    }
    val seasons = remember(state.detailsData, state.selectedShowLogs, state.logSeasons, state.selectedLogEpisodes) {
        seasonsOf(state.detailsData, state.selectedShowLogs, state.logSeasons, state.selectedLogEpisodes)
    }
    val single = state.logSeason

    Column {
        PageSectionTitle(
            title = "Watched",
            trailing = logTargetLabel(state.logSeasons, state.selectedLogEpisodes),
            trailingColor = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.logSeasons.isEmpty(),
                onClick = { onAction(SabdekhoAction.SelectLogSeason(null)) },
                label = { Text("Whole show") }
            )
            seasons.forEach { s ->
                val done = watched.isSeasonDone(s.number, s.episodeCount)
                FilterChip(
                    selected = s.number in state.logSeasons,
                    onClick = { onAction(SabdekhoAction.SelectLogSeason(s.number)) },
                    label = { Text("S${s.number}") },
                    trailingIcon = if (done) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Logged",
                                tint = WatchedGreen,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null
                )
            }
        }

        val count = single?.let { n -> seasons.firstOrNull { it.number == n }?.episodeCount } ?: 0
        if (single != null && count > 0) {
            val allPicked = (1..count).all { it in state.selectedLogEpisodes }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$count episodes",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onAction(SabdekhoAction.ToggleAllLogEpisodes) }) {
                    Text(if (allPicked) "None" else "All")
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (ep in 1..count) {
                    EpisodeTile(
                        number = ep,
                        selected = ep in state.selectedLogEpisodes,
                        watched = watched.isWatched(single, ep),
                        onToggle = { onAction(SabdekhoAction.ToggleLogEpisode(ep)) }
                    )
                }
            }
            if ((1..count).any { watched.isWatched(single, it) }) {
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(WatchedGreen)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Logged before",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeTile(number: Int, selected: Boolean, watched: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.surfaceContainerHigh,
        label = "episodeTile"
    )
    Box(
        modifier = Modifier
            .size(EpisodeTileSize)
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$number",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = when {
                selected -> colors.onPrimary
                watched -> colors.onSurfaceVariant
                else -> colors.onSurface
            }
        )
        if (watched && !selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(WatchedGreen)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rating, review, tags
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RatingSection(
    rating: Float,
    liked: Boolean,
    rewatch: Boolean,
    onRating: (Float) -> Unit,
    onLiked: (Boolean) -> Unit,
    onRewatch: (Boolean) -> Unit
) {
    Column {
        PageSectionTitle(
            title = "Rating",
            trailing = if (rating > 0f) "${formatStars(rating)} / 5" else null,
            trailingColor = GoldenStarColor
        )
        DragStarRating(rating = rating, onRatingChange = onRating)
        Row(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = liked,
                onClick = { onLiked(!liked) },
                label = { Text("Liked") },
                leadingIcon = {
                    Icon(
                        imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = if (liked) LikedPink else LocalContentColor.current,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            )
            FilterChip(
                selected = rewatch,
                onClick = { onRewatch(!rewatch) },
                label = { Text("Rewatch") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Autorenew,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            )
        }
    }
}

@Composable
private fun ReviewSection(review: String, onReview: (String) -> Unit) {
    Column {
        PageSectionTitle(title = "Review")
        OutlinedTextField(
            value = review,
            onValueChange = onReview,
            placeholder = { Text("What did you think?") },
            minLines = 3,
            maxLines = 8,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** The tags on this log and the ones used most, each a tap to add or remove; new ones typed below. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(
    tags: String,
    quickTags: List<String>,
    draft: String,
    onDraft: (String) -> Unit,
    onTags: (String) -> Unit
) {
    val current = remember(tags) { tags.split(',').map { it.trim() }.filter { it.isNotEmpty() } }
    val chips = remember(current, quickTags) { (current + quickTags).distinctBy { it.lowercase() } }
    val commit = {
        if (draft.isNotBlank()) onTags(addTags(tags, draft))
        onDraft("")
    }

    Column {
        PageSectionTitle(title = "Tags")
        if (chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { tag ->
                    val on = hasTag(tags, tag)
                    FilterChip(
                        selected = on,
                        onClick = { onTags(toggleTag(tags, tag)) },
                        label = { Text(tag) },
                        leadingIcon = if (on) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { typed ->
                // A comma finishes a tag.
                if (typed.endsWith(",")) {
                    onTags(addTags(tags, typed))
                    onDraft("")
                } else {
                    onDraft(typed)
                }
            },
            placeholder = { Text("Add a tag") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            trailingIcon = if (draft.isNotBlank()) {
                {
                    IconButton(onClick = commit) {
                        Icon(Icons.Default.Add, contentDescription = "Add tag")
                    }
                }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Save
// ─────────────────────────────────────────────────────────────────────────────

/** Save, above the gesture bar, and above the keyboard while it's open. */
@Composable
private fun LogSaveBar(label: String, isSaving: Boolean, error: String?, onSave: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Button(
                onClick = onSave,
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                } else {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
