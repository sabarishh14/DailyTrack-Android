package com.example.dailytrack_mobile.presentation.screens.money.components

import com.example.dailytrack_mobile.presentation.components.ConnectedToggleGroup
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.dailytrack_mobile.presentation.screens.money.CategoryEmojis
import com.example.dailytrack_mobile.presentation.screens.money.FilterMode
import com.example.dailytrack_mobile.presentation.screens.money.ItemFilterStatus
import com.example.dailytrack_mobile.presentation.util.Dimens

/**
 * Reusable filter section implementing the "Mode Toggle" pattern for Include vs. Exclude logic,
 * with streamlined top chips and a dedicated search & filter modal for fast Include/Exclude actions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedFilterSection(
    title: String,
    items: List<String>,
    selectedStatusMap: Map<String, ItemFilterStatus>,
    onItemStatusChange: (item: String, newStatus: ItemFilterStatus) -> Unit,
    modifier: Modifier = Modifier,
    initialMode: FilterMode = FilterMode.INCLUDE,
    maxVisibleChips: Int = 8
) {
    var currentMode by remember { mutableStateOf(initialMode) }
    var showSearchDialog by remember { mutableStateOf(false) }
    val dims = Dimens.current

    val includedCount = selectedStatusMap.count { it.value == ItemFilterStatus.INCLUDED }
    val excludedCount = selectedStatusMap.count { it.value == ItemFilterStatus.EXCLUDED }

    val activeItems = remember(items, selectedStatusMap) {
        items.filter { (selectedStatusMap[it] ?: ItemFilterStatus.NEUTRAL) != ItemFilterStatus.NEUTRAL }
    }
    val inactiveItems = remember(items, selectedStatusMap) {
        items.filter { (selectedStatusMap[it] ?: ItemFilterStatus.NEUTRAL) == ItemFilterStatus.NEUTRAL }
    }
    val visibleItems = remember(items, activeItems, inactiveItems, maxVisibleChips) {
        if (items.size <= maxVisibleChips) {
            items
        } else {
            val slotsLeft = (maxVisibleChips - activeItems.size).coerceAtLeast(3)
            (activeItems + inactiveItems.take(slotsLeft)).distinct()
        }
    }
    val hasMore = items.size > visibleItems.size

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
    ) {
        // Section Header with Title & Active Count Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Status Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (includedCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text(
                            text = "+$includedCount included",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (excludedCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text(
                            text = "-$excludedCount excluded",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Mode Toggle: include / exclude
        ConnectedToggleGroup(
            options = listOf(FilterMode.INCLUDE, FilterMode.EXCLUDE),
            selected = currentMode,
            onSelect = { currentMode = it },
            label = { if (it == FilterMode.INCLUDE) "Include Mode" else "Exclude Mode" },
            icon = { if (it == FilterMode.INCLUDE) Icons.Default.Check else Icons.Default.Close },
            colors = { mode ->
                ToggleButtonDefaults.toggleButtonColors(
                    checkedContainerColor = if (mode == FilterMode.INCLUDE) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                    checkedContentColor = if (mode == FilterMode.INCLUDE) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        // FlowRow of FilterChips (Top Visible Chips + Selected Items + More Action)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            visibleItems.forEach { item ->
                val status = selectedStatusMap[item] ?: ItemFilterStatus.NEUTRAL
                val isSelected = status != ItemFilterStatus.NEUTRAL

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val nextStatus = when (status) {
                            ItemFilterStatus.INCLUDED,
                            ItemFilterStatus.EXCLUDED -> ItemFilterStatus.NEUTRAL
                            ItemFilterStatus.NEUTRAL -> {
                                if (currentMode == FilterMode.INCLUDE) ItemFilterStatus.INCLUDED
                                else ItemFilterStatus.EXCLUDED
                            }
                        }
                        onItemStatusChange(item, nextStatus)
                    },
                    label = {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        )
                    },
                    leadingIcon = when (status) {
                        ItemFilterStatus.INCLUDED -> {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Included",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        ItemFilterStatus.EXCLUDED -> {
                            {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Excluded",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        ItemFilterStatus.NEUTRAL -> null
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = if (status == ItemFilterStatus.INCLUDED) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        selectedLabelColor = if (status == ItemFilterStatus.INCLUDED) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        selectedLeadingIconColor = if (status == ItemFilterStatus.INCLUDED) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        selectedBorderColor = if (status == ItemFilterStatus.INCLUDED) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        }
                    ),
                    shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
                )
            }

            // "+ Search & More" action chip when there are more items
            if (hasMore) {
                FilterChip(
                    selected = false,
                    onClick = { showSearchDialog = true },
                    label = {
                        Text(
                            text = "+ Search & More (${items.size})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        labelColor = MaterialTheme.colorScheme.primary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
                )
            }
        }
    }

    // Category / Account Search & Dual Include/Exclude Dialog
    if (showSearchDialog) {
        ItemFilterSearchDialog(
            title = title,
            items = items,
            selectedStatusMap = selectedStatusMap,
            onItemStatusChange = onItemStatusChange,
            onDismiss = { showSearchDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Item Filter Search Dialog with Dual Include/Exclude Actions
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemFilterSearchDialog(
    title: String,
    items: List<String>,
    selectedStatusMap: Map<String, ItemFilterStatus>,
    onItemStatusChange: (item: String, newStatus: ItemFilterStatus) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val isCategory = title.contains("Category", ignoreCase = true)

    val activeEntries = items.mapNotNull { item ->
        val status = selectedStatusMap[item] ?: ItemFilterStatus.NEUTRAL
        if (status != ItemFilterStatus.NEUTRAL) item to status else null
    }

    val filteredItems = remember(searchQuery, items) {
        val q = searchQuery.trim()
        if (q.isBlank()) items else items.filter { it.contains(q, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .heightIn(max = 560.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter $title",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search $title...", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Active Filters Row (if any)
                if (activeEntries.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        activeEntries.forEach { (item, status) ->
                            val isInc = status == ItemFilterStatus.INCLUDED
                            val bg = if (isInc) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                            val fg = if (isInc) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = bg,
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = (if (isInc) "+ " else "- ") + item,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = fg
                                    )
                                    IconButton(
                                        onClick = { onItemStatusChange(item, ItemFilterStatus.NEUTRAL) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", tint = fg, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Results List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (filteredItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No matching $title found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        filteredItems.forEach { item ->
                            val status = selectedStatusMap[item] ?: ItemFilterStatus.NEUTRAL
                            val isIncluded = status == ItemFilterStatus.INCLUDED
                            val isExcluded = status == ItemFilterStatus.EXCLUDED

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    isIncluded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    isExcluded -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isIncluded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        isExcluded -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Name + Emoji
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isCategory) {
                                            Text(
                                                text = CategoryEmojis.forCategory(item),
                                                fontSize = 18.sp
                                            )
                                        }
                                        Text(
                                            text = item,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (status != ItemFilterStatus.NEUTRAL) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // Direct Action Buttons: [+ Include] and [- Exclude]
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Include button
                                        FilledTonalButton(
                                            onClick = {
                                                val next = if (isIncluded) ItemFilterStatus.NEUTRAL else ItemFilterStatus.INCLUDED
                                                onItemStatusChange(item, next)
                                            },
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = if (isIncluded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                contentColor = if (isIncluded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isIncluded) Icons.Default.Check else Icons.Default.Add,
                                                contentDescription = "Include",
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isIncluded) "Included" else "Include",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                        }

                                        // Exclude button
                                        FilledTonalButton(
                                            onClick = {
                                                val next = if (isExcluded) ItemFilterStatus.NEUTRAL else ItemFilterStatus.EXCLUDED
                                                onItemStatusChange(item, next)
                                            },
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = if (isExcluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                contentColor = if (isExcluded) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isExcluded) Icons.Default.Close else Icons.Default.Remove,
                                                contentDescription = "Exclude",
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isExcluded) "Excluded" else "Exclude",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss
            ) {
                Text("Done")
            }
        }
    )
}
