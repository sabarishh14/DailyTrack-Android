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

// ─────────────────────────────────────────────────────────────────────────────
// What an entry does to its account: "₹14,230 → ₹13,730", measured against the
// minimum the user wants to keep there. Mirrors the web's BalanceImpact.
// ─────────────────────────────────────────────────────────────────────────────

data class BalanceProjection(val account: String, val before: Double, val after: Double, val min: Double?)

enum class BalanceLevel { OK, WARN, DANGER }

/** Below the floor is DANGER, within half the floor above it WARN. Without a floor only overdraft warns. */
fun balanceLevel(after: Double, min: Double?): BalanceLevel = when {
    min != null && after < min -> BalanceLevel.DANGER
    min != null && after < min * 1.5 -> BalanceLevel.WARN
    min == null && after < 0 -> BalanceLevel.DANGER
    else -> BalanceLevel.OK
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

/** "KOTAK ₹13,730 left · IDBI ₹1,800 left (under ₹2,000)" for the save snackbar. */
fun balanceSummaryLine(changes: List<com.example.dailytrack_mobile.data.remote.dto.BalanceChangeDto>): String? {
    if (changes.isEmpty()) return null
    return changes.joinToString("  ·  ") { c ->
        val base = "${c.account} ₹${formatRupees(c.after)} left"
        if (c.belowMin && c.minBalance != null) "$base ⚠ under ₹${formatRupees(c.minBalance)}" else base
    }
}
