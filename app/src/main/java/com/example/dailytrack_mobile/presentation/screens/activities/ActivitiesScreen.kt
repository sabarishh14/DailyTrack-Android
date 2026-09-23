package com.example.dailytrack_mobile.presentation.screens.activities

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.presentation.components.DailyTrackPullToRefreshBox
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Activity type colour palette (theme-agnostic accent colours)
// ─────────────────────────────────────────────────────────────────────────────

private val activityColors: Map<ActivityType, Color> = mapOf(
    ActivityType.GYM          to Color(0xFF2ECC71),   // emerald green
    ActivityType.BADMINTON     to Color(0xFFF39C12),   // amber
    ActivityType.CRICKET       to Color(0xFF3498DB),   // sky blue
    ActivityType.TABLE_TENNIS  to Color(0xFF9B59B6),   // purple
    ActivityType.OTHERS        to Color(0xFF95A5A6),   // slate
)

// ─────────────────────────────────────────────────────────────────────────────
// Main composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ActivitiesScreen(
    viewModel: ActivitiesVM = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val dims = Dimens.current

    // Derived stats for the selected month
    val activeDays   = state.activityLog.count { it.activities.isNotEmpty() }
    val totalSessions = state.activityLog.sumOf { it.activities.size }
    val gymDays      = state.activityLog.count { it.activities.contains(ActivityType.GYM) }

    // Per-type counts for summary pills
    val typeCounts = ActivityType.entries.associateWith { type ->
        state.activityLog.count { entry -> entry.activities.contains(type) }
    }

    DailyTrackPullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.onAction(ActivitiesAction.Refresh) },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = dims.screenHorizontalPadding,
                end = dims.screenHorizontalPadding,
                top = dims.screenTopPadding,
                bottom = dims.screenBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(dims.sectionSpacing)
        ) {
            // ── 1. Stats row ───────────────────────────────────────────────────
            item {
                StatsRow(
                    activeDays    = activeDays,
                    totalSessions = totalSessions,
                    typeCounts    = typeCounts
                )
            }

            // ── 2. Month selector + summary pills ─────────────────────────────
            item {
                MonthSummarySection(
                    selectedMonth = state.selectedMonth,
                    selectedYear  = state.selectedYear,
                    typeCounts    = typeCounts,
                    onMonthChange = { month, year ->
                        viewModel.onAction(ActivitiesAction.OnMonthChanged(month, year))
                    }
                )
            }

            // ── 3. Activity log header ─────────────────────────────────────────
            item {
                Text(
                    text       = "ACTIVITY LOG",
                    style      = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color      = MaterialTheme.colorScheme.primary
                )
            }

            // ── 4. Log entries ─────────────────────────────────────────────────
            items(state.activityLog) { entry ->
                ActivityLogRow(entry = entry)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stats row – three cards
// ─────────────────────────────────────────────────────────────────────────────

// The ordered list of activity types that the easter-egg card cycles through
private val cycleOrder = listOf(
    ActivityType.GYM,
    ActivityType.BADMINTON,
    ActivityType.CRICKET,
    ActivityType.TABLE_TENNIS,
    ActivityType.OTHERS
)

@Composable
private fun StatsRow(
    activeDays: Int,
    totalSessions: Int,
    typeCounts: Map<ActivityType, Int>
) {
    val dims = Dimens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            value = activeDays.toString(),
            label = "Active Days",
            valueColor = MaterialTheme.colorScheme.primary
        )
        StatCard(
            modifier = Modifier.weight(1f),
            value = totalSessions.toString(),
            label = "Sessions",
            valueColor = MaterialTheme.colorScheme.tertiary
        )
        // ── Rightmost card: secret easter-egg cycling card ─────────────────
        CyclingStatCard(
            modifier   = Modifier.weight(1f),
            typeCounts = typeCounts
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    valueColor: Color
) {
    val dims = Dimens.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(dims.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dims.statCardPaddingVertical, horizontal = dims.statCardPaddingHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dims.itemSpacingSmall)
        ) {
            Text(
                text  = value,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize   = dims.statCardValueFontSize
                ),
                color = valueColor
            )
            Text(
                text  = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Easter-egg cycling card (rightmost stat card)
// Swipe down from the top to reveal other activity-day counts silently.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CyclingStatCard(
    modifier: Modifier = Modifier,
    typeCounts: Map<ActivityType, Int>
) {
    val dims = Dimens.current
    var cycleIndex by remember { mutableIntStateOf(0) }
    // Track cumulative drag to fire the cycle only once per swipe-down gesture
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 40f   // dp-ish pixels before we advance

    val currentType  = cycleOrder[cycleIndex]
    val currentValue = (typeCounts[currentType] ?: 0).toString()
    val currentLabel = when (currentType) {
        ActivityType.GYM         -> "Gym Days"
        ActivityType.BADMINTON   -> "Badminton Days"
        ActivityType.CRICKET     -> "Cricket Days"
        ActivityType.TABLE_TENNIS -> "TT Days"
        ActivityType.OTHERS      -> "Other Days"
    }
    val currentColor = activityColors[currentType] ?: Color(0xFFF39C12)

    Card(
        modifier = modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { dragAccumulator = 0f },
                onDragEnd   = { dragAccumulator = 0f },
                onDragCancel = { dragAccumulator = 0f },
                onVerticalDrag = { change, dragAmount ->
                    // Only accumulate + consume downward motion (positive dragAmount) —
                    // this card owns that gesture. Upward drags are left unconsumed so
                    // the enclosing scrollable page still receives them; consuming both
                    // directions here fought the page's own scroll on every swipe that
                    // started on this card, which read as the whole page jittering.
                    if (dragAmount > 0f) {
                        change.consume()
                        dragAccumulator += dragAmount
                        if (dragAccumulator >= swipeThreshold) {
                            // Advance to next type and reset accumulator
                            cycleIndex = (cycleIndex + 1) % cycleOrder.size
                            dragAccumulator = 0f
                        }
                    } else {
                        // Upward drag resets the accumulator without cycling
                        dragAccumulator = 0f
                    }
                }
            )
        },
        shape = RoundedCornerShape(dims.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dims.statCardPaddingVertical, horizontal = dims.statCardPaddingHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dims.itemSpacingSmall)
        ) {
            // Animate the value number sliding in from below when cycling
            AnimatedContent(
                targetState = currentValue,
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith
                    (slideOutVertically { -it } + fadeOut())
                },
                label = "cycling_value"
            ) { value ->
                Text(
                    text  = value,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize   = dims.statCardValueFontSize
                    ),
                    color = currentColor
                )
            }
            // Animate the label fading in/out
            AnimatedContent(
                targetState = currentLabel,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "cycling_label"
            ) { label ->
                Text(
                    text  = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Month selector + summary pills section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MonthSummarySection(
    selectedMonth: Month,
    selectedYear: Int,
    typeCounts: Map<ActivityType, Int>,
    onMonthChange: (Month, Int) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val dims = Dimens.current

    Column(verticalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge)) {

        // ── Header row: "THIS MONTH" label + calendar icon ────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
            ) {
                Text(
                    text  = "THIS MONTH",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                // Small calendar icon button
                IconButton(
                    onClick  = { showPicker = true },
                    modifier = Modifier.size(dims.avatarSizeSmall)
                ) {
                    Icon(
                        imageVector        = Icons.Default.CalendarMonth,
                        contentDescription = "Pick month",
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(dims.iconSizeSmall + 2.dp)
                    )
                }
            }

            // Current month label (read-only)
            Text(
                text  = "${selectedMonth.getDisplayName(TextStyle.FULL, Locale.getDefault())} $selectedYear",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Month picker dialog ────────────────────────────────────────────
        if (showPicker) {
            MonthYearPickerDialog(
                selectedMonth = selectedMonth,
                selectedYear  = selectedYear,
                onDismiss     = { showPicker = false },
                onSelected    = { m, y ->
                    onMonthChange(m, y)
                    showPicker = false
                }
            )
        }

        // ── Activity type summary pills ────────────────────────────────────
        ActivitySummaryPills(typeCounts = typeCounts)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Month/Year picker dropdown
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MonthYearPickerDialog(
    selectedMonth: Month,
    selectedYear: Int,
    onDismiss: () -> Unit,
    onSelected: (Month, Int) -> Unit
) {
    var displayYear by remember { mutableIntStateOf(selectedYear) }
    val dims = Dimens.current
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val dialogFraction = when {
        screenWidth < 360 -> 0.92f
        screenWidth <= 410 -> 0.88f
        else -> 0.82f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape  = RoundedCornerShape(dims.cardCornerRadius + 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier  = Modifier
                .fillMaxWidth(dialogFraction)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(dims.cardInnerPadding),
                verticalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge)
            ) {
                // Dialog title
                Text(
                    text  = "Select Month",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Year nav
                Row(
                    modifier             = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment    = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { displayYear-- }) {
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Previous year",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text  = displayYear.toString(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { displayYear++ }) {
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "Next year",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Divider
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // Month grid (3 columns × 4 rows)
                val months  = Month.entries
                val chunked = months.chunked(3)
                chunked.forEach { row ->
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
                    ) {
                        row.forEach { month ->
                            val isSelected = month == selectedMonth && displayYear == selectedYear
                            val bgColor    = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                Color.Transparent
                            val textColor  = if (isSelected)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgColor)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication        = null
                                    ) { onSelected(month, displayYear) }
                                    .padding(vertical = dims.itemSpacingLarge),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text  = month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = textColor
                                )
                            }
                        }
                    }
                }

                // Cancel button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text  = "Cancel",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Activity summary pills (Gym 5x  Badminton 2x  …)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivitySummaryPills(typeCounts: Map<ActivityType, Int>) {
    val dims = Dimens.current
    // Use FlowRow for responsive wrapping instead of hardcoded chunked(3)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium),
        verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
    ) {
        ActivityType.entries.forEach { type ->
            val count = typeCounts[type] ?: 0
            SummaryPill(type = type, count = count)
        }
    }
}

@Composable
private fun SummaryPill(type: ActivityType, count: Int) {
    val dims = Dimens.current
    val accentColor = activityColors[type] ?: MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(50),
        color = accentColor.copy(alpha = 0.12f),
        modifier = Modifier.border(
            width  = 1.5.dp,
            color  = accentColor.copy(alpha = 0.35f),
            shape  = RoundedCornerShape(50)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dims.itemSpacingLarge, vertical = dims.itemSpacingMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingSmall)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Text(
                text  = type.label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text  = "${count}x",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = accentColor
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Activity log row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActivityLogRow(entry: ActivityEntry) {
    val dims = Dimens.current
    val hasActivities = entry.activities.isNotEmpty()

    Card(
        shape  = RoundedCornerShape(dims.cardCornerRadius - 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingLarge),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge)
        ) {
            // Date block
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.width(dims.avatarSizeMedium)
            ) {
                Text(
                    text  = entry.dayOfWeek,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight    = FontWeight.Medium,
                        letterSpacing = 0.8.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text  = entry.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize   = dims.fontSizeHeadlineMedium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(dims.dividerLineHeight)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )

            // Activity pills OR "Rest day"
            if (hasActivities) {
                Row(
                    modifier              = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    entry.activities.forEach { type ->
                        ActivityPill(type = type)
                    }
                }
            } else {
                Text(
                    text     = "Rest day",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                )
            }

            // Checkmark (only on active days)
            if (hasActivities) {
                Box(
                    modifier = Modifier
                        .size(dims.avatarSizeSmall)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier           = Modifier.size(dims.iconSizeSmall)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Individual activity pill in the log row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActivityPill(type: ActivityType) {
    val dims = Dimens.current
    val accentColor = activityColors[type] ?: MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(50),
        color = accentColor.copy(alpha = 0.18f)
    ) {
        Text(
            text     = type.label,
            style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color    = accentColor,
            modifier = Modifier.padding(horizontal = dims.itemSpacingLarge, vertical = dims.itemSpacingSmall)
        )
    }
}
