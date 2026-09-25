package com.example.dailytrack_mobile.presentation.screens.money.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.presentation.screens.money.BudgetProgress
import com.example.dailytrack_mobile.presentation.screens.money.MoneyAction
import com.example.dailytrack_mobile.presentation.screens.money.MoneyState
import com.example.dailytrack_mobile.presentation.util.Dimens

// ─────────────────────────────────────────────────────────────────────────────
// Budget Goals — current calendar month against the per-category limits.
//
// Deliberately collapsed to the three categories that need attention; the rest
// expand on demand so the Analysis tab keeps its shape as budgets are added.
// ─────────────────────────────────────────────────────────────────────────────

private const val COLLAPSED_ROW_COUNT = 3

@Composable
fun BudgetSection(
    state: MoneyState,
    onAction: (MoneyAction) -> Unit
) {
    val dims = Dimens.current
    val progress = state.budgetProgress

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dims.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(dims.cardInnerPadding)) {
            BudgetHeader(
                monthLabel = state.budgetMonthLabel,
                daysRemaining = state.budgetDaysRemaining,
                overCount = state.budgetsOverLimitCount,
                hasBudgets = progress.isNotEmpty(),
                onEdit = { onAction(MoneyAction.SetBudgetSheetVisible(true)) }
            )

            if (progress.isEmpty()) {
                BudgetEmptyState(onSetBudgets = { onAction(MoneyAction.SetBudgetSheetVisible(true)) })
                return@Column
            }

            Spacer(Modifier.height(16.dp))

            OverallBudgetBar(
                spent = state.totalBudgetSpent,
                limit = state.totalBudgetLimit,
                fraction = state.totalBudgetFraction,
                monthElapsed = state.monthElapsedFraction
            )

            Spacer(Modifier.height(18.dp))

            val expanded = state.isBudgetSectionExpanded
            val visibleRows = if (expanded) progress else progress.take(COLLAPSED_ROW_COUNT)

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                visibleRows.forEach { item ->
                    key(item.category) { BudgetRow(item) }
                }
            }

            if (progress.size > COLLAPSED_ROW_COUNT) {
                Spacer(Modifier.height(6.dp))
                ShowMoreToggle(
                    expanded = expanded,
                    hiddenCount = progress.size - COLLAPSED_ROW_COUNT,
                    onToggle = { onAction(MoneyAction.SetBudgetSectionExpanded(!expanded)) }
                )
            }
        }
    }
}

@Composable
private fun BudgetHeader(
    monthLabel: String,
    daysRemaining: Int,
    overCount: Int,
    hasBudgets: Boolean,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "BUDGETS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = when {
                    !hasBudgets -> monthLabel
                    overCount > 0 -> "$overCount over limit"
                    daysRemaining > 0 -> "$monthLabel · $daysRemaining days left"
                    else -> "$monthLabel · last day"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (overCount > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
            )
        }

        if (hasBudgets && com.example.dailytrack_mobile.presentation.access.LocalAccess.current.canEdit(com.example.dailytrack_mobile.data.local.auth.AccessModule.MONEY)) {
            FilledTonalIconButton(
                onClick = onEdit,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Manage budgets",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun BudgetEmptyState(onSetBudgets: () -> Unit) {
    Spacer(Modifier.height(10.dp))
    Text(
        text = "Set a monthly limit per category and track it here as the month goes.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(14.dp))
    if (com.example.dailytrack_mobile.presentation.access.LocalAccess.current.canEdit(com.example.dailytrack_mobile.data.local.auth.AccessModule.MONEY)) FilledTonalButton(
        onClick = onSetBudgets,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text("Set budgets", fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The all-categories total, with a tick marking how far into the month we are.
 * Spend sitting left of the tick means the pace is comfortable.
 */
@Composable
private fun OverallBudgetBar(
    spent: Double,
    limit: Double,
    fraction: Float,
    monthElapsed: Float
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "OverallBudgetFill"
    )
    val isOver = spent > limit
    val barColor = when {
        isOver -> MaterialTheme.colorScheme.error
        fraction >= 0.8f -> BudgetWarningColor
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = formatExactCurrency(spent),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "of ${formatExactCurrency(limit)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp)
        )
    }

    Spacer(Modifier.height(10.dp))

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, size = size, cornerRadius = radius)
        if (animatedFraction > 0f) {
            drawRoundRect(
                color = barColor,
                size = Size(size.width * animatedFraction, size.height),
                cornerRadius = radius
            )
        }
        // Pace marker — today's position within the month.
        val markerX = (size.width * monthElapsed).coerceIn(0f, size.width)
        drawLine(
            color = markerColor,
            start = Offset(markerX, -2f),
            end = Offset(markerX, size.height + 2f),
            strokeWidth = 2f
        )
    }

    Spacer(Modifier.height(8.dp))

    Text(
        text = if (isOver) {
            "${formatExactCurrency(spent - limit)} over budget"
        } else {
            "${formatExactCurrency(limit - spent)} left · ${(fraction * 100).toInt()}% used"
        },
        style = MaterialTheme.typography.labelMedium,
        color = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun BudgetRow(item: BudgetProgress) {
    val animatedFraction by animateFloatAsState(
        targetValue = item.fraction,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "BudgetRowFill-${item.category}"
    )
    val barColor = when {
        item.isOver -> MaterialTheme.colorScheme.error
        item.isNearLimit -> BudgetWarningColor
        else -> item.color
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(text = item.emoji, fontSize = 14.sp)
                Spacer(Modifier.width(7.dp))
                Text(
                    text = item.category,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "${formatShortened(item.spent)} / ${formatShortened(item.limit)}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (item.isOver) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(7.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }

        if (item.isOver) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = "${formatExactCurrency(item.spent - item.limit)} over",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ShowMoreToggle(
    expanded: Boolean,
    hiddenCount: Int,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (expanded) "Show less" else "Show $hiddenCount more",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(17.dp)
        )
    }
}

/** Amber for "close to the limit" — distinct from both the error red and any category hue. */
internal val BudgetWarningColor = Color(0xFFF5A623)
