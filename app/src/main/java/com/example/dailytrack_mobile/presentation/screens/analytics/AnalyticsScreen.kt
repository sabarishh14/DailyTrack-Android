package com.example.dailytrack_mobile.presentation.screens.analytics

import com.example.dailytrack_mobile.presentation.components.topBarIconButtonColors
import com.example.dailytrack_mobile.presentation.components.LocalFloatingBarClearance
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.presentation.screens.money.MoneyVM
import com.example.dailytrack_mobile.presentation.screens.money.TransactionType
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBudgets: () -> Unit = {},
    viewModel: MoneyVM = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val dims = Dimens.current

    // Calendar Dropdown State
    var showCalendarDropdown by remember { mutableStateOf(false) }
    var selectedCalendarOption by remember { mutableStateOf("Month") }
    
    // Calculate start and end for selected calendar option
    val calendar = Calendar.getInstance()
    val now = calendar.timeInMillis
    var startMillis = 0L
    var endMillis = Long.MAX_VALUE
    var timeLeftText = ""
    var periodLabel = ""
    var projectedLabel = ""

    when (selectedCalendarOption) {
        "Day" -> {
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            startMillis = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            calendar.add(Calendar.MILLISECOND, -1)
            endMillis = calendar.timeInMillis
            
            val diff = endMillis - now
            val hours = diff / (1000 * 60 * 60)
            timeLeftText = "$hours hours left"
            periodLabel = "Spent today"
            projectedLabel = "Projected today"
        }
        "Week" -> {
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            startMillis = calendar.timeInMillis
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
            calendar.add(Calendar.MILLISECOND, -1)
            endMillis = calendar.timeInMillis
            
            val diff = endMillis - now
            val days = diff / (1000 * 60 * 60 * 24)
            timeLeftText = "$days days left"
            periodLabel = "Spent this week"
            projectedLabel = "Projected week"
        }
        "Month" -> {
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            startMillis = calendar.timeInMillis
            calendar.add(Calendar.MONTH, 1)
            calendar.add(Calendar.MILLISECOND, -1)
            endMillis = calendar.timeInMillis
            
            val diff = endMillis - now
            val days = diff / (1000 * 60 * 60 * 24)
            timeLeftText = "$days days left"
            periodLabel = "Spent this month"
            projectedLabel = "Projected month"
        }
    }

    // Filter transactions for the selected period
    val periodTransactions = state.transactions.filter {
        it.timestampMillis in startMillis..endMillis && !it.isExcluded
    }
    
    val periodExpenses = periodTransactions.filter { it.type == TransactionType.DEBIT }
    val spentThisPeriod = periodExpenses.sumOf { it.amount }

    // Find most expensive category
    val categoryTotals = periodExpenses.groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
    
    val mostExpensiveCategory = categoryTotals.maxByOrNull { it.value }
    val shoppingInfoText = if (mostExpensiveCategory != null && spentThisPeriod > 0) {
        val percentage = ((mostExpensiveCategory.value / spentThisPeriod) * 100).toInt()
        "${mostExpensiveCategory.key} is driving $percentage% of spending this ${selectedCalendarOption.lowercase()}."
    } else {
        "No spending this ${selectedCalendarOption.lowercase()}."
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    FilledIconButton(colors = topBarIconButtonColors(), onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                title = {
                    Text(
                        text = "Analytics",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        // The period's stats (including time left) live in the card
                        // below; the app bar only needs to offer picking a period.
                        Box {
                            FilledIconButton(colors = topBarIconButtonColors(), onClick = { showCalendarDropdown = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = "Calendar",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = showCalendarDropdown,
                                onDismissRequest = { showCalendarDropdown = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Day") },
                                    onClick = { 
                                        selectedCalendarOption = "Day"
                                        showCalendarDropdown = false 
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                                    trailingIcon = if (selectedCalendarOption == "Day") {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                                DropdownMenuItem(
                                    text = { Text("Week") },
                                    onClick = { 
                                        selectedCalendarOption = "Week"
                                        showCalendarDropdown = false 
                                    },
                                    leadingIcon = { Icon(Icons.Default.ViewWeek, contentDescription = null) },
                                    trailingIcon = if (selectedCalendarOption == "Week") {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                                DropdownMenuItem(
                                    text = { Text("Month") },
                                    onClick = { 
                                        selectedCalendarOption = "Month"
                                        showCalendarDropdown = false 
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                                    trailingIcon = if (selectedCalendarOption == "Month") {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dims.screenHorizontalPadding)
        ) {
            Spacer(modifier = Modifier.height(dims.itemSpacingMedium))

            // 1. Spent this month Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(dims.cardCornerRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(dims.cardInnerPadding)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = periodLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = formatCurrency(spentThisPeriod),
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Projected month
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = projectedLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatCurrency(spentThisPeriod * 1.5), // Dummy projection logic
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Time left
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Time left",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = timeLeftText,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Shopping info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = shoppingInfoText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            BudgetsEntryCard(
                hasBudgets = state.hasBudgets,
                isLoading = state.isLoading && state.budgets.isEmpty(),
                spent = state.totalBudgetSpent,
                limit = state.totalBudgetLimit,
                overCount = state.budgetsOverLimitCount,
                fraction = state.totalBudgetFraction,
                onClick = onNavigateToBudgets
            )

            Spacer(modifier = Modifier.height(32.dp + LocalFloatingBarClearance.current))
        }
    }
}

/**
 * The one way into Budgets from Analytics: a full-width card that reads as a
 * destination (icon, title, chevron) and carries this month's standing, so it
 * earns its space rather than being a bare button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetsEntryCard(
    hasBudgets: Boolean,
    isLoading: Boolean,
    spent: Double,
    limit: Double,
    overCount: Int,
    fraction: Float,
    onClick: () -> Unit
) {
    val dims = Dimens.current
    val accent = when {
        overCount > 0 -> MaterialTheme.colorScheme.error
        fraction >= 0.8f -> Color(0xFFF5A623)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dims.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(dims.cardInnerPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasBudgets || isLoading) "Manage your budgets" else "Set up budgets",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            isLoading -> "Loading this month…"
                            !hasBudgets -> "Give each category a monthly limit"
                            overCount > 0 -> "$overCount ${if (overCount == 1) "category is" else "categories are"} over limit"
                            else -> "All categories within limit this month"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overCount > 0 && !isLoading) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (hasBudgets && !isLoading) {
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${formatCurrency(spent)} spent",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "of ${formatCurrency(limit)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build())
    format.maximumFractionDigits = 0
    format.minimumFractionDigits = 0
    return format.format(amount)
}
