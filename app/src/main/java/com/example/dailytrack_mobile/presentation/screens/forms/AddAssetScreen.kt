package com.example.dailytrack_mobile.presentation.screens.forms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────────────────────────────────────
// Asset Category Definition (Matches Web Application Invest Manual Assets)
// ─────────────────────────────────────────────────────────────────────────────

enum class AssetCategory(
    val id: String,
    val label: String,
    val emoji: String,
    val placeholder: String,
    val isMath: Boolean // true for FD (auto-compounding by interest rate), false for ledger/market assets
) {
    FD("FD", "Fixed Deposit", "🏦", "e.g. HDFC Fixed Deposit", isMath = true),
    EPF("EPF", "EPF", "🛡️", "e.g. EPFO Account", isMath = false),
    PPF("PPF", "PPF", "🔒", "e.g. SBI PPF Account", isMath = false),
    NPS("NPS", "NPS", "🏛️", "e.g. Tier-1 NPS Account", isMath = false),
    SGB("SGB", "SGB / Gold", "🥇", "e.g. Sovereign Gold Bond", isMath = false),
    RSU("RSU", "RSU / Stocks", "💼", "e.g. Company RSU Grant", isMath = false),
    REAL_ESTATE("RealEstate", "Real Estate", "🏠", "e.g. 2BHK Apartment", isMath = false),
    CASH("Cash", "Cash / Bank", "💵", "e.g. Emergency Liquid Fund", isMath = false);

    companion object {
        fun fromId(id: String): AssetCategory = entries.find { it.id == id } ?: FD
    }
}

private enum class ActiveDatePicker {
    NONE,
    START_DATE,
    MATURITY_DATE,
    NEXT_RUN_DATE
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddAssetScreen(
    formsVM: FormsVM = hiltViewModel(),
    onDirtyStateChanged: (Boolean) -> Unit = {},
    onSaveSuccess: () -> Unit = {}
) {
    val formState by formsVM.addAssetState.collectAsState()
    val dims = Dimens.current

    var selectedCategory by remember { mutableStateOf(AssetCategory.FD) }
    var assetName by remember { mutableStateOf("") }
    var investedAmount by remember { mutableStateOf("") }
    var currentValue by remember { mutableStateOf("") }
    var interestRate by remember { mutableStateOf("") }

    var startDate by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var maturityDate by remember { mutableStateOf<LocalDate?>(null) }

    // Recurring automation state
    var isRecurring by remember { mutableStateOf(false) }
    var amountToAdd by remember { mutableStateOf("") }
    var intervalValue by remember { mutableStateOf("1") }
    var intervalUnit by remember { mutableStateOf("months") }
    var nextRunDate by remember { mutableStateOf(LocalDate.now()) }

    var activeDatePicker by remember { mutableStateOf(ActiveDatePicker.NONE) }

    val isMath = selectedCategory.isMath
    val isLedgerOrMarket = !selectedCategory.isMath
    val supportsAutomateAdditions = selectedCategory != AssetCategory.REAL_ESTATE && 
            selectedCategory != AssetCategory.CASH && 
            !selectedCategory.isMath

    // Clean up incompatible fields when switching between categories
    LaunchedEffect(selectedCategory) {
        if (isLedgerOrMarket) {
            interestRate = ""
        }
        if (!supportsAutomateAdditions) {
            isRecurring = false
            amountToAdd = ""
        }
        if (selectedCategory != AssetCategory.FD) {
            maturityDate = null
        }
    }

    val isDirty = remember(
        selectedCategory,
        assetName,
        investedAmount,
        currentValue,
        interestRate,
        startDate,
        maturityDate,
        isRecurring,
        amountToAdd
    ) {
        assetName.isNotBlank() ||
                investedAmount.isNotBlank() ||
                currentValue.isNotBlank() ||
                interestRate.isNotBlank() ||
                startDate != null ||
                maturityDate != null ||
                isRecurring ||
                amountToAdd.isNotBlank()
    }

    LaunchedEffect(isDirty) {
        onDirtyStateChanged(isDirty)
    }

    val displayDateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }
    val apiDateFormatter = remember { DateTimeFormatter.ISO_LOCAL_DATE }

