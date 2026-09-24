package com.example.dailytrack_mobile.presentation.screens.money.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.data.remote.dto.BudgetSuggestionDto
import com.example.dailytrack_mobile.presentation.screens.money.CategoryEmojis
import com.example.dailytrack_mobile.presentation.util.Dimens
import kotlin.math.roundToLong
import com.example.dailytrack_mobile.presentation.components.rememberSheetHeight

// ─────────────────────────────────────────────────────────────────────────────
// Budget editor
//
// Edits every category in one draft and saves them in a single call, so the
// sheet never leaves the server half-updated. Categories that already have a
// limit float to the top; the rest stay searchable below.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetManagerSheet(
    categories: List<String>,
    currentBudgets: Map<String, Double>,
    suggestions: Map<String, BudgetSuggestionDto>,
    isSaving: Boolean,
    onSave: (Map<String, Double>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dims = Dimens.current

    // Held as text so a half-typed value doesn't round-trip through Double.
    val draft = remember(currentBudgets) {
        mutableStateMapOf<String, String>().apply {
            currentBudgets.forEach { (category, limit) -> put(category, formatLimitForInput(limit)) }
        }
    }
    var query by remember { mutableStateOf("") }

    // Every category the user could budget: whatever the app knows about, plus
    // anything that already has a budget but has since dropped out of the list.
    val allCategories = remember(categories, currentBudgets) {
        (categories + currentBudgets.keys).distinct()
    }

    val orderedCategories = remember(allCategories, currentBudgets, query) {
        val q = query.trim().lowercase()
        allCategories
            .filter { q.isBlank() || it.lowercase().contains(q) }
            .sortedWith(
                compareByDescending<String> { currentBudgets.containsKey(it) }
                    .thenBy { it.lowercase() }
            )
    }

    val draftCount = draft.count { parseLimit(it.value) > 0.0 }
    val draftTotal = draft.values.sumOf { parseLimit(it) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        contentWindowInsets = com.example.dailytrack_mobile.presentation.components.SheetContentInsets,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(rememberSheetHeight(0.85f))
        ) {
            // ── Header ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dims.screenHorizontalPadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Monthly budgets",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (draftCount == 0) "No limits set yet"
                               else "$draftCount categories · ${formatExactCurrency(draftTotal)} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Search ────────────────────────────────────────────────────
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search categories") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(19.dp))
                },
                trailingIcon = {
                    AnimatedVisibility(visible = query.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(17.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dims.screenHorizontalPadding)
            )

            Spacer(Modifier.height(12.dp))

            // ── Rows ──────────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = dims.screenHorizontalPadding,
                    end = dims.screenHorizontalPadding,
                    bottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (orderedCategories.isEmpty()) {
                    item {
                        Text(
                            text = "No categories match \"$query\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp)
                        )
                    }
                }

                items(orderedCategories, key = { it }) { category ->
                    BudgetEditRow(
                        category = category,
                        value = draft[category] ?: "",
                        suggestion = suggestions[category],
                        onValueChange = { newValue ->
                            if (newValue.isBlank()) draft.remove(category) else draft[category] = newValue
                        }
                    )
                }
            }

            // ── Save ──────────────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        // Categories the user cleared are sent as 0 so the server deletes them.
                        val cleared = currentBudgets.keys.associateWith { 0.0 }
                        val edited = draft.mapValues { parseLimit(it.value) }.filterValues { it > 0.0 }
                        onSave(cleared + edited)
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = dims.screenHorizontalPadding,
                            end = dims.screenHorizontalPadding,
                            top = 12.dp,
                            bottom = 20.dp
                        )
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(19.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Saving...", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Save budgets", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetEditRow(
    category: String,
    value: String,
    suggestion: BudgetSuggestionDto?,
    onValueChange: (String) -> Unit
) {
    val hasBudget = parseLimit(value) > 0.0
    // Only worth offering when it would actually change the number.
    val suggestedText = suggestion
        ?.takeIf { it.suggested > 0.0 && formatLimitForInput(roundToNearest50(it.suggested)) != value }
        ?.let { formatLimitForInput(roundToNearest50(it.suggested)) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (hasBudget) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = CategoryEmojis.forCategory(category), fontSize = 18.sp)
            Spacer(Modifier.width(12.dp))

            // Name and its suggestion stack tightly on the left; the amount field
            // sits beside them, centred, so the row is only as tall as it needs.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedVisibility(
                    visible = suggestedText != null,
                    enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
                ) {
                    SuggestionChip(
                        amountText = suggestedText.orEmpty(),
                        monthsOfHistory = suggestion?.monthsOfHistory ?: 0,
                        onApply = { suggestedText?.let(onValueChange) },
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))
            CompactAmountField(value = value, onValueChange = onValueChange)
        }
    }
}

/**
 * A slim amount input. The stock OutlinedTextField enforces a 56dp minimum
 * height, which made every row tall and pushed the suggestion far from its label.
 */
@Composable
private fun CompactAmountField(
    value: String,
    onValueChange: (String) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        isFocused -> MaterialTheme.colorScheme.primary
        value.isNotBlank() -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }

    BasicTextField(
        value = value,
        onValueChange = { input ->
            if (input.isEmpty() || input.all { it.isDigit() } && input.length <= 9) onValueChange(input)
        },
        singleLine = true,
        interactionSource = interactionSource,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next
        ),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.width(118.dp),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f))
                    .border(
                        width = if (isFocused) 1.5.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(11.dp)
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "₹",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) {
                        Text(
                            text = "No limit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}

@Composable
private fun SuggestionChip(
    amountText: String,
    monthsOfHistory: Int,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onApply
            )
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Suggest ₹$amountText",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "· ${monthsOfHistory}-month avg",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun parseLimit(text: String): Double = text.trim().toDoubleOrNull() ?: 0.0

private fun formatLimitForInput(limit: Double): String =
    if (limit <= 0.0) "" else limit.roundToLong().toString()

/** Suggestions land on odd numbers; rounding keeps the editor's values tidy. */
private fun roundToNearest50(amount: Double): Double = (amount / 50.0).roundToLong() * 50.0
