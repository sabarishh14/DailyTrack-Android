package com.example.dailytrack_mobile.presentation.components.transaction

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.dailytrack_mobile.data.remote.dto.AccountDto
import com.example.dailytrack_mobile.data.repository.isCcAccount
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// ─────────────────────────────────────────────────────────────────────────────
// What an entry does to its account: "₹14,230 → ₹13,730", measured against the
// minimum the user wants to keep there. Mirrors the web's BalanceImpact.
// ─────────────────────────────────────────────────────────────────────────────

/** What an entry does to its account: a balance, or a credit card's spending this month. */
sealed interface AccountImpact

data class BalanceProjection(val account: String, val before: Double, val after: Double, val min: Double?) : AccountImpact

/** A card's used-this-month before and after the entry, against its monthly budget. */
data class CardProjection(val account: String, val before: Double, val after: Double, val budget: Double?) : AccountImpact

enum class BalanceLevel { OK, WARN, DANGER }

/** Below the floor is DANGER, within half the floor above it WARN. Without a floor only overdraft warns. */
fun balanceLevel(after: Double, min: Double?): BalanceLevel = when {
    min != null && after < min -> BalanceLevel.DANGER
    min != null && after < min * 1.5 -> BalanceLevel.WARN
    min == null && after < 0 -> BalanceLevel.DANGER
    else -> BalanceLevel.OK
}

/** Over its budget a card is DANGER, from 80% of it WARN. Without a budget nothing warns. */
fun cardLevel(used: Double, budget: Double?): BalanceLevel = when {
    budget == null -> BalanceLevel.OK
    used > budget -> BalanceLevel.DANGER
    used >= budget * 0.8 -> BalanceLevel.WARN
    else -> BalanceLevel.OK
}

/**
 * Like [projectBalances], for credit cards: what each entry adds to its card's
 * spending this month (a refund takes it back down). Entries dated in another
 * month don't touch this month's number, so they get no projection.
 */
fun projectCardSpend(
    entries: List<TransactionEntryState>,
    accounts: List<AccountDto>,
    today: LocalDate = LocalDate.now()
): Map<Long, CardProjection> {
    val byName = accounts.associateBy { it.account }
    val running = mutableMapOf<String, Double>()
    val out = mutableMapOf<Long, CardProjection>()
    for (entry in entries) {
        val name = entry.account ?: continue
        if (!isCcAccount(name)) continue
        val used = byName[name]?.usedThisMonth ?: continue
        val amount = entry.evaluatedAmount?.takeIf { it > 0 } ?: continue
        val date = Instant.ofEpochMilli(entry.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        if (YearMonth.from(date) != YearMonth.from(today)) continue
        val before = running[name] ?: used
        val after = if (entry.type == EntryType.INCOME) maxOf(0.0, before - amount) else before + amount
        running[name] = after
        out[entry.id] = CardProjection(name, before, after, byName[name]?.monthlyBudget)
    }
    return out
}

/**
 * Walks the entries in order so two on the same account stack up. Only entries
 * that move a tracked balance the user can see get a projection.
 */
fun projectBalances(entries: List<TransactionEntryState>, accounts: List<AccountDto>): Map<Long, BalanceProjection> {
    val byName = accounts.associateBy { it.account }
    val running = mutableMapOf<String, Double>()
    val out = mutableMapOf<Long, BalanceProjection>()
    for (entry in entries) {
        val name = entry.account ?: continue
        val acc = byName[name] ?: continue
        val balance = acc.balance ?: continue
        if (!acc.balanceTracked || isCcAccount(name)) continue
        val amount = entry.evaluatedAmount?.takeIf { it > 0 } ?: continue
        val before = running[name] ?: balance
        val after = if (entry.type == EntryType.INCOME) before + amount else before - amount
        running[name] = after
        out[entry.id] = BalanceProjection(name, before, after, acc.minBalance)
    }
    return out
}

@Composable
fun balanceLevelColor(level: BalanceLevel): Color = when (level) {
    BalanceLevel.OK -> entryTypeAccent(EntryType.INCOME)
    BalanceLevel.WARN -> Color(0xFFF59E0B)
    BalanceLevel.DANGER -> MaterialTheme.colorScheme.error
}

private fun floorNote(after: Double, min: Double?, level: BalanceLevel): String? = when {
    min == null -> if (level == BalanceLevel.DANGER) "Overdrawn" else null
    level == BalanceLevel.DANGER -> "₹${formatRupees(min - after)} below your ₹${formatRupees(min)} minimum"
    else -> "₹${formatRupees(after - min)} above your ₹${formatRupees(min)} minimum"
}

/** Each account's colour, matching the web's BANKS. Credit cards share one. */
fun bankColor(account: String): Color? = when {
    isCcAccount(account) -> Color(0xFFEC4899)
    else -> when (account.trim().uppercase()) {
        "KOTAK" -> Color(0xFFEF4444)
        "IDBI" -> Color(0xFF22C55E)
        "FEDERAL" -> Color(0xFFF97316)
        "CUB" -> Color(0xFFA855F7)
        "INDIAN" -> Color(0xFF3B82F6)
        "ICICI" -> Color(0xFFEAB308)
        "CASH" -> Color(0xFF10B981)
        else -> null
    }
}

/**
 * One compact line under the account picker: the account in its colour, where
 * the balance goes, and a thin bar against the floor. Says more only when the
 * floor is close or crossed.
 */
@Composable
fun EntryBalancePreview(projection: BalanceProjection, modifier: Modifier = Modifier) {
    val level = balanceLevel(projection.after, projection.min)
    val accountColor = bankColor(projection.account) ?: MaterialTheme.colorScheme.primary
    // The bar keeps the account's colour until the floor needs attention.
    val barColor by animateColorAsState(
        if (level == BalanceLevel.OK) accountColor else balanceLevelColor(level), tween(250), label = "balanceColor"
    )
    val note = floorNote(projection.after, projection.min, level).takeIf { level != BalanceLevel.OK }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (level == BalanceLevel.DANGER) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            else entryCardColor()
        ),
        border = entryCardBorder(),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accountColor)
            )
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = projection.account.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f)
                    )
                    if (projection.after != projection.before) {
                        Text(
                            text = "₹${formatRupees(projection.before)}  →  ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = "₹${formatRupees(projection.after)}",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (level == BalanceLevel.OK) MaterialTheme.colorScheme.onSurface else barColor
                    )
                }
                FloorBar(projection.before, projection.after, projection.min, barColor)
                note?.let {
                    Text(
                        text = if (level == BalanceLevel.DANGER) "⚠ $it" else it,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = barColor
                    )
                }
            }
        }
    }
}