    val cardBg = MaterialTheme.colorScheme.surfaceContainer
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

    // ── Date Picker Dialog ───────────────────────────────────────────
    if (activeDatePicker != ActiveDatePicker.NONE) {
        val initialMillis = when (activeDatePicker) {
            ActiveDatePicker.START_DATE -> (startDate ?: LocalDate.now())
            ActiveDatePicker.MATURITY_DATE -> (maturityDate ?: LocalDate.now().plusYears(1))
            ActiveDatePicker.NEXT_RUN_DATE -> nextRunDate
            ActiveDatePicker.NONE -> LocalDate.now()
        }.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { activeDatePicker = ActiveDatePicker.NONE },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val chosenDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            when (activeDatePicker) {
                                ActiveDatePicker.START_DATE -> startDate = chosenDate
                                ActiveDatePicker.MATURITY_DATE -> maturityDate = chosenDate
                                ActiveDatePicker.NEXT_RUN_DATE -> nextRunDate = chosenDate
                                ActiveDatePicker.NONE -> Unit
                            }
                        }
                        activeDatePicker = ActiveDatePicker.NONE
                    }
                ) {
                    Text("Select", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeDatePicker = ActiveDatePicker.NONE }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingLarge),
        verticalArrangement = Arrangement.spacedBy(dims.sectionSpacing)
    ) {

        // ── Error Banner if any ──────────────────────────────────────
        formState.errorMessage?.let { errorMsg ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(dims.buttonCornerRadius),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(dims.cardInnerPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingSmall)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(dims.iconSizeSmall)
                    )
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── Category Selector (2x2 Swipable Pager) ───────────────────
        val categoryPages = remember {
            AssetCategory.entries.chunked(4) // 2 pages of 4 items each
        }
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { categoryPages.size })

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dims.itemSpacingSmall)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("Asset Category")
                Text(
                    text = "Swipe for more (Page ${pagerState.currentPage + 1}/${categoryPages.size})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { pageIndex ->
                val itemsForPage = categoryPages[pageIndex]
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(dims.itemSpacingSmall)
                ) {
                    for (rowIndex in 0 until 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingSmall)
                        ) {
                            for (colIndex in 0 until 2) {
                                val itemIndex = rowIndex * 2 + colIndex
                                if (itemIndex < itemsForPage.size) {
                                    val cat = itemsForPage[itemIndex]
                                    val isSelected = selectedCategory == cat
                                    Surface(
                                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainer
                                        },
                                        border = if (isSelected) {
                                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                        } else {
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(dims.buttonCornerRadius))
                                            .clickable { selectedCategory = cat }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = dims.itemSpacingMedium, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingSmall + 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(
                                                        color = if (isSelected) {
                                                            MaterialTheme.colorScheme.primaryContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                                        },
                                                        shape = RoundedCornerShape(8.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = cat.emoji,
                                                    fontSize = 18.sp
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cat.label,
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                                    ),
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = if (cat.isMath) "Auto-compound" else "Ledger asset",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Pager Dots Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(categoryPages.size) { index ->
                    val isActive = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(if (isActive) 20.dp else 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                    )
                }
            }
        }

        // ── Asset Name ───────────────────────────────────────────────
        SectionLabel("Asset Name")
        OutlinedTextField(
            value = assetName,
            onValueChange = { assetName = it },
            placeholder = {
                Text(
                    selectedCategory.placeholder,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(dims.buttonCornerRadius),
            modifier = Modifier.fillMaxWidth()
        )

        // ── Invested Amount & Current Value ──────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
        ) {
            // Invested Amount
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel("Invested (₹)")
                Spacer(modifier = Modifier.height(dims.itemSpacingSmall))
                OutlinedTextField(
                    value = investedAmount,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() || it == '.' }) investedAmount = newValue
                    },
                    placeholder = { Text("0.00", style = MaterialTheme.typography.bodyMedium) },
                    prefix = { Text("₹ ", style = MaterialTheme.typography.bodyMedium) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Current Value
            val hasAutoInterest = isMath && interestRate.isNotBlank()
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SectionLabel("Current Value (₹)")
                    if (hasAutoInterest) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Auto",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(dims.itemSpacingSmall))
                OutlinedTextField(
                    value = if (hasAutoInterest && currentValue.isBlank()) investedAmount else currentValue,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() || it == '.' }) currentValue = newValue
                    },
                    placeholder = { Text("0.00", style = MaterialTheme.typography.bodyMedium) },
                    prefix = { Text("₹ ", style = MaterialTheme.typography.bodyMedium) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !hasAutoInterest,
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── Interest Rate Row (compounding for math assets) ───────────
        if (isMath) {
            Column(verticalArrangement = Arrangement.spacedBy(dims.itemSpacingSmall)) {
                SectionLabel("Interest Rate %")
                OutlinedTextField(
                    value = interestRate,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() || it == '.' }) interestRate = newValue
                    },
                    placeholder = { Text("e.g. 7.1", style = MaterialTheme.typography.bodyMedium) },
                    suffix = { Text("% p.a.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Math assets auto-compound daily using the interest rate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Dates Section (Start Date & Maturity Date for FD, Investment Date for others) ──
        if (selectedCategory == AssetCategory.FD) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
            ) {
                // Start Date
                Column(modifier = Modifier.weight(1f)) {
                    SectionLabel("Start Date")
                    Spacer(modifier = Modifier.height(dims.itemSpacingSmall))
                    Surface(
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        color = cardBg,
                        border = cardBorder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(dims.buttonCornerRadius))
                            .clickable { activeDatePicker = ActiveDatePicker.START_DATE }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dims.itemSpacingMedium, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = startDate?.format(displayDateFormatter) ?: "Select date",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (startDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(dims.iconSizeSmall)
                            )
                        }
                    }
                }

                // Maturity Date
                Column(modifier = Modifier.weight(1f)) {
                    SectionLabel("Maturity Date")
                    Spacer(modifier = Modifier.height(dims.itemSpacingSmall))
                    Surface(
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        color = cardBg,
                        border = cardBorder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(dims.buttonCornerRadius))
                            .clickable { activeDatePicker = ActiveDatePicker.MATURITY_DATE }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dims.itemSpacingMedium, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = maturityDate?.format(displayDateFormatter) ?: "Optional",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (maturityDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(dims.iconSizeSmall)
                            )
                        }
                    }
                }
            }
        } else {
            // Single Investment Date field
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionLabel("Investment Date")
                Spacer(modifier = Modifier.height(dims.itemSpacingSmall))
                Surface(
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    color = cardBg,
                    border = cardBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(dims.buttonCornerRadius))
                        .clickable { activeDatePicker = ActiveDatePicker.START_DATE }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dims.itemSpacingMedium, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = startDate?.format(displayDateFormatter) ?: LocalDate.now().format(displayDateFormatter),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(dims.iconSizeSmall)
                        )
                    }
                }
            }
        }

        // ── Automate Additions Toggle Card (For applicable Ledger/Market assets) ──
        if (supportsAutomateAdditions) {
            Surface(
                shape = RoundedCornerShape(dims.cardCornerRadius),
                color = cardBg,
                border = cardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(dims.cardInnerPadding)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = null,
                                tint = if (isRecurring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(dims.iconSizeMedium)
                            )
                            Column {
                                Text(
                                    text = "Automate Additions",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Add amount automatically on schedule",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isRecurring,
                            onCheckedChange = { isRecurring = it }
                        )
                    }

                    // Expanded Recurring Settings
                    AnimatedVisibility(
                        visible = isRecurring,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = dims.itemSpacingMedium),
                            verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // Recurring Amount
                            Column {
                                SectionLabel("Amount to Add (₹)")
                                Spacer(modifier = Modifier.height(dims.itemSpacingSmall))
                                OutlinedTextField(
                                    value = amountToAdd,
                                    onValueChange = { newValue ->
                                        if (newValue.all { it.isDigit() || it == '.' }) amountToAdd = newValue
                                    },
                                    placeholder = { Text("0.00", style = MaterialTheme.typography.bodyMedium) },
                                    prefix = { Text("₹ ", style = MaterialTheme.typography.bodyMedium) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Frequency & Unit
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
                            ) {
                                Column(modifier = Modifier.width(100.dp)) {
                                    SectionLabel("Every")
                                    Spacer(modifier = Modifier.height(dims.itemSpacingSmall))
                                    OutlinedTextField(
                                        value = intervalValue,
                                        onValueChange = { newValue ->
                                            if (newValue.all { it.isDigit() }) intervalValue = newValue
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    SectionLabel("Interval")
                                    Spacer(modifier = Modifier.height(dims.itemSpacingSmall))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingSmall)
                                    ) {
                                        listOf("days" to "Days", "months" to "Months", "years" to "Years").forEach { (unitVal, unitLabel) ->
                                            FilterChip(
                                                selected = intervalUnit == unitVal,
                                                onClick = { intervalUnit = unitVal },
                                                label = { Text(unitLabel, style = MaterialTheme.typography.bodySmall) },
                                                shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Next Trigger Date
                            Column {
                                SectionLabel("Next Trigger Date")
                                Spacer(modifier = Modifier.height(dims.itemSpacingSmall))
                                Surface(
                                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                                    color = cardBg,
                                    border = cardBorder,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(dims.buttonCornerRadius))
                                        .clickable { activeDatePicker = ActiveDatePicker.NEXT_RUN_DATE }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = dims.itemSpacingMedium, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = nextRunDate.format(displayDateFormatter),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(dims.iconSizeSmall)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Save Button ──────────────────────────────────────────────
        val isFormValid = assetName.isNotBlank() &&
                (investedAmount.isNotBlank() || currentValue.isNotBlank()) &&
                (!isRecurring || amountToAdd.isNotBlank())

        Button(
            onClick = {
                formsVM.clearAddAssetError()
                val invVal = investedAmount.toDoubleOrNull() ?: 0.0
                val currVal = if (isMath && interestRate.isNotBlank()) {
                    invVal
                } else {
                    currentValue.toDoubleOrNull() ?: invVal
                }
                val rate = interestRate.toDoubleOrNull()
                val sDate = startDate?.format(apiDateFormatter)
                val mDate = maturityDate?.format(apiDateFormatter)
                val recAmt = amountToAdd.toDoubleOrNull()
                val intVal = intervalValue.toIntOrNull() ?: 1
                val nRunDate = nextRunDate.format(apiDateFormatter)

                formsVM.saveManualAsset(
                    category = selectedCategory.id,
                    name = assetName.trim(),
                    investedValue = invVal,
                    currentValue = currVal,
                    interestRate = rate,
                    startDate = sDate,
                    maturityDate = mDate,
                    isRecurring = isRecurring,
                    amountToAdd = recAmt,
                    intervalValue = intVal,
                    intervalUnit = intervalUnit,
                    nextRunDate = if (isRecurring) nRunDate else null,
                    onSuccess = onSaveSuccess
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(dims.searchBarHeight),
                enabled = isFormValid && !formState.isSaving
        ) {
            if (formState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(dims.iconSizeMedium),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(
                    "Save Asset",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.height(dims.itemSpacingMedium))
    }
}
