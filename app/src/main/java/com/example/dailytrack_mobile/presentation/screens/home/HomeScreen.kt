package com.example.dailytrack_mobile.presentation.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.dailytrack_mobile.presentation.screens.money.AccountInfo
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.time.Month
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.presentation.components.DailyTrackPullToRefreshBox
import com.example.dailytrack_mobile.presentation.components.MonthYearPickerDialog

// ─────────────────────────────────────────────────────────────────────────────
// Shared accent colours (theme-agnostic, same pattern as ActivitiesScreen)
// ─────────────────────────────────────────────────────────────────────────────
private val GainGreen   = Color(0xFF2ECC71)
private val LossRed     = Color(0xFFE74C3C)

// Removed mock investments data

private data class FlowBreakdown(
    val label: String,
    val icon: ImageVector,
    val amount: Double
)

private fun getIconForAccount(accountName: String): ImageVector {
    return when {
        accountName.contains("Cash", ignoreCase = true) -> Icons.Default.Money
        accountName.contains("CC", ignoreCase = true) || accountName.contains("Credit", ignoreCase = true) -> Icons.Default.CreditCard
        else -> Icons.Default.AccountBalance
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────
private fun formatCurrency(amount: Double): String {
    val abs = Math.abs(amount)
    return when {
        abs >= 1_00_000 -> "₹%.2fL".format(abs / 1_00_000)
        abs >= 1_000    -> "₹%.2fK".format(abs / 1_000)
        else            -> "₹%.0f".format(abs)
    }
}

private fun formatCurrencyFull(amount: Double): String {
    val prefix = if (amount < 0) "-" else ""
    return "$prefix₹%,.2f".format(Math.abs(amount))
}

// ─────────────────────────────────────────────────────────────────────────────
// Main composable
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HomeScreen(
    viewModel: HomeVM = hiltViewModel()
) {
    val homeState by viewModel.state.collectAsState()
    val dims = Dimens.current
    val selectedMonth = homeState.selectedMonth
    val selectedYear = homeState.selectedYear

    val incomeFlows = remember(homeState.incomeByCategory) {
        homeState.incomeByCategory.map { (category, amount) ->
            FlowBreakdown(
                label = category,
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                amount = amount
            )
        }.sortedByDescending { it.amount }
    }
    val expenseFlows = remember(homeState.expenseByCategory) {
        homeState.expenseByCategory.map { (category, amount) ->
            FlowBreakdown(
                label = category,
                icon = Icons.AutoMirrored.Filled.TrendingDown,
                amount = amount
            )
        }.sortedByDescending { it.amount }
    }

    // Compute bank balance from API accounts (only balance_tracked accounts on main page)
    val apiBankBalance = homeState.totalBankBalance
    val apiAccounts = remember(homeState.accounts) {
        homeState.accounts.filter { it.balanceTracked }
    }
    val totalNetWorth = apiBankBalance + homeState.investmentTotalCurrent

    DailyTrackPullToRefreshBox(
        isRefreshing = homeState.isRefreshing,
        onRefresh = { viewModel.onAction(HomeAction.Refresh(forceRefresh = true)) },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start  = dims.screenHorizontalPadding,
                end    = dims.screenHorizontalPadding,
                top    = dims.screenTopPadding,
                bottom = dims.screenBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(dims.sectionSpacing)
        ) {
            item {
                NetWorthSection(
                    totalBankBalance = apiBankBalance,
                    totalNetWorth = totalNetWorth,
                    accountCount = apiAccounts.size,
                    isLoading = homeState.isLoading,
                    isInvestFiltered = homeState.isInvestFiltered,
                    visibleInvestCount = homeState.visibleInvestCategoriesCount,
                    totalInvestCount = homeState.totalInvestCategoriesCount
                )
            }
            item {
                BankAccountsSection(
                    accounts = apiAccounts,
                    totalBankBalance = apiBankBalance,
                    isLoading = homeState.isLoading
                )
            }
            item { 
                InvestmentPortfolioSection(
                    totalInvested = homeState.investmentTotalInvested,
                    totalCurrent  = homeState.investmentTotalCurrent,
                    isLoading     = homeState.isLoading,
                    isFiltered    = homeState.isInvestFiltered,
                    visibleCount  = homeState.visibleInvestCategoriesCount,
                    totalCount    = homeState.totalInvestCategoriesCount
                ) 
            }
            item {
                FlowSection(
                    title         = "INCOME BY ACCOUNT",
                    flows         = incomeFlows,
                    isIncome      = true,
                    selectedMonth = selectedMonth,
                    selectedYear  = selectedYear,
                    onDateChange  = { m, y ->
                        viewModel.onAction(HomeAction.DateSelected(m, y))
                    }
                )
            }
            item {
                FlowSection(
                    title         = "EXPENSES BY ACCOUNT",
                    flows         = expenseFlows,
                    isIncome      = false,
                    selectedMonth = selectedMonth,
                    selectedYear  = selectedYear,
                    onDateChange  = { m, y ->
                        viewModel.onAction(HomeAction.DateSelected(m, y))
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 1 – Swipeable Balance & Net Worth Header Card (No Pagination)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun NetWorthSection(
    totalBankBalance: Double,
    totalNetWorth: Double,
    accountCount: Int,
    isLoading: Boolean,
    isInvestFiltered: Boolean = false,
    visibleInvestCount: Int = 0,
    totalInvestCount: Int = 0
) {
    val dims = Dimens.current
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    var isBalanceVisible by rememberSaveable { mutableStateOf(false) }

    SectionCard(
        onClick = { isBalanceVisible = !isBalanceVisible }
    ) {
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dims.itemSpacingSmall),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (page == 0) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.size(20.dp))
                        SectionLabel(text = "TOTAL BANK BALANCE")
                        Icon(
                            imageVector        = if (isBalanceVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isBalanceVisible) "Hide balance" else "Show balance",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(dims.itemSpacingMedium))
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            strokeCap = StrokeCap.Round,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        AnimatedContent(
                            targetState = isBalanceVisible,
                            label       = "BankBalanceVisibility"
                        ) { visible ->
                            Text(
                                text  = if (visible) formatCurrencyFull(totalBankBalance) else "₹XXXX",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = dims.fontSizeDisplayLarge,
                                    letterSpacing = if (visible) 0.sp else 2.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text  = "$accountCount Linked Bank Accounts",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text  = if (isBalanceVisible) "· Tap to hide" else "· Tap to show",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
                    }
                } else {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.size(20.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SectionLabel(text = "NET WORTH")
                            if (isInvestFiltered) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = "$visibleInvestCount/$totalInvestCount",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Icon(
                            imageVector        = if (isBalanceVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isBalanceVisible) "Hide balance" else "Show balance",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(dims.itemSpacingMedium))
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            strokeCap = StrokeCap.Round,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        AnimatedContent(
                            targetState = isBalanceVisible,
                            label       = "NetWorthVisibility"
                        ) { visible ->
                            Text(
                                text  = if (visible) formatCurrencyFull(totalNetWorth) else "₹XXXX",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = dims.fontSizeDisplayLarge,
                                    letterSpacing = if (visible) 0.sp else 2.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text  = if (isInvestFiltered) "Banks · Cash · Investments ($visibleInvestCount/$totalInvestCount)" else "Banks · Cash · Investments",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text  = if (isBalanceVisible) "· Tap to hide" else "· Tap to show",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 2 – Bank Accounts (Collapsible, List vs Cards Toggle)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BankAccountsSection(
    accounts: List<AccountInfo>,
    totalBankBalance: Double,
    isLoading: Boolean
) {
    val dims = Dimens.current
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var isGridView by rememberSaveable { mutableStateOf(true) }

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge)) {
            // Header Row
            Row(
                modifier              = Modifier.fillMaxWidth().heightIn(min = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                SectionLabel(
                    text       = "BANK BALANCES",
                    isExpanded = isExpanded,
                    onClick    = { isExpanded = !isExpanded }
                )

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text  = "${accounts.size} Accounts",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Small toggle: Cards vs List
                    Surface(
                        shape  = RoundedCornerShape(8.dp),
                        color  = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier          = Modifier.padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Grid / Cards toggle
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isGridView) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication        = null
                                    ) { isGridView = true }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector        = Icons.Default.GridView,
                                    contentDescription = "Cards View",
                                    tint               = if (isGridView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier           = Modifier.size(13.dp)
                                )
                            }
                            // List toggle
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (!isGridView) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication        = null
                                    ) { isGridView = false }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector        = Icons.AutoMirrored.Filled.List,
                                    contentDescription = "List View",
                                    tint               = if (!isGridView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier           = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter   = fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                          expandVertically(
                              expandFrom = Alignment.Top,
                              animationSpec = tween(280, easing = FastOutSlowInEasing)
                          ),
                exit    = fadeOut(animationSpec = tween(150, easing = FastOutLinearInEasing)) +
                          shrinkVertically(
                              shrinkTowards = Alignment.Top,
                              animationSpec = tween(240, easing = FastOutSlowInEasing)
                          )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge)) {
                    if (isLoading && accounts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = dims.itemSpacingLarge),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                strokeCap = StrokeCap.Round,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        AnimatedContent(
                            targetState = isGridView,
                            label       = "BankAccountsViewToggle"
                        ) { targetIsGrid ->
                            if (targetIsGrid) {
                                // 2-column grid of bank cards
                                val rows = accounts.chunked(2)
                                Column(verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)) {
                                    rows.forEach { rowAccounts ->
                                        Row(
                                            modifier              = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
                                        ) {
                                            rowAccounts.forEach { account ->
                                                BankAccountCard(
                                                    account  = account,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            if (rowAccounts.size < 2) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Sleek list view of bank accounts
                                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                    accounts.forEachIndexed { idx, account ->
                                        BankAccountRow(account = account)
                                        if (idx < accounts.lastIndex) {
                                            HorizontalDivider(
                                                modifier  = Modifier.padding(vertical = dims.itemSpacingMedium),
                                                thickness = 0.5.dp,
                                                color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text  = "Total Balance",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text  = formatCurrencyFull(totalBankBalance),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BankAccountRow(account: AccountInfo) {
    val dims = Dimens.current
    val isCreditCard = account.account.startsWith("CC-", ignoreCase = true)
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge)
        ) {
            Box(
                modifier         = Modifier
                    .size(dims.avatarSizeMedium)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = if (isCreditCard) Icons.Default.CreditCard else Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier           = Modifier.size(dims.iconSizeSmall + 2.dp)
                )
            }
            Text(
                text  = account.account,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text  = formatCurrencyFull(account.balance),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BankAccountCard(
    account: AccountInfo,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current
    val isCreditCard = account.account.startsWith("CC-", ignoreCase = true)
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
        ),
        border    = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(dims.itemSpacingLarge),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier         = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = if (isCreditCard) Icons.Default.CreditCard else Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier           = Modifier.size(16.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text     = account.account,
                    style    = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text     = formatCurrencyFull(account.balance),
                    style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color    = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 3 – Investment Portfolio (Collapsible on tapping name)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun InvestmentPortfolioSection(
    totalInvested: Double,
    totalCurrent: Double,
    isLoading: Boolean = false,
    isFiltered: Boolean = false,
    visibleCount: Int = 0,
    totalCount: Int = 0
) {
    val dims = Dimens.current
    val totalReturns = totalCurrent - totalInvested
    val isOverallGain   = totalReturns >= 0
    val overallColor    = if (isOverallGain) GainGreen else LossRed
    val totalReturnsPct = if (totalInvested == 0.0) 0.0 else (totalReturns / totalInvested) * 100.0
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge)) {
            Row(
                modifier              = Modifier.fillMaxWidth().heightIn(min = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SectionLabel(
                        text       = "INVESTMENT PORTFOLIO",
                        isExpanded = isExpanded,
                        onClick    = { isExpanded = !isExpanded }
                    )
                    if (isFiltered) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "$visibleCount/$totalCount",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter   = fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                          expandVertically(
                              expandFrom = Alignment.Top,
                              animationSpec = tween(280, easing = FastOutSlowInEasing)
                          ),
                exit    = fadeOut(animationSpec = tween(150, easing = FastOutLinearInEasing)) +
                          shrinkVertically(
                              shrinkTowards = Alignment.Top,
                              animationSpec = tween(240, easing = FastOutSlowInEasing)
                          )
            ) {
                if (isLoading && totalInvested == 0.0 && totalCurrent == 0.0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dims.itemSpacingLarge),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            strokeCap = StrokeCap.Round,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    // 2x2 Grid of metric cards
                    Column(verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)) {
                    // Row 1: Invested & Current
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
                    ) {
                        PortfolioMetricCard(
                            label    = "Invested",
                            value    = formatCurrencyFull(totalInvested),
                            modifier = Modifier.weight(1f)
                        )
                        PortfolioMetricCard(
                            label    = "Current",
                            value    = formatCurrencyFull(totalCurrent),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 2: Returns & Total Return %
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
                    ) {
                        PortfolioMetricCard(
                            label      = "Returns",
                            value      = "${if (isOverallGain) "+" else ""}${formatCurrencyFull(totalReturns)}",
                            valueColor = overallColor,
                            modifier   = Modifier.weight(1f)
                        )
                        PortfolioMetricCard(
                            label      = "Total Return %",
                            value      = "%s%.2f%%".format(if (isOverallGain) "+" else "", totalReturnsPct),
                            valueColor = overallColor,
                            icon       = if (isOverallGain) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            modifier   = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun PortfolioMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector? = null
) {
    val dims = Dimens.current
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
        ),
        border    = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(dims.itemSpacingLarge),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector        = icon,
                        contentDescription = null,
                        tint               = valueColor,
                        modifier           = Modifier.size(16.dp)
                    )
                }
                Text(
                    text     = value,
                    style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color    = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sections 4 & 5 – Income / Expense breakdown (Collapsible, Month/Year Filter)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FlowSection(
    title: String,
    flows: List<FlowBreakdown>,
    isIncome: Boolean,
    selectedMonth: Month,
    selectedYear: Int,
    onDateChange: (Month, Int) -> Unit
) {
    val dims = Dimens.current
    val accentColor = if (isIncome) GainGreen else LossRed
    val total       = flows.sumOf { it.amount }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge)) {
            // Header row with Section Label and Month/Year filter pill
            Row(
                modifier              = Modifier.fillMaxWidth().heightIn(min = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                SectionLabel(
                    text       = title,
                    isExpanded = isExpanded,
                    onClick    = { isExpanded = !isExpanded }
                )

                Surface(
                    onClick = { showDatePicker = true },
                    shape   = RoundedCornerShape(8.dp),
                    color   = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                    border  = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier              = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.CalendarMonth,
                            contentDescription = "Select Month & Year",
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(13.dp)
                        )
                        Text(
                            text  = "${selectedMonth.getDisplayName(TextStyle.SHORT, Locale.getDefault())} $selectedYear",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector        = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier           = Modifier.size(14.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter   = fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                          expandVertically(
                              expandFrom = Alignment.Top,
                              animationSpec = tween(280, easing = FastOutSlowInEasing)
                          ),
                exit    = fadeOut(animationSpec = tween(150, easing = FastOutLinearInEasing)) +
                          shrinkVertically(
                              shrinkTowards = Alignment.Top,
                              animationSpec = tween(240, easing = FastOutSlowInEasing)
                          )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge)) {
                    // Total row
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text  = "Total ${if (isIncome) "Inflow" else "Outflow"}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text  = formatCurrencyFull(total),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // Flow breakdown items
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        flows.forEachIndexed { idx, flow ->
                            FlowRow(flow = flow, accentColor = accentColor)
                            if (idx < flows.lastIndex) {
                                HorizontalDivider(
                                    modifier  = Modifier.padding(vertical = dims.itemSpacingMedium),
                                    thickness = 0.5.dp,
                                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        MonthYearPickerDialog(
            selectedMonth = selectedMonth,
            selectedYear  = selectedYear,
            onDismiss     = { showDatePicker = false },
            onSelected    = { m, y ->
                showDatePicker = false
                onDateChange(m ?: selectedMonth, y)
            }
        )
    }
}

@Composable
private fun FlowRow(flow: FlowBreakdown, accentColor: Color) {
    val dims = Dimens.current
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge)
        ) {
            Box(
                modifier         = Modifier
                    .size(dims.avatarSizeSmall)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = flow.icon,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(dims.iconSizeSmall)
                )
            }
            Text(
                text  = flow.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text  = if (flow.amount == 0.0) "—" else formatCurrencyFull(flow.amount),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (flow.amount == 0.0) MaterialTheme.colorScheme.onSurfaceVariant
                    else accentColor
        )
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Reusable building blocks
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val dims = Dimens.current
    if (onClick != null) {
        Card(
            onClick   = onClick,
            modifier  = Modifier.fillMaxWidth().then(modifier),
            shape     = RoundedCornerShape(dims.cardCornerRadius),
            colors    = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dims.cardInnerPadding),
                content  = content
            )
        }
    } else {
        Card(
            modifier  = Modifier.fillMaxWidth().then(modifier),
            shape     = RoundedCornerShape(dims.cardCornerRadius),
            colors    = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dims.cardInnerPadding),
                content  = content
            )
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    isExpanded: Boolean? = null,
    onClick: (() -> Unit)? = null
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded == true) 0f else -90f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "sectionChevronRotation"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = if (onClick != null) {
            modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null
                ) { onClick() }
        } else modifier
    ) {
        Text(
            text     = text,
            style    = MaterialTheme.typography.labelLarge.copy(
                fontWeight    = FontWeight.Bold,
                letterSpacing = 1.5.sp
            ),
            color    = MaterialTheme.colorScheme.primary
        )
        if (isExpanded != null) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevronRotation)
            )
        }
    }
}
