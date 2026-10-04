package com.example.dailytrack_mobile.presentation.screens.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dailytrack_mobile.domain.routines.CheckIn
import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import com.example.dailytrack_mobile.domain.routines.DayItem
import com.example.dailytrack_mobile.domain.routines.PeriodUnit
import com.example.dailytrack_mobile.domain.routines.RoutineSchedule
import com.example.dailytrack_mobile.presentation.components.FullScreenPage
import com.example.dailytrack_mobile.presentation.components.navigationBarPadding
import com.example.dailytrack_mobile.presentation.screens.routines.components.DayDoneColor
import com.example.dailytrack_mobile.presentation.screens.routines.components.DayMissedColor
import com.example.dailytrack_mobile.presentation.screens.routines.components.DaySkippedColor
import com.example.dailytrack_mobile.presentation.screens.routines.components.EmojiBadge
import com.example.dailytrack_mobile.presentation.screens.routines.components.RoutineSection
import com.example.dailytrack_mobile.presentation.screens.routines.components.SkipReasonSheet
import com.example.dailytrack_mobile.presentation.screens.routines.components.StatusChoices
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/**
 * One routine on its own: streaks and scores, its calendar (tap any day to see
 * or change it), and why it was skipped. A full page, so scrolling can't close it.
 */
@Composable
internal fun RoutineDetailPage(
    detail: RoutineDetail,
    today: LocalDate,
    reminder: LocalTime?,
    skipping: DayItem?,
    onAction: (RoutinesAction) -> Unit,
    /** Null for someone else's routine: no editing. */
    onEdit: (() -> Unit)?
) {
    val dims = Dimens.current
    val routine = detail.routine
    FullScreenPage(onDismiss = { onAction(RoutinesAction.CloseRoutine) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = dims.screenHorizontalPadding, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onAction(RoutinesAction.CloseRoutine) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            EmojiBadge(routine.emoji, status = null, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = RoutineText.summary(routine, today, detail.nextDue?.takeIf { it > today }) +
                        (reminder?.let { " · ⏰ ${RoutineText.time(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            if (onEdit != null) FilledTonalButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Edit")
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = dims.screenHorizontalPadding,
                end = dims.screenHorizontalPadding,
                top = 12.dp,
                bottom = 24.dp + navigationBarPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "stats") { StatsGrid(detail) }
            scheduleNote(detail, today)?.let { note ->
                item(key = "note") { Note(note) }
            }

            item(key = "calendar-title") { RoutineSection("Calendar") }
            item(key = "calendar") {
                RoutineCalendar(
                    detail = detail,
                    today = today,
                    onMonth = { onAction(RoutinesAction.ShowRoutineMonth(it)) },
                    onDay = { onAction(RoutinesAction.SelectRoutineDay(it)) }
                )
            }
            detail.selected?.let { item ->
                item(key = "selected") {
                    SelectedDay(
                        item = item,
                        today = today,
                        onStatus = { onAction(RoutinesAction.SetStatus(item.routine.id, item.date, it)) },
                        onSkip = { onAction(RoutinesAction.AskSkipReason(item)) }
                    )
                }
            }

            if (detail.skips.isNotEmpty()) {
                item(key = "skips-title") { RoutineSection("Skips", "${detail.skips.size}") }
                items(detail.skips, key = { "skip-${it.date}" }) { skip -> SkipRow(skip, today) }
            }
        }

        // Inside the page, so the sheet opens on top of it.
        skipping?.let { item ->
            SkipReasonSheet(
                item = item,
                onSkip = { reason ->
                    onAction(RoutinesAction.SetStatus(item.routine.id, item.date, CheckInStatus.SKIPPED, reason))
                },
                onDismiss = { onAction(RoutinesAction.DismissSkip) }
            )
        }
    }
}

@Composable
private fun StatsGrid(detail: RoutineDetail) {
    val streakUnit = detail.streak.unit
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                emoji = "🔥",
                value = RoutineText.streakLength(detail.streak.current, streakUnit),
                label = "Current streak",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                emoji = "🏆",
                value = RoutineText.streakLength(detail.streak.best, streakUnit),
                label = "Best streak",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                emoji = "📅",
                value = RoutineText.percent(detail.last30.fraction),
                label = "Last 30 days",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                emoji = "⭐",
                value = RoutineText.percent(detail.allTime.fraction),
                label = "All time · ${detail.doneCount} done",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatTile(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = "$emoji $value",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A line for what the schedule means right now, when there's something to say. */
private fun scheduleNote(detail: RoutineDetail, today: LocalDate): String? {
    val routine = detail.routine
    return when (routine.schedule) {
        RoutineSchedule.WEEKLY, RoutineSchedule.MONTHLY -> detail.progress?.let {
            val period = if (it.unit == PeriodUnit.WEEK) "week" else "month"
            if (it.met) "Done for this $period: ${it.done} of ${it.needed} ✓" else "This $period: ${it.done} of ${it.needed}"
        }
        RoutineSchedule.INTERVAL -> {
            val due = detail.nextDue?.let { due ->
                when {
                    due < today -> "Overdue since ${RoutineText.weekdayDate(due)}"
                    due == today -> "Due today"
                    else -> "Next due ${RoutineText.weekdayDate(due)}"
                }
            }
            val last = detail.lastDone?.let { "last done ${RoutineText.weekdayDate(it)}" }
            listOfNotNull(due, last).joinToString(" · ").ifEmpty { null }
        }
        else -> null
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    )
}

/**
 * The routine's own month: filled green / yellow / red for done / skipped /
 * missed, an outline where a day was left blank, faint where it wasn't due.
 */
@Composable
private fun RoutineCalendar(
    detail: RoutineDetail,
    today: LocalDate,
    onMonth: (YearMonth) -> Unit,
    onDay: (LocalDate) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val month = detail.month
    val canGoBack = month > YearMonth.from(detail.routine.startDate)
    val canGoForward = month < YearMonth.from(today)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(8.dp)
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
                        RoutineDayCell(
                            date = date,
                            item = detail.days.getOrNull(dayOfMonth - 1),
                            today = today,
                            selected = detail.selected?.date == date,
                            onClick = { onDay(date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(DayDoneColor, "done")
            LegendDot(DaySkippedColor, "skipped")
            LegendDot(DayMissedColor, "missed")
            LegendDot(null, "left blank")
        }
    }
}

@Composable
private fun RoutineDayCell(
    date: LocalDate,
    item: DayItem?,
    today: LocalDate,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val status = item?.status
    val blankMiss = item != null && status == null && item.required && date < today &&
        item.routine.countsIfUnanswered(date)
    val fill = when (status) {
        CheckInStatus.DONE -> DayDoneColor
        CheckInStatus.SKIPPED -> DaySkippedColor
        CheckInStatus.MISSED -> DayMissedColor
        null -> if (item != null && !blankMiss && date != today) colors.surfaceContainerHigh else Color.Transparent
    }
    val outline = when {
        selected -> colors.primary
        blankMiss -> DayMissedColor
        item != null && date == today && status == null -> colors.primary.copy(alpha = 0.6f)
        else -> Color.Transparent
    }
    val textColor = when {
        item == null -> colors.onSurfaceVariant.copy(alpha = 0.35f)
        status == CheckInStatus.SKIPPED -> Color.Black
        status != null -> Color.White
        else -> colors.onSurface
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(CircleShape)
            .background(fill)
            .border(if (selected) 2.5.dp else 1.5.dp, outline, CircleShape)
            .clickable(enabled = item != null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${date.dayOfMonth}",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (date == today || selected) FontWeight.Black else FontWeight.Medium
            ),
            color = textColor
        )
    }
}

@Composable
private fun LegendDot(color: Color?, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color ?: Color.Transparent)
                .border(1.5.dp, color ?: DayMissedColor, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The day picked on the calendar, with the ❤️ / 😭 / ⏭️ buttons to change it. */
@Composable
private fun SelectedDay(
    item: DayItem,
    today: LocalDate,
    onStatus: (CheckInStatus?) -> Unit,
    onSkip: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceContainerHigh)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = RoutineText.longDate(item.date, today),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface
            )
            Text(
                text = when (item.status) {
                    CheckInStatus.DONE -> "Done"
                    CheckInStatus.MISSED -> "Missed"
                    CheckInStatus.SKIPPED -> item.note?.let { "Skipped · $it" } ?: "Skipped"
                    null -> if (item.date == today) "Not answered yet" else "Left blank"
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        StatusChoices(status = item.status, onStatus = onStatus, onSkip = onSkip)
    }
}

@Composable
private fun SkipRow(skip: CheckIn, today: LocalDate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(DaySkippedColor)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = RoutineText.longDate(skip.date, today),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = skip.note ?: "No reason given",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
