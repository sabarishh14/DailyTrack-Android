package com.example.dailytrack_mobile.presentation.screens.money.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.domain.money.AccountRow
import com.example.dailytrack_mobile.domain.money.AccountStats
import com.example.dailytrack_mobile.presentation.screens.money.ChartColors
import com.example.dailytrack_mobile.presentation.screens.money.SpendingCategory
import com.example.dailytrack_mobile.presentation.theme.AppTheme
import com.example.dailytrack_mobile.presentation.theme.DtOgChartColors
import com.example.dailytrack_mobile.presentation.theme.LocalAppTheme
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The analyser grouped by account (the web's AccountAverages): the accounts'
 * donut for the filters, then each one's latest month next to its usual month
 * (the average of the 12 before). Tapping an account narrows the analyser to it,
 * by category.
 */
@Composable
internal fun AccountBreakdownCard(
    stats: AccountStats,
    periodLabel: String,
    /** Expenses only: a rise is bad news. */
    spending: Boolean,
    onPick: (String) -> Unit
) {
    val dims = Dimens.current
    val isDtOg = LocalAppTheme.current == AppTheme.DT_OG
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outline
    val colors = remember(stats, primary, isDtOg) {
        val palette = if (isDtOg) List(stats.shown.size) { DtOgChartColors.PieColors[it % DtOgChartColors.PieColors.size] }
            else generateThemeChartPalette(primary, stats.shown.size.coerceAtLeast(1))
        stats.shown.mapIndexed { i, (account, _) -> account to palette[i] }.toMap()
    }
    val slices = remember(stats, colors) {
        stats.shown.map { (account, amount) -> SpendingCategory(account, amount, colors[account] ?: muted) }
    }
    val total = stats.shown.sumOf { it.second }
    val latestLabel = stats.latest?.month?.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) ?: ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dims.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
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
            Text(
                text = "BY ACCOUNT — ${periodLabel.uppercase()}",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(dims.itemSpacingMedium))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(primary.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                DonutChart(
                    categories = slices,
                    total = total,
                    isDtOgStyle = isDtOg,
                    centerTitle = "TOTAL",
                    modifier = Modifier.size(dims.donutChartSize * 0.78f)
                )
            }
            Spacer(Modifier.height(dims.itemSpacingLarge))
            // Column heads instead of a sentence: what the two figures on a row are.
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                ColumnHead("Account", Modifier.weight(1f))
                ColumnHead(latestLabel)
            }
            stats.rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                AccountStatRow(row, colors[row.account] ?: muted, spending) { onPick(row.account) }
            }
        }
    }
}

@Composable
private fun ColumnHead(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = modifier
    )
}

/** Name, its share and usual month on the left; the latest month and how it compares on the right. */
@Composable
private fun AccountStatRow(row: AccountRow, color: Color, spending: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.account,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = listOfNotNull(
                    row.share.roundToInt().takeIf { it > 0 }?.let { "$it%" },
                    row.average?.let { "avg ${rupees(it)}/mo" } ?: "New"
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                rupees(row.latest),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = colors.onSurface,
                maxLines = 1
            )
            row.change?.let {
                Spacer(Modifier.height(4.dp))
                ChangeChip(it, spending)
            }
        }
    }
}

/** "↑ 21%" in a tinted capsule: red when spending rises (or income falls), green the other way. */
@Composable
private fun ChangeChip(change: Double, spending: Boolean) {
    val rounded = change.roundToInt()
    val color = when {
        rounded == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        (rounded > 0) == spending -> ChartColors.ExpenseRed
        else -> ChartColors.IncomeGreen
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(start = if (rounded == 0) 8.dp else 5.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rounded != 0) {
            Icon(
                imageVector = if (rounded > 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = if (rounded > 0) "up" else "down",
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(2.dp))
        }
        Text(
            "${abs(rounded)}%",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = color
        )
    }
}

private fun rupees(amount: Double): String = formatExactCurrency(amount.roundToInt().toDouble())
