package com.example.dailytrack_mobile.presentation.screens.sabdekho.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.presentation.components.rememberSheetHeight
import com.example.dailytrack_mobile.presentation.screens.sabdekho.SabdekhoState
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Year / Month / Week / Language filters for the Library, as a bottom sheet —
 * matches the pattern the Money tab's filter sheet already uses instead of
 * cramming four dropdowns into the page.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryFilterBottomSheet(
    state: SabdekhoState,
    onApply: (year: String, month: String, week: String, language: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dims = Dimens.current

    var draftYear by remember { mutableStateOf(state.yearFilter) }
    var draftMonth by remember { mutableStateOf(state.monthFilter) }
    var draftWeek by remember { mutableStateOf(state.weekFilter) }
    var draftLanguage by remember { mutableStateOf(state.languageFilter) }

    val activeCount = listOf(draftYear, draftMonth, draftWeek, draftLanguage).count { it != "all" }

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
                .height(rememberSheetHeight(0.82f))
        ) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dims.screenHorizontalPadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Filters",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    if (activeCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text("$activeCount", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { draftYear = "all"; draftMonth = "all"; draftWeek = "all"; draftLanguage = "all" },
                        enabled = activeCount > 0
                    ) {
                        Text(
                            text = "Reset All",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // ── Scrollable filter sections ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.sectionSpacing),
                verticalArrangement = Arrangement.spacedBy(dims.sectionSpacing)
            ) {
                FilterChipSection(
                    title = "Year",
                    options = listOf("all" to "All Years") + state.filterYears.map { it.toString() to it.toString() },
                    selected = draftYear,
                    onSelect = { draftYear = it }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                FilterChipSection(
                    title = "Month",
                    options = listOf("all" to "All Months") + (1..12).map { it.toString() to monthShortName(it) },
                    selected = draftMonth,
                    onSelect = { draftMonth = it }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                FilterChipSection(
                    title = "Week",
                    options = listOf("all" to "All Weeks") + (1..52).map { it.toString() to "W$it" },
                    selected = draftWeek,
                    onSelect = { draftWeek = it }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                FilterChipSection(
                    title = "Language",
                    options = listOf("all" to "All Languages") +
                        state.filterLanguages.map { (it.code ?: "unknown") to (it.label ?: it.code.orEmpty()) },
                    selected = draftLanguage,
                    onSelect = { draftLanguage = it }
                )

                Spacer(modifier = Modifier.height(dims.sectionSpacing))
            }

            // ── Sticky footer ──
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingLarge),
                    horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(dims.buttonHeight),
                        shape = RoundedCornerShape(dims.buttonCornerRadius)
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Button(
                        onClick = { onApply(draftYear, draftMonth, draftWeek, draftLanguage) },
                        modifier = Modifier.weight(1.5f).height(dims.buttonHeight),
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = if (activeCount > 0) "Apply Filters ($activeCount)" else "Apply Filters",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

private fun monthShortName(month: Int): String =
    Month.of(month).getDisplayName(TextStyle.SHORT, Locale.getDefault())

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipSection(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val dims = Dimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(value) },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
                )
            }
        }
    }
}
