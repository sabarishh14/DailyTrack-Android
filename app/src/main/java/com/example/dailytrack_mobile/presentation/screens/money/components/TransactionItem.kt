package com.example.dailytrack_mobile.presentation.screens.money.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.presentation.screens.money.ChartColors
import com.example.dailytrack_mobile.presentation.screens.money.Transaction
import com.example.dailytrack_mobile.presentation.screens.money.TransactionType
import com.example.dailytrack_mobile.presentation.util.Dimens
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────
private fun formatAmount(transaction: Transaction): String {
    val prefix = if (transaction.type == TransactionType.CREDIT) "+" else "-"
    val abs = Math.abs(transaction.amount)
    return when {
        abs >= 1_00_000 -> "${prefix}₹%.0fL".format(abs / 1_00_000)
        abs >= 1_000 && abs % 1_000 == 0.0 -> "${prefix}₹%,.0f".format(abs)
        else -> "${prefix}₹%,.0f".format(abs)
    }
}

private val categoryEmojiColors: Map<String, Color> = mapOf(
    "Food"          to Color(0xFFF5A623),
    "Bills"         to Color(0xFF4A90D9),
    "Shopping"      to Color(0xFF9B59B6),
    "Transport"     to Color(0xFF1ABC9C),
    "Health"        to Color(0xFFE91E63),
    "Entertainment" to Color(0xFFFF7043),
    "Income"        to Color(0xFF2ECC71),
    "Cinema"        to Color(0xFFE040FB),
    "Daily Need"    to Color(0xFF8D6E63),
    "Education"     to Color(0xFF42A5F5),
    "Investment"    to Color(0xFF66BB6A),
    "Salary"        to Color(0xFF26A69A),
)

// ─────────────────────────────────────────────────────────────────────────────
// Swipeable Transaction Item
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SwipeableTransactionItem(
    transaction: Transaction,
    isSwiped: Boolean,
    onSwipeStateChanged: (Boolean) -> Unit,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    swipeEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current
    val circleSize = 44.dp
    val buttonSpacing = 10.dp
    val cardGap = 12.dp
    val totalRevealWidth = (circleSize * 2) + buttonSpacing + cardGap

    val density = LocalDensity.current
    val maxRevealPx = remember(density, totalRevealWidth) {
        with(density) { totalRevealWidth.toPx() }
    }

    val scope = rememberCoroutineScope()
    val offsetX = remember(transaction.id) { Animatable(0f) }

    // Respond to external swipe state (auto-close when another item is swiped or in selection mode)
    LaunchedEffect(isSwiped, isSelectionMode) {
        if ((!isSwiped || isSelectionMode) && offsetX.value != 0f) {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        // ── Revealed Action Buttons (Delete + Edit Circles) - disabled during selection mode ──
        if (!isSelectionMode) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val progress = (-offsetX.value / maxRevealPx).coerceIn(0f, 1f)
                        alpha = progress
                    },
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete button circle (Light red background, red icon)
                Box(
                    modifier = Modifier
                        .size(circleSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                        .clickable {
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                                onSwipeStateChanged(false)
                            }
                            onDelete()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Transaction",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(dims.iconSizeMedium)
                    )
                }

                Spacer(modifier = Modifier.width(buttonSpacing))

                // Edit button circle (Theme color background, theme color icon)
                Box(
                    modifier = Modifier
                        .size(circleSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        .clickable {
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                                onSwipeStateChanged(false)
                            }
                            onEdit()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Transaction",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(dims.iconSizeMedium - 2.dp)
                    )
                }
            }
        }

        // ── Foreground Transaction Card ──────────────────────────────────────
        val dragModifier = if (!isSelectionMode && swipeEnabled) {
            Modifier.pointerInput(transaction.id) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        onSwipeStateChanged(true)
                    },
                    onDragEnd = {
                        scope.launch {
                            val shouldOpen = offsetX.value < -maxRevealPx * 0.35f
                            val target = if (shouldOpen) -maxRevealPx else 0f
                            offsetX.animateTo(
                                targetValue = target,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            onSwipeStateChanged(shouldOpen)
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            onSwipeStateChanged(false)
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            val newOffset = (offsetX.value + dragAmount).coerceIn(-maxRevealPx, 0f)
                            offsetX.snapTo(newOffset)
                        }
                    }
                )
            }
        } else Modifier

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .then(dragModifier)
                .pointerInput(transaction.id, isSelectionMode) {
                    detectTapGestures(
                        onLongPress = {
                            onLongClick()
                        },
                        onTap = {
                            if (!isSelectionMode && offsetX.value < -10f) {
                                scope.launch {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                    onSwipeStateChanged(false)
                                }
                            } else {
                                onClick()
                            }
                        }
                    )
                }
        ) {
            TransactionCardSurface(
                transaction = transaction,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Transaction Card Surface
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TransactionCardSurface(
    transaction: Transaction,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current
    val emojiBgColor = categoryEmojiColors[transaction.category]
        ?: MaterialTheme.colorScheme.surfaceContainerHighest

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "tx_card_container"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else if (isSelectionMode) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        else Color.Transparent,
        label = "tx_card_border"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dims.cardCornerRadius - 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingLarge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection Checkbox Circle (smooth animated expansion without layout snapping)
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = fadeIn(animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing)) +
                        expandHorizontally(
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Start
                        ),
                exit = fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)) +
                       shrinkHorizontally(
                           animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                           shrinkTowards = Alignment.Start
                       )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = dims.itemSpacingLarge)
                ) {
                    val checkboxBg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                        animationSpec = tween(durationMillis = 150),
                        label = "checkbox_bg"
                    )
                    val checkboxBorder by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        animationSpec = tween(durationMillis = 150),
                        label = "checkbox_border"
                    )

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(checkboxBg)
                            .border(
                                width = 2.dp,
                                color = checkboxBorder,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isSelected,
                            enter = androidx.compose.animation.scaleIn(tween(150)) + fadeIn(tween(150)),
                            exit = androidx.compose.animation.scaleOut(tween(120)) + fadeOut(tween(120))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // Emoji icon in a coloured circle
            Box(
                modifier = Modifier
                    .size(dims.iconSizeXLarge)
                    .clip(CircleShape)
                    .background(emojiBgColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = transaction.emoji,
                    fontSize = dims.fontSizeTitleLarge
                )
            }

            Spacer(modifier = Modifier.width(dims.itemSpacingLarge))

            // Title + Description + Date/Bank/Excluded
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = transaction.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Show description (category) as subtitle when it differs from title
                if (!transaction.description.isNullOrBlank()) {
                    Text(
                        text = transaction.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transaction.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = transaction.bank,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (transaction.isExcluded) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Excluded",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 9.sp
                                ),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(dims.itemSpacingMedium))

            // Amount
            Text(
                text = formatAmount(transaction),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = if (transaction.type == TransactionType.CREDIT) ChartColors.IncomeGreen
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