/**
 * The credit card version of [EntryBalancePreview]: this month's spending on
 * the card and where the entry takes it, with a bar towards the budget. Says
 * more only when the budget is close or crossed.
 */
@Composable
fun CardSpendPreview(projection: CardProjection, modifier: Modifier = Modifier) {
    val budget = projection.budget
    val level = cardLevel(projection.after, budget)
    val accountColor = bankColor(projection.account) ?: MaterialTheme.colorScheme.primary
    val barColor by animateColorAsState(
        if (level == BalanceLevel.OK) accountColor else balanceLevelColor(level), tween(250), label = "cardSpendColor"
    )
    val note = when {
        budget == null || level == BalanceLevel.OK -> null
        level == BalanceLevel.DANGER -> "⚠ ₹${formatRupees(projection.after - budget)} over your ₹${formatRupees(budget)} monthly budget"
        else -> "₹${formatRupees(budget - projection.after)} left of your ₹${formatRupees(budget)} monthly budget"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (level == BalanceLevel.DANGER) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            else entryCardColor()
        ),
        border = entryCardBorder(),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accountColor)
            )
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${projection.account.uppercase()} · THIS MONTH",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f)
                    )
                    if (projection.after != projection.before) {
                        Text(
                            text = "₹${formatRupees(projection.before)}  →  ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = "₹${formatRupees(projection.after)} used",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (level == BalanceLevel.OK) MaterialTheme.colorScheme.onSurface else barColor
                    )
                }
                // Filling towards the budget; once over, the tick marks where the budget was.
                if (budget != null) FloorBar(before = budget, after = projection.after, min = budget, color = barColor)
                note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = barColor
                    )
                }
            }
        }
    }
}

/** How much of the pre-entry balance is left, with a tick where the floor sits. */
@Composable
private fun FloorBar(before: Double, after: Double, min: Double?, color: Color) {
    val top = maxOf(before, after, min ?: 0.0, 1.0)
    val fill by animateFloatAsState((after / top).toFloat().coerceIn(0f, 1f), tween(300), label = "balanceFill")
    val floor = min?.let { (it / top).toFloat().coerceIn(0f, 1f) }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val tick = MaterialTheme.colorScheme.onSurfaceVariant

    // Drawn rather than laid out: the card sizes itself by intrinsic height,
    // which BoxWithConstraints (a SubcomposeLayout) can't answer.
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        val barHeight = 4.dp.toPx()
        val top = (size.height - barHeight) / 2
        val radius = CornerRadius(barHeight / 2)
        drawRoundRect(track, topLeft = Offset(0f, top), size = Size(size.width, barHeight), cornerRadius = radius)
        if (fill > 0f) {
            drawRoundRect(color, topLeft = Offset(0f, top), size = Size(size.width * fill, barHeight), cornerRadius = radius)
        }
        if (floor != null) {
            val tickWidth = 2.dp.toPx()
            val x = (size.width * floor - tickWidth / 2).coerceIn(0f, size.width - tickWidth)
            drawRoundRect(tick, topLeft = Offset(x, 0f), size = Size(tickWidth, size.height), cornerRadius = CornerRadius(tickWidth / 2))
        }
    }
}

/**
 * "KOTAK ₹13,730 left · CC-AXIS ₹12,840 used this month" for the save snackbar,
 * with a ⚠ on anything under its minimum or over its budget.
 */
fun balanceSummaryLine(
    changes: List<com.example.dailytrack_mobile.data.remote.dto.BalanceChangeDto>,
    cards: List<com.example.dailytrack_mobile.data.remote.dto.CardChangeDto> = emptyList()
): String? {
    val parts = changes.map { c ->
        val base = "${c.account} ₹${formatRupees(c.after)} left"
        if (c.belowMin && c.minBalance != null) "$base ⚠ under ₹${formatRupees(c.minBalance)}" else base
    } + cards.filter { it.after != it.before }.map { c ->
        val base = "${c.account} ₹${formatRupees(c.after)} used this month"
        if (c.overBudget && c.monthlyBudget != null) "$base ⚠ over ₹${formatRupees(c.monthlyBudget)}" else base
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}
