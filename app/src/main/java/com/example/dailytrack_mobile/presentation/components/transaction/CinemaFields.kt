package com.example.dailytrack_mobile.presentation.components.transaction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.dailytrack_mobile.data.remote.dto.MediaSearchResultDto

// ─────────────────────────────────────────────────────────────────────────────
// Cinema entries
//
// With the category on Cinema, typing the description searches films; picking
// one links it, and saving logs the visit in SabDekho (the server adds the
// theatre tags itself).
// ─────────────────────────────────────────────────────────────────────────────

private fun posterUrl(path: String?, size: String = "w92") =
    path?.let { "https://image.tmdb.org/t/p/$size$it" }

fun MediaSearchResultDto.toLinkedMovie() = LinkedMovie(
    tmdbId = id,
    title = displayTitle,
    posterPath = posterPath,
    year = year
)

@Composable
private fun Poster(path: String?, width: Int, height: Int) {
    Box(
        modifier = Modifier
            .size(width.dp, height.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size((width / 2).dp)
        )
        posterUrl(path)?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

/** The linked film, and the tags its diary entry gets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CinemaMovieCard(
    movie: LinkedMovie,
    tags: List<String>,
    knownTags: List<String>,
    onUnlink: () -> Unit,
    onTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var adding by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(adding) { if (adding) runCatching { focus.requestFocus() } }

    fun commit() {
        val tag = newTag.trim()
        if (tag.isNotEmpty() && tags.none { it.equals(tag, ignoreCase = true) }) onTagsChange(tags + tag)
        newTag = ""
        adding = false
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = entryCardColor()),
        border = entryCardBorder(),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Poster(movie.posterPath, 36, 54)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOf(movie.year, "SabDekho").filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onUnlink) {
                    Icon(Icons.Default.Close, contentDescription = "Unlink", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                (tags + knownTags.filter { k -> tags.none { it.equals(k, ignoreCase = true) } }).take(12).forEach { tag ->
                    val on = tag in tags
                    FilterChip(
                        selected = on,
                        onClick = { onTagsChange(if (on) tags - tag else tags + tag) },
                        label = { Text(tag, style = MaterialTheme.typography.labelMedium) }
                    )
                }
                if (adding) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                            BasicTextField(
                                value = newTag,
                                onValueChange = { newTag = it.replace(",", "") },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { commit() }),
                                modifier = Modifier.widthIn(min = 60.dp, max = 160.dp).focusRequester(focus)
                            )
                        }
                    }
                } else {
                    AssistChip(
                        onClick = { adding = true },
                        label = { Text("Tag", style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }
    }
}

/** Docked above the keyboard like the description suggestions, listing films instead. */
@Composable
fun MovieSuggestionBar(
    results: List<MediaSearchResultDto>,
    onPick: (MediaSearchResultDto) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎬 FILMS",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onDone,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Done", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                results.forEach { result ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        onClick = { onPick(result) }
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Poster(result.posterPath, 24, 36)
                            Column {
                                Text(
                                    text = result.displayTitle,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 180.dp)
                                )
                                if (result.year.isNotBlank()) {
                                    Text(
                                        text = result.year,
                                        style = MaterialTheme.typography.labelSmall,
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
}
