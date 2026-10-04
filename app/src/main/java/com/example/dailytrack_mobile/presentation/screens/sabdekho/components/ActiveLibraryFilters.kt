package com.example.dailytrack_mobile.presentation.screens.sabdekho.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dailytrack_mobile.presentation.screens.sabdekho.LibraryFacet
import com.example.dailytrack_mobile.presentation.screens.sabdekho.MonthNames
import com.example.dailytrack_mobile.presentation.screens.sabdekho.SabdekhoState
import com.example.dailytrack_mobile.presentation.screens.sabdekho.WeekdayNames
import com.example.dailytrack_mobile.presentation.screens.sabdekho.starsLabel
import com.example.dailytrack_mobile.presentation.screens.sabdekho.weekLabel

/**
 * The Library's active filters as removable chips — how a jump from a Stats
 * bar shows what it filtered to, without opening the filter sheet.
 */
@Composable
fun ActiveLibraryFilters(
    state: SabdekhoState,
    onRemove: (LibraryFacet) -> Unit,
    onClearAll: () -> Unit
) {
    val chips = buildList {
        if (state.yearFilter != "all") add(LibraryFacet.YEAR to state.yearFilter)
        state.monthFilter.toIntOrNull()?.let { add(LibraryFacet.MONTH to (MonthNames.getOrNull(it - 1) ?: state.monthFilter)) }
        state.weekFilter.toIntOrNull()?.let { add(LibraryFacet.WEEK to weekLabel(state.yearFilter, it)) }
        state.weekdayFilter.toIntOrNull()?.let { add(LibraryFacet.WEEKDAY to "${WeekdayNames.getOrNull(it) ?: state.weekdayFilter}s") }
        state.ratingFilter.toDoubleOrNull()?.let { add(LibraryFacet.RATING to "Rated ${starsLabel(it)}") }
        if (state.languageFilter != "all") {
            val name = state.filterLanguages.firstOrNull { it.code == state.languageFilter }?.label
            add(LibraryFacet.LANGUAGE to (name ?: state.languageFilter.uppercase()))
        }
    }
    if (chips.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        chips.forEach { (facet, label) -> FilterPill(label) { onRemove(facet) } }
        Text(
            text = "Clear all",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onClearAll)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun FilterPill(label: String, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onRemove, shape = CircleShape, color = colors.primaryContainer) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimaryContainer,
                maxLines = 1
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove $label filter",
                tint = colors.onPrimaryContainer.copy(alpha = 0.75f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
