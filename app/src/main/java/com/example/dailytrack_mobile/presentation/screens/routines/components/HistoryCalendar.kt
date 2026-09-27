package com.example.dailytrack_mobile.presentation.screens.routines.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.dailytrack_mobile.domain.routines.DayStats
import com.example.dailytrack_mobile.presentation.screens.routines.RoutineText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/**
 * A month of days, each ringed by how it went: green done, yellow skipped, red
 * missed, and a soft green fill for a perfect day. Any day that can be opened is
 * tappable, including blank ones from before a routine was added.
 */
@Composable
internal fun HistoryCalendar(
    month: YearMonth,
    days: List<DayStats?>,
    today: LocalDate,
    firstDay: LocalDate?,
    onMonth: (YearMonth) -> Unit,
    onDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val canGoBack = firstDay != null && month > YearMonth.from(firstDay)
    val canGoForward = month < YearMonth.from(today)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }, enabled = canGoBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                text = month.format(MonthTitle),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = canGoForward) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        Row(modifier = Modifier.padding(bottom = 4.dp)) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    text = RoutineText.narrowDay(day),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
        val cells: List<Int?> = List(leadingBlanks) { null } + (1..month.lengthOfMonth())
        cells.chunked(7).forEach { week ->
            Row {
                week.forEach { dayOfMonth ->
                    if (dayOfMonth == null) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(dayOfMonth)
                        DayCell(
                            date = date,
                            stats = days.getOrNull(dayOfMonth - 1),
                            today = today,
                            onClick = { onDay(date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
        Text(
            text = "Tap a day to see or change it",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    stats: DayStats?,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val mix = stats?.mix(today)
    val isToday = date == today
    val track = colors.surfaceContainerHighest
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (mix?.perfect == true) DayDoneColor.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(enabled = stats != null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (mix != null && mix.all > 0) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
            ) {
                drawMixRing(mix, track, strokeWidth = 3.dp.toPx())
            }
        }
        Text(
            text = "${date.dayOfMonth}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isToday) FontWeight.Black else FontWeight.Medium),
            color = when {
                stats == null -> colors.onSurfaceVariant.copy(alpha = 0.35f)
                isToday -> colors.primary
                else -> colors.onSurface
            }
        )
    }
}
