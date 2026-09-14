package com.example.dailytrack_mobile.presentation.screens.money.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.presentation.screens.money.CategoryEmojis
import com.example.dailytrack_mobile.presentation.screens.money.DEFAULT_CANONICAL_ACCOUNTS
import com.example.dailytrack_mobile.presentation.screens.money.Transaction
import com.example.dailytrack_mobile.presentation.screens.money.TransactionType
import com.example.dailytrack_mobile.presentation.screens.money.sortAccountsCanonical
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.text.SimpleDateFormat
import java.util.*

private enum class EditTxType(val label: String, val dbValue: String, val color: Color) {
    EXPENSE("Expense", "Debit", Color(0xFFFF5252)),
    INCOME("Income", "Credit", Color(0xFF2ECC71)),
    SAVINGS("Savings", "Savings", Color(0xFF29B6F6)),
    INVESTMENT("Invest", "Investment", Color(0xFFAB47BC))
}

private val defaultCategories = listOf(
    "Food", "Transport", "Shopping", "Entertainment", "Bills",
    "Health", "Education", "Cinema", "Daily Need", "Salary",
    "Freelance", "Investment", "Gift", "Other"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    availableAccounts: List<String>,
    availableCategories: List<String>,
    mostUsedCategories: List<String> = emptyList(),
    recentDescriptions: List<String> = emptyList(),
    descriptionsByCategory: Map<String, List<String>> = emptyMap(),
    isUpdating: Boolean,
    onSave: (
        id: Long,
        type: String,
        category: String,
        amount: Double,
        note: String?,
        accountName: String,
        date: String,
        excludeAnalytics: Boolean
    ) -> Unit,
    onDelete: (Transaction) -> Unit,
    onDismiss: () -> Unit
) {
    val dims = Dimens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    var isNoteFocused by remember { mutableStateOf(false) }
    val apiDateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val displayDateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

    // Initialize state from transaction
    val initialType = remember(transaction) {
        when {
            transaction.type == TransactionType.CREDIT || transaction.rawType.equals("Credit", ignoreCase = true) -> EditTxType.INCOME
            transaction.type == TransactionType.SAVINGS || transaction.isSavings -> EditTxType.SAVINGS
            transaction.type == TransactionType.INVESTMENT || transaction.isInvestment -> EditTxType.INVESTMENT
            else -> EditTxType.EXPENSE
        }
    }

    val initialDateMillis = remember(transaction) {
        if (transaction.rawDate.isNotBlank()) {
            try {
                apiDateFormat.parse(transaction.rawDate)?.time ?: transaction.timestampMillis
            } catch (_: Exception) {
                transaction.timestampMillis
            }
        } else {
            transaction.timestampMillis
        }
    }

    var selectedType by remember { mutableStateOf(initialType) }
    var selectedDate by remember { mutableStateOf(initialDateMillis) }
    var showDatePicker by remember { mutableStateOf(false) }

    val formattedInitialAmount = remember(transaction.amount) {
        if (transaction.amount % 1.0 == 0.0) "%.0f".format(transaction.amount)
        else "%.2f".format(transaction.amount)
    }
    var amount by remember { mutableStateOf(formattedInitialAmount) }
    var selectedAccount by remember { mutableStateOf(transaction.bank) }
    var showAccountPicker by remember { mutableStateOf(false) }

    var categoryInput by remember { mutableStateOf(transaction.category) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf(transaction.note ?: "") }
    var excludeAnalytics by remember { mutableStateOf(transaction.isExcluded) }

    val accountsList = remember(availableAccounts) {
        val list = if (availableAccounts.isNotEmpty()) availableAccounts else defaultAccounts
        sortAccountsCanonical(list)
    }

    val allCategories = remember(availableCategories) {
        if (availableCategories.isNotEmpty()) availableCategories else defaultCategories
    }

    // Recent categories prioritizing user's actual most used categories
    val recentCategories = remember(mostUsedCategories, allCategories, selectedType) {
        val source = if (mostUsedCategories.isNotEmpty()) mostUsedCategories else allCategories
        val filtered = if (selectedType == EditTxType.EXPENSE) {
            source.filter { !it.equals("Salary", ignoreCase = true) && !it.equals("Freelance", ignoreCase = true) }
        } else {
            source.filter {
                it.equals("Salary", ignoreCase = true) || it.equals("Freelance", ignoreCase = true) ||
                it.equals("Investment", ignoreCase = true) || it.equals("Gift", ignoreCase = true) ||
                it.equals("Other", ignoreCase = true)
            }.ifEmpty { listOf("Salary", "Freelance", "Investment", "Gift", "Other") }
        }
        (listOf(categoryInput) + filtered).distinct().take(8)
    }

    val noteSuggestions = remember(categoryInput, note, recentDescriptions, descriptionsByCategory) {
        val categoryList = if (categoryInput.isNotBlank()) {
            descriptionsByCategory[categoryInput.trim()].orEmpty()
        } else {
            emptyList()
        }
        val fallback = listOf("Lunch", "Dinner", "Snacks", "Coffee", "Groceries", "Uber", "Fuel", "Shopping", "Subscription", "Bill")
        val combined = (categoryList + recentDescriptions + fallback).filter { it.isNotBlank() }.distinct()
        val query = note.trim()
        if (query.isBlank()) {
            combined.take(50)
        } else {
            val (startsWith, contains) = combined
                .filter { !it.equals(query, ignoreCase = true) }
                .partition { it.startsWith(query, ignoreCase = true) }
            (startsWith + contains.filter { it.contains(query, ignoreCase = true) }).take(50)
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDate = it }
                    showDatePicker = false
                }) {
                    Text("OK")
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

    // Category Picker Dialog
    if (showCategoryPicker) {
        CategoryPickerDialog(
            allCategories = allCategories,
            recentCategories = recentCategories,
            currentCategory = categoryInput,
            onCategorySelected = {
                categoryInput = it
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }

    // Account Picker Dialog
    if (showAccountPicker) {
        AccountPickerDialog(
            accountsList = accountsList,
            currentAccount = selectedAccount,
            onAccountSelected = {
                selectedAccount = it
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isUpdating) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.79f)
                .imePadding()
        ) {
            // ── Header ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingMedium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = selectedType.color.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = CategoryEmojis.forCategory(categoryInput),
                                fontSize = 20.sp
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Edit Transaction",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "ID: #${transaction.id} • ${transaction.bank}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    enabled = !isUpdating
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // ── Scrollable Form Body ─────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dims.screenHorizontalPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Hero Amount Card (Centered without weird left-box bias)
                val amountFocusRequester = remember { FocusRequester() }
                Card(
                    shape = RoundedCornerShape(dims.cardCornerRadius),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            amountFocusRequester.requestFocus()
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "AMOUNT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )

                        // Truly centered currency row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "₹",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = selectedType.color
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            BasicTextField(
                                value = amount,
                                onValueChange = { newValue ->
                                    if (newValue.all { it.isDigit() || it == '.' }) amount = newValue
                                },
                                modifier = Modifier
                                    .width(IntrinsicSize.Min)
                                    .defaultMinSize(minWidth = if (amount.isEmpty()) 64.dp else 16.dp)
                                    .focusRequester(amountFocusRequester),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                cursorBrush = SolidColor(selectedType.color),
                                textStyle = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Start
                                ),
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (amount.isEmpty()) {
                                            Text(
                                                text = "0.00",
                                                style = MaterialTheme.typography.headlineLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                                )
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }
                }

                // 2. Transaction Type Segmented Pills
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Transaction Type",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EditTxType.entries.forEach { type ->
                            val isSelected = selectedType == type
                            val bgColor by animateColorAsState(
                                targetValue = if (isSelected) type.color.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                label = "pill_bg"
                            )
                            val borderColor by animateColorAsState(
                                targetValue = if (isSelected) type.color
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                label = "pill_border"
                            )

                            Surface(
                                shape = RoundedCornerShape(dims.buttonCornerRadius),
                                color = bgColor,
                                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(dims.buttonCornerRadius))
                                    .clickable { selectedType = type }
                            ) {
                                Text(
                                    text = type.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) type.color else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Date Selector with Shortcuts
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Date",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = { selectedDate = System.currentTimeMillis() },
                            label = { Text("Today", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
                        )

                        FilterChip(
                            selected = false,
                            onClick = {
                                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                                selectedDate = cal.timeInMillis
                            },
                            label = { Text("Yesterday", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
                        )

                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(dims.iconSizeSmall)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = displayDateFormat.format(Date(selectedDate)),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        }
                    }
                }

                // 4. Account Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Account",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Quick select",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    // Horizontal quick-select chips for top accounts
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        accountsList.take(6).forEach { account ->
                            val isSelected = selectedAccount.trim().equals(account.trim(), ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedAccount = account
                                },
                                label = { Text(account, style = MaterialTheme.typography.bodyMedium) },
                                shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }

                        // "+ More" chip to open AccountPickerDialog
                        FilterChip(
                            selected = false,
                            onClick = { showAccountPicker = true },
                            label = { Text("+ More", style = MaterialTheme.typography.bodyMedium) },
                            shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                labelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    // Clickable Account Box
                    Surface(
                        onClick = { showAccountPicker = true },
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (selectedAccount.isNotBlank()) selectedAccount else "Select Account",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (selectedAccount.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                                        color = if (selectedAccount.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select Account",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 5. Category Selector with Recent Categories Chips
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Recent categories",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    // Horizontal scrolling row of Recent Categories
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentCategories.forEach { category ->
                            val isSelected = categoryInput.trim().equals(category.trim(), ignoreCase = true)
                            val emoji = CategoryEmojis.forCategory(category)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    categoryInput = if (isSelected) "" else category
                                },
                                label = { Text("$emoji $category", style = MaterialTheme.typography.bodyMedium) },
                                shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }

                        // "+ More" chip to open CategoryPickerDialog
                        FilterChip(
                            selected = false,
                            onClick = { showCategoryPicker = true },
                            label = { Text("+ More", style = MaterialTheme.typography.bodyMedium) },
                            shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                labelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    // Clickable Category Box
                    Surface(
                        onClick = { showCategoryPicker = true },
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                val emoji = if (categoryInput.isNotBlank()) CategoryEmojis.forCategory(categoryInput) else "📁"
                                Text(
                                    text = emoji,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = if (categoryInput.isNotBlank()) categoryInput else "Select or Search Category",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (categoryInput.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                                        color = if (categoryInput.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select Category",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 6. Note / Description
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Description (optional)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        noteSuggestions.forEach { suggestion ->
                            val isCategoryMatch = categoryInput.isNotBlank() &&
                                descriptionsByCategory[categoryInput.trim()]?.contains(suggestion) == true
                            SuggestionChip(
                                onClick = { note = suggestion },
                                label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) },
                                icon = if (isCategoryMatch) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                } else null,
                                shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isNoteFocused = it.isFocused },
                        placeholder = { Text("What was this for?", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Outlined.Notes,
                                contentDescription = null,
                                modifier = Modifier.size(dims.iconSizeMedium)
                            )
                        },
                        trailingIcon = {
                            if (note.isNotBlank()) {
                                IconButton(onClick = { note = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear Note",
                                        modifier = Modifier.size(dims.iconSizeSmall)
                                    )
                                }
                            }
                        },
                        minLines = 2,
                        maxLines = 3,
                        shape = RoundedCornerShape(dims.buttonCornerRadius)
                    )
                }

                // 7. Exclude from Spending Analyser
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(dims.cardCornerRadius),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Exclude from Spending Analyser",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Hide this transaction from the pie chart and stats",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = excludeAnalytics,
                            onCheckedChange = { excludeAnalytics = it }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // ── Sticky Footer: Docked Suggestion Accessory Bar or Action Buttons ──
            if (isNoteFocused) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (categoryInput.isNotBlank()) "SUGGESTIONS • ${categoryInput.uppercase()}" else "SUGGESTIONS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 1.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "• 1-tap to fill",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            TextButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    isNoteFocused = false
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "Done",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            noteSuggestions.forEach { suggestion ->
                                val isCategoryMatch = categoryInput.isNotBlank() &&
                                    descriptionsByCategory[categoryInput.trim()]?.contains(suggestion) == true
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isCategoryMatch) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isCategoryMatch) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        }
                                    ),
                                    modifier = Modifier.clickable {
                                        note = suggestion
                                        focusManager.clearFocus()
                                        isNoteFocused = false
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCategoryMatch) Icons.Default.Check else Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = suggestion,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 13.sp,
                                                fontWeight = if (isCategoryMatch) FontWeight.Bold else FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingLarge),
                    horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Delete Button
                    OutlinedButton(
                        onClick = { onDelete(transaction) },
                        enabled = !isUpdating,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            modifier = Modifier.size(dims.iconSizeSmall)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    // Save Changes Button
                    val isValidAmount = (amount.toDoubleOrNull() ?: 0.0) > 0
                    val isFormValid = isValidAmount && categoryInput.isNotBlank() && selectedAccount.isNotBlank()

                    Button(
                        onClick = {
                            val parsedAmt = amount.toDoubleOrNull() ?: 0.0
                            val dateStr = apiDateFormat.format(Date(selectedDate))
                            onSave(
                                transaction.id,
                                selectedType.dbValue,
                                categoryInput.trim().ifEmpty { "Other" },
                                parsedAmt,
                                note.takeIf { it.isNotBlank() },
                                selectedAccount,
                                dateStr,
                                excludeAnalytics
                            )
                        },
                        enabled = !isUpdating && isFormValid,
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        modifier = Modifier
                            .weight(1f)
                            .height(dims.searchBarHeight)
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(dims.iconSizeMedium)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Save Changes",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}
