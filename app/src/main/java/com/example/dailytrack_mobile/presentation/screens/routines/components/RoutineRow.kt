package com.example.dailytrack_mobile.presentation.screens.routines.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import com.example.dailytrack_mobile.domain.routines.DayItem
import com.example.dailytrack_mobile.domain.routines.Streak
import com.example.dailytrack_mobile.presentation.screens.routines.RoutineText

/** A heart for done, a tear for missed; skipped stays neutral. */
internal val DoneColor = Color(0xFFFF4D6D)
internal val MissedColor = Color(0xFF5B8DEF)

internal const val DEFAULT_EMOJI = "✅"

@Composable
internal fun EmojiBadge(emoji: String?, status: CheckInStatus?, size: Dp = 44.dp) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = when (status) {
            CheckInStatus.DONE -> DoneColor.copy(alpha = 0.18f)
            CheckInStatus.MISSED -> MissedColor.copy(alpha = 0.16f)
            CheckInStatus.SKIPPED -> colors.surfaceContainerHighest
            null -> colors.surfaceContainerHigh
        },
        label = "emojiBadge"
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji?.takeIf { it.isNotBlank() } ?: DEFAULT_EMOJI, fontSize = (size.value * 0.46f).sp)
    }
}

/** One routine on a day's list, answered with the ❤️ / 😭 / ⏭️ buttons on the right. */
@Composable
internal fun RoutineRow(
    item: DayItem,
    streak: Streak?,
    onStatus: (CheckInStatus?) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceContainer)
            .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EmojiBadge(item.routine.emoji, item.status)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.routine.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val line = RoutineText.itemLine(item, streak)
            if (line.isNotEmpty()) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.status == CheckInStatus.SKIPPED && !item.note.isNullOrBlank()) {
                Text(
                    text = "Skipped · ${item.note}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        StatusChoices(status = item.status, onStatus = onStatus, onSkip = onSkip)
    }
}

/** Tapping the chosen answer again clears it. Skip asks for a reason first. */
@Composable
internal fun StatusChoices(
    status: CheckInStatus?,
    onStatus: (CheckInStatus?) -> Unit,
    onSkip: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        StatusChoice("❤️", "Done", status == CheckInStatus.DONE, status != null, DoneColor) {
            onStatus(if (status == CheckInStatus.DONE) null else CheckInStatus.DONE)
        }
        StatusChoice("😭", "Missed", status == CheckInStatus.MISSED, status != null, MissedColor) {
            onStatus(if (status == CheckInStatus.MISSED) null else CheckInStatus.MISSED)
        }
        StatusChoice(
            "⏭️", "Skip", status == CheckInStatus.SKIPPED, status != null,
            MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            if (status == CheckInStatus.SKIPPED) onStatus(null) else onSkip()
        }
    }
}

@Composable
private fun StatusChoice(
    emoji: String,
    label: String,
    selected: Boolean,
    answered: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val background by animateColorAsState(
        targetValue = if (selected) tint.copy(alpha = 0.2f) else Color.Transparent,
        label = "choiceBackground"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "choiceScale"
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background)
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 19.sp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = when {
                    selected -> 1f
                    answered -> 0.3f
                    else -> 0.6f
                }
            }
        )
    }
}

@Composable
internal fun RoutineSection(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
    }
}
