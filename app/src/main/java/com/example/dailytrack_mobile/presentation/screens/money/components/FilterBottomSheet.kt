package com.example.dailytrack_mobile.presentation.screens.money.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.presentation.components.MonthYearPickerDialog
import com.example.dailytrack_mobile.presentation.screens.money.*
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import com.example.dailytrack_mobile.presentation.components.rememberSheetHeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    filterState: AnalysisFilterState,
    allCategories: List<String>,
    allAccounts: List<String>,
    onApply: (AnalysisFilterState) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dims = Dimens.current

    // Local draft state for filters
    var draftFilters by remember(filterState) { mutableStateOf(filterState) }
    var showMonthYearPicker by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    val financialYears = listOf("All Time", "FY 2025-26", "FY 2024-25", "FY 2023-24", "FY 2022-23")
    var fyDropdownExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(rememberSheetHeight(0.79f))
        ) {
            // ─────────────────────────────────────────────────────────────────
            // Header Bar
            // ─────────────────────────────────────────────────────────────────
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
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (draftFilters.hasActiveFilters) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = "${draftFilters.activeFilterCount}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = { draftFilters = AnalysisFilterState() },
                        enabled = draftFilters.hasActiveFilters
                    ) {
                        Text(
                            text = "Reset All",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (draftFilters.hasActiveFilters) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
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
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp
            )

            // ─────────────────────────────────────────────────────────────────
            // Scrollable Filter Sections
            // ─────────────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.sectionSpacing),
                verticalArrangement = Arrangement.spacedBy(dims.sectionSpacing)
            ) {
                // 1. Date & Time Section
                DateTimeFilterSection(
                    financialYears = financialYears,
                    selectedYear = draftFilters.financialYear ?: "All Time",
                    onYearSelected = {
                        draftFilters = draftFilters.copy(
                            financialYear = if (it == "All Time") null else it,
                            selectedMonth = null,
                            selectedYear = null,
                            customDateRange = null
                        )
                    },
                    selectedMonth = draftFilters.selectedMonth,
                    selectedDateYear = draftFilters.selectedYear,
                    formattedDateRange = draftFilters.formattedDateRange(),
                    onOpenMonthYearPicker = { showMonthYearPicker = true },
                    onClearMonthYear = {
                        draftFilters = draftFilters.copy(
                            selectedMonth = null,
                            selectedYear = null,
                            customDateRange = null
                        )
                    },
                    onOpenDateRangePicker = { showDateRangePicker = true },
                    onClearDateRange = {
                        draftFilters = draftFilters.copy(
                            customDateRange = null,
                            selectedMonth = null,
                            selectedYear = null
                        )
                    },
                    fyDropdownExpanded = fyDropdownExpanded,
                    onDropdownExpandedChange = { fyDropdownExpanded = it }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    thickness = 1.dp
                )

                // 2. Transaction Type Filter
                TransactionTypeSection(
                    selectedTypes = draftFilters.selectedTypes,
                    onTypeToggle = { type ->
                        val updated = draftFilters.selectedTypes.toMutableSet()
                        if (updated.contains(type)) updated.remove(type) else updated.add(type)
                        draftFilters = draftFilters.copy(selectedTypes = updated)
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    thickness = 1.dp
                )

                // 3. Complex Filters: Categories (Mode Toggle Pattern)
                AdvancedFilterSection(
                    title = "Categories",
                    items = allCategories,
                    selectedStatusMap = draftFilters.categoryFilters,
                    onItemStatusChange = { category, newStatus ->
                        val updated = draftFilters.categoryFilters.toMutableMap()
                        if (newStatus == ItemFilterStatus.NEUTRAL) {
                            updated.remove(category)
                        } else {
                            updated[category] = newStatus
                        }
                        draftFilters = draftFilters.copy(categoryFilters = updated)
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    thickness = 1.dp
                )

                // 4. Complex Filters: Accounts & Banks (Mode Toggle Pattern)
                val sortedAccounts = remember(allAccounts) {
                    sortAccountsCanonical(allAccounts)
                }
                AdvancedFilterSection(
                    title = "Accounts & Banks",
                    items = sortedAccounts,
                    selectedStatusMap = draftFilters.accountFilters,
                    onItemStatusChange = { account, newStatus ->
                        val updated = draftFilters.accountFilters.toMutableMap()
                        if (newStatus == ItemFilterStatus.NEUTRAL) {
                            updated.remove(account)
                        } else {
                            updated[account] = newStatus
                        }
                        draftFilters = draftFilters.copy(accountFilters = updated)
                    }
                )

                Spacer(modifier = Modifier.height(dims.sectionSpacing))
            }

            // ─────────────────────────────────────────────────────────────────
            // Sticky Bottom Action Bar
            // ─────────────────────────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingLarge),
                    horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(dims.buttonHeight),
                        shape = RoundedCornerShape(dims.buttonCornerRadius)
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    Button(
                        onClick = { onApply(draftFilters) },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(dims.buttonHeight),
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        val count = draftFilters.activeFilterCount
                        Text(
                            text = if (count > 0) "Apply Filters ($count)" else "Apply Filters",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Month Year Picker Dialog
    // ─────────────────────────────────────────────────────────────────────────
    if (showMonthYearPicker) {
        MonthYearPickerDialog(
            selectedMonth = draftFilters.selectedMonth,
            selectedYear = draftFilters.selectedYear ?: LocalDate.now().year,
            onSelected = { month, year ->
                val (start, end) = if (month != null) {
                    getMonthRangeMillis(month, year)
                } else {
                    getYearRangeMillis(year)
                }
                draftFilters = draftFilters.copy(
                    selectedMonth = month,
                    selectedYear = year,
                    customDateRange = Pair(start, end),
                    financialYear = null,
                    activeDatePreset = null
                )
                showMonthYearPicker = false
            },
            onDismiss = { showMonthYearPicker = false }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Date Range Picker Dialog
    // ─────────────────────────────────────────────────────────────────────────
    if (showDateRangePicker) {
        DateRangePickerDialog(
            initialStartMillis = draftFilters.customDateRange?.first,
            initialEndMillis = draftFilters.customDateRange?.second,
            onConfirm = { start, end ->
                draftFilters = draftFilters.copy(
                    customDateRange = Pair(start, end),
                    selectedMonth = null,
                    selectedYear = null,
                    financialYear = null,
                    activeDatePreset = null
                )
                showDateRangePicker = false
            },
            onDismiss = { showDateRangePicker = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date & Time Filter Section
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeFilterSection(
    financialYears: List<String>,
    selectedYear: String,
    onYearSelected: (String) -> Unit,
    selectedMonth: Month?,
    selectedDateYear: Int?,
    formattedDateRange: String?,
    onOpenMonthYearPicker: () -> Unit,
    onClearMonthYear: () -> Unit,
    onOpenDateRangePicker: () -> Unit,
    onClearDateRange: () -> Unit,
    fyDropdownExpanded: Boolean,
    onDropdownExpandedChange: (Boolean) -> Unit
) {
    val dims = Dimens.current
    val hasMonthYear = selectedDateYear != null
    val hasCustomRange = formattedDateRange != null && !hasMonthYear

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
    ) {
        Text(
            text = "Date & Period",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        // Option 1: Month & Year Picker Card (One-tap full month)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dims.cardCornerRadius - 6.dp))
                .clickable { onOpenMonthYearPicker() },
            color = if (hasMonthYear) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                width = if (hasMonthYear) 1.dp else 0.5.dp,
                color = if (hasMonthYear) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(dims.cardCornerRadius - 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dims.screenHorizontalPadding, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (hasMonthYear) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = "Month & Year",
                            tint = if (hasMonthYear) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Month & Year",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (hasMonthYear) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when {
                                selectedMonth != null && selectedDateYear != null ->
                                    "${selectedMonth.getDisplayName(TextStyle.FULL, Locale.getDefault())} $selectedDateYear"
                                selectedDateYear != null ->
                                    "Year $selectedDateYear (Full Year)"
                                else ->
                                    "Tap to select month or entire year"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (hasMonthYear) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (hasMonthYear) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                if (hasMonthYear) {
                    IconButton(
                        onClick = onClearMonthYear,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear month & year",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Select Month & Year",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Option 2: Custom Date Range Card Button (Custom start & end dates)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dims.cardCornerRadius - 6.dp))
                .clickable { onOpenDateRangePicker() },
            color = if (hasCustomRange) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                width = if (hasCustomRange) 1.dp else 0.5.dp,
                color = if (hasCustomRange) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(dims.cardCornerRadius - 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dims.screenHorizontalPadding, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (hasCustomRange) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DateRange,
                            contentDescription = "Custom Date Range",
                            tint = if (hasCustomRange) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Custom Date Range",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (hasCustomRange) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (hasCustomRange) (formattedDateRange ?: "")
                            else "Pick custom start & end dates",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (hasCustomRange) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (hasCustomRange) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                if (hasCustomRange) {
                    IconButton(
                        onClick = onClearDateRange,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear date range",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Pick Range",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Option 3: Financial Year Dropdown
        ExposedDropdownMenuBox(
            expanded = fyDropdownExpanded,
            onExpandedChange = onDropdownExpandedChange,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedYear,
                onValueChange = {},
                readOnly = true,
                label = { Text("Financial Year") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalance,
                        contentDescription = null,
                        tint = if (selectedYear != "All Time") MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = fyDropdownExpanded)
                },
                shape = RoundedCornerShape(dims.cardCornerRadius - 6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )

            ExposedDropdownMenu(
                expanded = fyDropdownExpanded,
                onDismissRequest = { onDropdownExpandedChange(false) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                financialYears.forEach { year ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = year,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (year == selectedYear) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (year == selectedYear) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onYearSelected(year)
                            onDropdownExpandedChange(false)
                        },
                        trailingIcon = if (year == selectedYear) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Transaction Type Filter
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TransactionTypeSection(
    selectedTypes: Set<TransactionType>,
    onTypeToggle: (TransactionType) -> Unit
) {
    val dims = Dimens.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
    ) {
        Text(
            text = "Transaction Type",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        // Type Filter Chips FlowRow (Debit, Credit, Savings, Investment)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransactionType.entries.forEach { type ->
                val isSelected = selectedTypes.contains(type)
                FilterChip(
                    selected = isSelected,
                    onClick = { onTypeToggle(type) },
                    label = { Text(type.displayName) },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date Range Picker Dialog
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerDialog(
    initialStartMillis: Long?,
    initialEndMillis: Long?,
    onConfirm: (Long?, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartMillis,
        initialSelectedEndDateMillis = initialEndMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        dateRangePickerState.selectedStartDateMillis,
                        dateRangePickerState.selectedEndDateMillis
                    )
                },
                enabled = dateRangePickerState.selectedStartDateMillis != null
            ) {
                Text("Apply Range")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            title = {
                Text(
                    text = "Select Date Range",
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            headline = {
                DateRangePickerDefaults.DateRangePickerHeadline(
                    selectedStartDateMillis = dateRangePickerState.selectedStartDateMillis,
                    selectedEndDateMillis = dateRangePickerState.selectedEndDateMillis,
                    displayMode = dateRangePickerState.displayMode,
                    dateFormatter = remember { DatePickerDefaults.dateFormatter() },
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
