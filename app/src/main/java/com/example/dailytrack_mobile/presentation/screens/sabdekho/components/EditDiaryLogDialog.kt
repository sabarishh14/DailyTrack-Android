package com.example.dailytrack_mobile.presentation.screens.sabdekho.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.data.remote.dto.MediaDiaryLogDto
import com.example.dailytrack_mobile.presentation.util.Dimens

@Composable
fun EditDiaryLogDialog(
    log: MediaDiaryLogDto,
    onDismiss: () -> Unit,
    onSave: (rating: Float?, review: String?, liked: Boolean?, rewatch: Boolean?, tag: String?) -> Unit
) {
    val dims = Dimens.current
    var rating by remember { mutableFloatStateOf(log.rating ?: 0f) }
    var review by remember { mutableStateOf(log.review ?: "") }
    var liked by remember { mutableStateOf(log.liked) }
    var rewatch by remember { mutableStateOf(log.rewatch) }
    var tags by remember { mutableStateOf(log.tags ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(dims.cardCornerRadius),
        title = {
            Column {
                Text(
                    text = "Edit Watch Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = log.showName ?: "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Star Rating Bar
                Column {
                    Text(
                        text = "RATING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    StarRatingBar(
                        rating = rating,
                        onRatingChanged = { rating = it },
                        starSize = 28.dp
                    )
                }

                // Toggles Row: Liked & Rewatch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Like Toggle
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
                        shape = RoundedCornerShape(dims.buttonCornerRadius)
                    )

                    // Rewatch Toggle
                    FilterChip(
                        selected = rewatch,
                        onClick = { rewatch = !rewatch },
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

                // Platform / Theatre Tags
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (e.g. IMAX, Netflix, Theatre)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                // Review TextField
                OutlinedTextField(
                    value = review,
                    onValueChange = { review = it },
                    label = { Text("Review / Thoughts") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        rating.takeIf { it > 0f },
                        review.takeIf { it.isNotBlank() },
                        liked,
                        rewatch,
                        tags.takeIf { it.isNotBlank() }
                    )
                }
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}
