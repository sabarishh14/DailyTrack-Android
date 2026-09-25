package com.example.dailytrack_mobile.presentation.screens.money.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import com.example.dailytrack_mobile.presentation.components.MonthYearPickerDialog
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.dailytrack_mobile.presentation.screens.money.*
import com.example.dailytrack_mobile.presentation.theme.AppTheme
import com.example.dailytrack_mobile.presentation.theme.DtOgChartColors
import com.example.dailytrack_mobile.presentation.theme.LocalAppTheme
import com.example.dailytrack_mobile.presentation.util.Dimens


// ─────────────────────────────────────────────────────────────────────────────
// Cash Flow Breakdown Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun CashFlowBreakdownCard(
    categories: List<SpendingCategory>,
    transactions: List<Transaction>,
    periodLabel: String,
    hasActiveFilters: Boolean = false,
    isLoading: Boolean = false,
    onDrilldownChanged: (categories: Set<String>?) -> Unit = {},
    onViewTransactions: (String?) -> Unit
) {
    val dims = Dimens.current
    val total = categories.sumOf { it.amount }

    val currentTheme = LocalAppTheme.current
    val isDtOg = currentTheme == AppTheme.DT_OG
    val primaryColor = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()

    var activeDrilldownCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var drilldownBackStack by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

    val isOthersDrilldown = activeDrilldownCategory == "__OTHERS__"

    val handleBack: () -> Unit = {
        if (drilldownBackStack.isNotEmpty()) {
            activeDrilldownCategory = drilldownBackStack.last()
            drilldownBackStack = drilldownBackStack.dropLast(1)
        } else {
            activeDrilldownCategory = null
        }
    }

    // Intercept back button when drill-down is active
    BackHandler(enabled = activeDrilldownCategory != null) {
        handleBack()
    }

    // Filter transactions for the selected drill-down category
    val categoryTransactions = remember(transactions, activeDrilldownCategory) {
        if (activeDrilldownCategory == null || activeDrilldownCategory == "__OTHERS__") emptyList()
        else transactions.filter { it.category.equals(activeDrilldownCategory, ignoreCase = true) }
    }

    // Auto-reset if category no longer exists in filtered transactions (ignore for __OTHERS__)
    LaunchedEffect(categoryTransactions, activeDrilldownCategory, isLoading) {
        if (activeDrilldownCategory != null && !isOthersDrilldown && categoryTransactions.isEmpty() && !isLoading) {
            handleBack()
        }
    }

    // Calculate drill-down breakdown (Top 4 descriptions + "Others")
    val categoryTotal = remember(categoryTransactions) {
        categoryTransactions.sumOf { Math.abs(it.amount) }
    }

    val othersColor = if (isDtOg) Color(0xFF888888) else Color(0xFF7A889B)

    val drilldownItemsPerPage = 6
    val maxDrilldownPages = 5
    val maxDrilldownItems = drilldownItemsPerPage * maxDrilldownPages

    val processedDrilldownCategories = remember(categoryTransactions, categoryTotal, primaryColor, isDtOg, othersColor) {
        if (categoryTransactions.isEmpty()) emptyList()
        else {
            val grouped = categoryTransactions.groupBy { tx ->
                val raw = tx.note?.takeIf { it.isNotBlank() }
                    ?: tx.description?.takeIf { it.isNotBlank() }
                    ?: tx.title.takeIf { it.isNotBlank() && !it.equals(tx.category, ignoreCase = true) }
                    ?: "General"
                cleanDescriptionTitle(raw)
            }
            val aggregated = grouped.map { (desc, txs) ->
                desc to txs.sumOf { Math.abs(it.amount) }
            }.sortedByDescending { it.second }

            val items = if (aggregated.size <= maxDrilldownItems) {
                aggregated
            } else {
                val topItems = aggregated.take(maxDrilldownItems - 1)
                val othersSum = aggregated.drop(maxDrilldownItems - 1).sumOf { it.second }
                topItems + listOf("Others" to othersSum)
            }

            if (isDtOg) {
                items.mapIndexed { idx, (name, amt) ->
                    val color = if (name == "Others") othersColor else DtOgChartColors.PieColors[idx % DtOgChartColors.PieColors.size]
                    SpendingCategory(name, amt, color)
                }
            } else {
                val palette = generateThemeChartPalette(primaryColor, items.size)
                items.mapIndexed { idx, (name, amt) ->
                    val color = if (name == "Others") othersColor else palette[idx]
                    SpendingCategory(name, amt, color)
                }
            }
        }
    }

    val drilldownPages = remember(processedDrilldownCategories, drilldownItemsPerPage) {
        processedDrilldownCategories.chunked(drilldownItemsPerPage)
    }
    val drilldownPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { drilldownPages.size.coerceAtLeast(1) }
    )

    LaunchedEffect(activeDrilldownCategory) {
        if (activeDrilldownCategory != null && activeDrilldownCategory != "__OTHERS__" && drilldownPages.isNotEmpty() && drilldownPagerState.currentPage != 0) {
            drilldownPagerState.scrollToPage(0)
        }
    }

    // Top descriptions for the donut chart: Top 7 individual descriptions + 1 "Others" (with sleek slate color)
    val drilldownDonutCategories = remember(processedDrilldownCategories, categoryTotal, othersColor) {
        if (processedDrilldownCategories.size <= 7) {
            processedDrilldownCategories
        } else {
            val top7 = processedDrilldownCategories.take(7)
            val remainingAmt = (categoryTotal - top7.sumOf { it.amount }).coerceAtLeast(0.0)
            top7 + listOf(SpendingCategory("Others", remainingAmt, othersColor))
        }
    }

    // Assign colors to all categories. For > 10 categories, the top 9 get full-spread theme palette.
    val processedCategories = remember(categories, primaryColor, isDtOg) {
        if (isDtOg) {
            categories.mapIndexed { index, cat ->
                cat.copy(color = DtOgChartColors.PieColors[index % DtOgChartColors.PieColors.size])
            }
        } else {
            val palette = if (categories.size <= 10) {
                generateThemeChartPalette(primaryColor, categories.size)
            } else {
                val top9 = generateThemeChartPalette(primaryColor, 9)
                val rest = generateThemeChartPalette(primaryColor.copy(alpha = 0.7f), categories.size - 9)
                top9 + rest
            }
            categories.mapIndexed { index, cat ->
                cat.copy(color = palette[index])
            }
        }
    }

    // ─── OTHER CATEGORIES BREAKDOWN (For the "Others" Drilldown) ─────────────
    val otherCategoriesRaw = remember(categories) {
        if (categories.size <= 10) emptyList() else categories.drop(9)
    }
    val othersTotal = remember(otherCategoriesRaw) { otherCategoriesRaw.sumOf { it.amount } }

    // Lets the summary row below follow whatever the chart is drilled into (null = everything).
    LaunchedEffect(activeDrilldownCategory, otherCategoriesRaw) {
        onDrilldownChanged(
            when (activeDrilldownCategory) {
                null -> null
                "__OTHERS__" -> otherCategoriesRaw.map { it.name }.toSet()
                else -> setOf(activeDrilldownCategory!!)
            }
        )
    }

    val otherCategories = remember(otherCategoriesRaw, primaryColor, isDtOg) {
        if (otherCategoriesRaw.isEmpty()) emptyList()
        else {
            if (isDtOg) {
                otherCategoriesRaw.mapIndexed { idx, cat ->
                    cat.copy(color = DtOgChartColors.PieColors[(idx + 9) % DtOgChartColors.PieColors.size])
                }
            } else {
                val palette = generateThemeChartPalette(primaryColor.copy(alpha = 0.85f), otherCategoriesRaw.size)
                otherCategoriesRaw.mapIndexed { idx, cat ->
                    cat.copy(color = palette[idx])
                }
            }
        }
    }

    val othersDonutCategories = remember(otherCategories, othersTotal, othersColor) {
        if (otherCategories.size <= 7) {
            otherCategories
        } else {
            val top7 = otherCategories.take(7)
            val remainingAmt = (othersTotal - top7.sumOf { it.amount }).coerceAtLeast(0.0)
            top7 + listOf(SpendingCategory("Others", remainingAmt, othersColor))
        }
    }

    val otherCategoriesPages = remember(otherCategories) {
        otherCategories.chunked(6)
    }
    val otherCategoriesPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { otherCategoriesPages.size.coerceAtLeast(1) }
    )

    LaunchedEffect(activeDrilldownCategory) {
        if (isOthersDrilldown && otherCategoriesPages.isNotEmpty() && otherCategoriesPagerState.currentPage != 0) {
            otherCategoriesPagerState.scrollToPage(0)
        }
    }

    // Top categories for the main donut chart: Exactly 10 portions max (Top 9 individual categories + 1 "Others" slice with sleek slate color)
    val mainDonutCategories = remember(processedCategories, total, othersTotal, othersColor) {
        if (processedCategories.size <= 10) {
            processedCategories
        } else {
            val top9 = processedCategories.take(9)
            val othersSlice = SpendingCategory("Others", othersTotal, othersColor)
            top9 + listOf(othersSlice)
        }
    }

    // Main level pills: Exactly 10 pills (Top 9 individual categories + 1 "Others" pill). NO pager needed!
    val mainPills = remember(categories, processedCategories, total, othersTotal, otherCategoriesRaw, othersColor) {
        if (categories.size <= 10) {
            processedCategories
        } else {
            val top9 = processedCategories.take(9)
            val othersPill = SpendingCategory("Others (${otherCategoriesRaw.size})", othersTotal, othersColor)
            top9 + listOf(othersPill)
        }
    }

    val donutSize = dims.donutChartSize * 0.78f
    val viewTransactions = { onViewTransactions(if (isOthersDrilldown) null else activeDrilldownCategory) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dims.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = dims.cardInnerPadding,
                    end = dims.cardInnerPadding,
                    top = dims.cardInnerPadding * 0.7f,
                    bottom = (dims.cardInnerPadding - 6.dp).coerceAtLeast(8.dp)
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            if (activeDrilldownCategory != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { handleBack() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Back",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Back",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = if (isOthersDrilldown) "📦 OTHER CATEGORIES" else "${CategoryEmojis.forCategory(activeDrilldownCategory!!)} ${activeDrilldownCategory!!.uppercase()}",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val share = if (total > 0) {
                        if (isOthersDrilldown) (othersTotal / total) * 100.0 else (categoryTotal / total) * 100.0
                    } else 0.0
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "%.1f%%".format(share),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = "SPENDING ANALYSER — ${periodLabel.uppercase()}",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(dims.itemSpacingMedium))

            // Donut chart with center text (3D for DT_OG, 2D for standard themes) or Inside-Card Loading
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading && categories.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(donutSize),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(dims.itemSpacingMedium))
                        Text(
                            text = "Analyzing spending...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (isOthersDrilldown) {
                    DonutChart(
                        categories = othersDonutCategories,
                        total = othersTotal,
                        isDtOgStyle = isDtOg,
                        centerTitle = "OTHER CATS",
                        modifier = Modifier.size(donutSize)
                    )
                } else if (activeDrilldownCategory != null) {
                    DonutChart(
                        categories = drilldownDonutCategories,
                        total = categoryTotal,
                        isDtOgStyle = isDtOg,
                        centerTitle = "${CategoryEmojis.forCategory(activeDrilldownCategory!!)} ${activeDrilldownCategory!!.uppercase()}",
                        modifier = Modifier.size(donutSize)
                    )
                } else {
                    DonutChart(
                        categories = mainDonutCategories,
                        total = total,
                        isDtOgStyle = isDtOg,
                        centerTitle = "TOTAL",
                        modifier = Modifier.size(donutSize)
                    )
                }
            }

            if (!isLoading) {
                Spacer(modifier = Modifier.height(dims.itemSpacingSmall))

                if (isOthersDrilldown) {
                    // Drilldown Mode: OTHER CATEGORIES (Paginated 6 per page)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ALL OTHER CATEGORIES",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TransactionsLink(onClick = viewTransactions)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (otherCategoriesPages.size > 1) {
                        HorizontalPager(
                            state = otherCategoriesPagerState,
                            modifier = Modifier.fillMaxWidth()
                        ) { pageIndex ->
                            val pageCategories = otherCategoriesPages.getOrElse(pageIndex) { emptyList() }
                            LegendGrid(
                                categories = pageCategories,
                                totalAmount = othersTotal,
                                isDtOg = isDtOg,
                                onCategoryClick = { clickedCat ->
                                    drilldownBackStack = drilldownBackStack + listOf("__OTHERS__")
                                    activeDrilldownCategory = clickedCat
                                }
                            )
                        }

                        // Page Indicator Dots
                        Spacer(modifier = Modifier.height(dims.itemSpacingMedium))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(otherCategoriesPages.size) { index ->
                                val isSelected = otherCategoriesPagerState.currentPage == index
                                val width by animateDpAsState(
                                    targetValue = if (isSelected) 18.dp else 6.dp,
                                    label = "others_dot_width"
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .height(6.dp)
                                        .width(width)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                                        )
                                        .clickable {
                                            scope.launch {
                                                otherCategoriesPagerState.animateScrollToPage(index)
                                            }
                                        }
                                )
                            }
                        }
                    } else if (otherCategoriesPages.isNotEmpty()) {
                        LegendGrid(
                            categories = otherCategoriesPages[0],
                            totalAmount = othersTotal,
                            isDtOg = isDtOg,
                            onCategoryClick = { clickedCat ->
                                drilldownBackStack = drilldownBackStack + listOf("__OTHERS__")
                                activeDrilldownCategory = clickedCat
                            }
                        )
                    }
                } else if (activeDrilldownCategory != null) {
                    // Drilldown Mode: TOP DESCRIPTIONS (Paginated & Swipeable)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOP DESCRIPTIONS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TransactionsLink(onClick = viewTransactions)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (drilldownPages.size > 1) {
                        HorizontalPager(
                            state = drilldownPagerState,
                            modifier = Modifier.fillMaxWidth()
                        ) { pageIndex ->
                            val pageCategories = drilldownPages.getOrElse(pageIndex) { emptyList() }
                            LegendGrid(
                                categories = pageCategories,
                                totalAmount = categoryTotal,
                                isDtOg = isDtOg,
                                onCategoryClick = { /* Leaf description pills */ }
                            )
                        }

                        // Page Indicator Dots
                        Spacer(modifier = Modifier.height(dims.itemSpacingMedium))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(drilldownPages.size) { index ->
                                val isSelected = drilldownPagerState.currentPage == index
                                val width by animateDpAsState(
                                    targetValue = if (isSelected) 18.dp else 6.dp,
                                    label = "drilldown_dot_width"
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .height(6.dp)
                                        .width(width)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                                        )
                                        .clickable {
                                            scope.launch {
                                                drilldownPagerState.animateScrollToPage(index)
                                            }
                                        }
                                )
                            }
                        }
                    } else if (drilldownPages.isNotEmpty()) {
                        LegendGrid(
                            categories = drilldownPages[0],
                            totalAmount = categoryTotal,
                            isDtOg = isDtOg,
                            onCategoryClick = { /* Leaf description pills */ }
                        )
                    }
                } else {
                    // Normal Mode: CATEGORIES (Max 10 pills, exactly matching Donut Chart 1:1, NO Pager!)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CATEGORIES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "  ·  tap to explore",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        TransactionsLink(onClick = viewTransactions)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LegendGrid(
                        categories = mainPills,
                        totalAmount = total,
                        isDtOg = isDtOg,
                        onCategoryClick = { clickedCat ->
                            if (clickedCat.startsWith("Others")) {
                                drilldownBackStack = emptyList()
                                activeDrilldownCategory = "__OTHERS__"
                            } else {
                                drilldownBackStack = emptyList()
                                activeDrilldownCategory = clickedCat
                            }
                        }
                    )
                }

            }
        }
    }
}

/**
 * Opens the transactions behind what the card shows (the whole period, one
 * category, or "others"). Sits at the end of each section header, where the
 * eye already is, instead of a full-width button under the chips.
 */
@Composable
private fun TransactionsLink(onClick: () -> Unit) {
    Text(
        text = "Transactions ›",
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty Filter Results Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun EmptyFilterResultsCard(
    onResetFilters: () -> Unit
) {
    val dims = Dimens.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dims.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.cardInnerPadding * 1.5f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
        ) {
            Icon(
                imageVector = Icons.Outlined.FilterAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "No Transactions Found",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "No transactions match your active filters. Try adjusting or clearing filters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onResetFilters
            ) {
                Text("Reset Filters")
            }
        }
    }
}

