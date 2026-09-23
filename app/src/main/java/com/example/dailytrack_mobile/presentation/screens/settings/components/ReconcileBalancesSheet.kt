package com.example.dailytrack_mobile.presentation.screens.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Money
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.presentation.components.rememberSheetHeight
import com.example.dailytrack_mobile.presentation.screens.money.AccountInfo
import com.example.dailytrack_mobile.presentation.screens.settings.SyncTaskState
import com.example.dailytrack_mobile.presentation.util.Dimens
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// Reconcile Balances Sheet — compares each tracked account's ledger balance
// against the real balance last scanned from UPI screenshots in Drive, and
// flags whether money needs to move in or out to bring them back in sync.
// ─────────────────────────────────────────────────────────────────────────────

private enum class ReconcileStatus { MATCHED, INCREASE, REDUCE, NOT_SCANNED }

private fun statusFor(tracked: Double, real: Double?): ReconcileStatus {
    if (real == null) return ReconcileStatus.NOT_SCANNED
    val diff = tracked - real
    return when {
        abs(diff) < 0.01 -> ReconcileStatus.MATCHED
        diff < 0 -> ReconcileStatus.REDUCE
        else -> ReconcileStatus.INCREASE
    }
}

private val reconcilePalette = listOf(
    Color(0xFF6C63FF), Color(0xFF00BFA6), Color(0xFFFF6B6B),
    Color(0xFFFFA94D), Color(0xFF4DABF7), Color(0xFFB197FC)
)

private fun accentFor(account: String): Color =
    reconcilePalette[abs(account.hashCode()) % reconcilePalette.size]

private fun iconFor(account: String): ImageVector = when {
    account.contains("Cash", ignoreCase = true) -> Icons.Default.Money
    account.contains("CC", ignoreCase = true) || account.contains("Credit", ignoreCase = true) -> Icons.Default.CreditCard
    else -> Icons.Default.AccountBalance
}

private fun formatAmount(amount: Double): String {
    val prefix = if (amount < 0) "-" else ""
    return "$prefix₹%,.2f".format(abs(amount))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReconcileBalancesSheet(
    accounts: List<AccountInfo>,
    isLoadingAccounts: Boolean,
    scanTask: SyncTaskState,
    onScanClicked: () -> Unit,
    onDismiss: () -> Unit
) {
    val dims = Dimens.current
    val haptic = LocalHapticFeedback.current
    val trackedAccounts = remember(accounts) { accounts.filter { it.balanceTracked } }
    val matchedCount = remember(trackedAccounts) {
        trackedAccounts.count { statusFor(it.balance, it.realBalance) == ReconcileStatus.MATCHED }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(rememberSheetHeight(0.85f))
                .padding(
                    start = dims.screenHorizontalPadding,
                    end = dims.screenHorizontalPadding,
                    bottom = dims.screenBottomPadding + 16.dp
                )
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Reconcile Balances",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (trackedAccounts.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "$matchedCount/${trackedAccounts.size} matched",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tracked ledger vs. the balance last read from your UPI screenshots.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(dims.itemSpacingMedium))

            // Scan action
            val scanSubtitleColor = when {
                scanTask.isRunning -> MaterialTheme.colorScheme.primary
                scanTask.isSuccess == true -> Color(0xFF2ECC71)
                scanTask.isSuccess == false -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(
                onClick = {
                    if (!scanTask.isRunning) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onScanClicked()
                    }
                },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (scanTask.isRunning) {
                                LoadingIndicator(modifier = Modifier.size(18.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Scan Screenshots",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = scanTask.message ?: "Read balances from UPI screenshots in Drive",
                                style = MaterialTheme.typography.labelSmall,
                                color = scanSubtitleColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(dims.itemSpacingMedium))
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(dims.itemSpacingMedium))

            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoadingAccounts && trackedAccounts.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                    trackedAccounts.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "No tracked accounts yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(trackedAccounts, key = { it.account }) { account ->
                                ReconcileAccountRow(account)
                            }
                            item {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Scan pulls the latest UPI screenshots from Drive and updates each account's real balance.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReconcileAccountRow(account: AccountInfo) {
    val status = statusFor(account.balance, account.realBalance)
    val accent = accentFor(account.account)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconFor(account.account),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = account.account,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formatAmount(account.balance),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (account.realBalance != null) {
                            Text(
                                text = "→",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = formatAmount(account.realBalance),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            ReconcileStatusChip(status = status, diff = abs(account.balance - (account.realBalance ?: account.balance)))
        }
    }
}

@Composable
private fun ReconcileStatusChip(status: ReconcileStatus, diff: Double) {
    val (label, color, icon) = when (status) {
        ReconcileStatus.MATCHED -> Triple("Matched", Color(0xFF2ECC71), Icons.Default.CheckCircle)
        ReconcileStatus.INCREASE -> Triple("Add ${formatAmount(diff)}", Color(0xFF4DABF7), Icons.AutoMirrored.Filled.TrendingUp)
        ReconcileStatus.REDUCE -> Triple("Remove ${formatAmount(diff)}", MaterialTheme.colorScheme.error, Icons.AutoMirrored.Filled.TrendingDown)
        ReconcileStatus.NOT_SCANNED -> Triple("Not scanned", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.HourglassEmpty)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp
                ),
                color = color
            )
        }
    }
}
