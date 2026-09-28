package com.example.dailytrack_mobile.presentation.screens.routines

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.presentation.screens.routines.components.ConsistencyRing
import com.example.dailytrack_mobile.presentation.screens.routines.components.DayDoneColor
import com.example.dailytrack_mobile.presentation.screens.routines.components.DayMissedColor
import com.example.dailytrack_mobile.presentation.util.Dimens

/**
 * Routines at a glance on Home: the consistency ring leads, with today's count and
 * the perfect-day streak under it. Shares the Routines page's view model, so nothing loads twice.
 */
@Composable
fun RoutinesHomeCard(
    onClick: () -> Unit,
    viewModel: RoutinesVM = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val dims = Dimens.current
    val colors = MaterialTheme.colorScheme
    val stats = state.todayStats
    val consistency = state.consistency
    val streak = state.perfectDays?.current ?: 0
    val streakPart = if (streak >= 2) " · 🔥 $streak" else ""
    val trend = RoutineText.trendPoints(consistency, state.previousConsistency)

    val title = when {
        state.isLoading || state.routines.isEmpty() || consistency?.fraction == null -> "Routines"
        else -> "${RoutineText.percent(consistency.fraction)} consistent"
    }
    val subtitle = when {
        state.isLoading -> "Loading…"
        state.routines.isEmpty() -> "Build a habit, take on a challenge"
        stats == null || stats.total == 0 -> "Nothing due today$streakPart"
        stats.done == stats.total -> "All done today 🎉$streakPart"
        else -> "${stats.done} of ${stats.total} done today$streakPart"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dims.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dims.cardInnerPadding, vertical = dims.cardInnerPadding * 0.6f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConsistencyRing(
                score = consistency,
                diameter = 48.dp,
                strokeWidth = 5.dp,
                track = colors.surfaceContainerHighest
            ) {
                if (state.routines.isEmpty()) {
                    Text("🌱", fontSize = 20.sp)
                } else {
                    Text(
                        text = RoutineText.percent(consistency?.fraction),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
                        color = colors.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.width(dims.itemSpacingLarge))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trend != null && trend != 0) {
                Text(
                    text = if (trend > 0) "▲ $trend%" else "▼ ${-trend}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                    color = if (trend > 0) DayDoneColor else DayMissedColor,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open Routines",
                tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(dims.iconSizeSmall)
            )
        }
    }
}
