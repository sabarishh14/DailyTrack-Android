package com.example.dailytrack_mobile.presentation.screens.routines

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.dailytrack_mobile.data.local.routines.RoutineCheckInSettings
import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import com.example.dailytrack_mobile.domain.routines.DayStats
import com.example.dailytrack_mobile.domain.routines.RoutineSchedule
import com.example.dailytrack_mobile.domain.routines.Score
import com.example.dailytrack_mobile.domain.routines.Streak
import com.example.dailytrack_mobile.domain.routines.UpcomingItem
import com.example.dailytrack_mobile.presentation.components.DailyTrackPullToRefreshBox
import com.example.dailytrack_mobile.presentation.components.LocalFloatingBarClearance
import com.example.dailytrack_mobile.presentation.screens.routines.components.ChartLegend
import com.example.dailytrack_mobile.presentation.screens.routines.components.CheckInSettingsSheet
import com.example.dailytrack_mobile.presentation.screens.routines.components.ConsistencyRing
import com.example.dailytrack_mobile.presentation.screens.routines.components.DaySheet
import com.example.dailytrack_mobile.presentation.screens.routines.components.DayDoneColor
import com.example.dailytrack_mobile.presentation.screens.routines.components.DayMissedColor
import com.example.dailytrack_mobile.presentation.screens.routines.components.DaySkippedColor
import com.example.dailytrack_mobile.presentation.screens.routines.components.EmojiBadge
import com.example.dailytrack_mobile.presentation.screens.routines.components.HistoryCalendar
import com.example.dailytrack_mobile.presentation.screens.routines.components.RoutineRow
import com.example.dailytrack_mobile.presentation.screens.routines.components.RoutineSection
import com.example.dailytrack_mobile.presentation.screens.routines.components.SkipReasonSheet
import com.example.dailytrack_mobile.presentation.screens.routines.components.drawMixRing
import com.example.dailytrack_mobile.presentation.screens.routines.components.mix
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// ─────────────────────────────────────────────────────────────────────────────
// Routines: today's ring and the 30-day consistency score up top, then today's
// list to tick off, what's coming up, the week at a glance and every routine.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RoutinesScreen(
    onNewRoutine: (RoutineTemplate?) -> Unit,
    onEditRoutine: (Long) -> Unit,
    /** Whether this is the page on screen; a routine's page only shows over this one. */
    isCurrentPage: Boolean = true,
    viewModel: RoutinesVM = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val onAction = viewModel::onAction
    val dims = Dimens.current
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onAction(RoutinesAction.Resume) }

    LaunchedEffect(state.message) {
        state.message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            onAction(RoutinesAction.ConsumeMessage)
        }
    }

    DailyTrackPullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onAction(RoutinesAction.Refresh) },
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.readOnly && state.routines.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Notice(state.error ?: "No routines yet")
            }
            state.routines.isEmpty() -> EmptyRoutines(
                error = state.error,
                archived = state.archived.map { it.id to listOfNotNull(it.emoji, it.name).joinToString(" ") },
                onTemplate = { onNewRoutine(it) },
                onCreate = { onNewRoutine(null) },
                onRetry = { onAction(RoutinesAction.Refresh) },
                onEditArchived = onEditRoutine
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = dims.screenHorizontalPadding,
                    end = dims.screenHorizontalPadding,
                    top = dims.itemSpacingMedium,
                    bottom = dims.screenBottomPadding + LocalFloatingBarClearance.current
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // First thing on the page: days never answered, until they are.
                if (state.unfilledDays.isNotEmpty() && !state.readOnly) {
                    item(key = "fill-in") {
                        FillInBanner(days = state.unfilledDays, today = state.today) {
                            onAction(RoutinesAction.OpenDay(state.unfilledDays.first()))
                        }
                    }
                }
                item(key = "hero") {
                    RoutinesHero(
                        today = state.todayStats,
                        todayLeft = state.todayItems.count { it.needsAnswer },
                        consistency = state.consistency,
                        previous = state.previousConsistency,
                        perfectDays = state.perfectDays,
                        checkIn = state.checkIn,
                        pendingSync = state.pendingSync,
                        onCheckIn = { onAction(RoutinesAction.OpenCheckInSettings) }
                    )
                }
                state.error?.let { error ->
                    item(key = "error") { Notice("Showing what's saved on this phone. $error") }
                }

                item(key = "today-title") { RoutineSection("Today", RoutineText.weekdayDate(state.today)) }
                if (state.todayItems.isEmpty()) {
                    item(key = "today-empty") { Notice("Nothing due today. Enjoy it 🌿") }
                }
                items(state.todayItems, key = { "today-${it.routine.id}" }) { item ->
                    RoutineRow(
                        item = item,
                        streak = state.streaks[item.routine.id],
                        onStatus = { onAction(RoutinesAction.SetStatus(item.routine.id, item.date, it)) },
                        onSkip = { onAction(RoutinesAction.AskSkipReason(item)) },
                        onOpen = { onAction(RoutinesAction.OpenRoutine(item.routine.id)) }
                    )
                }

                if (state.upcoming.isNotEmpty()) {
                    item(key = "upcoming-title") { RoutineSection("Coming up") }
                    items(state.upcoming, key = { "upcoming-${it.routine.id}" }) { upcoming ->
                        UpcomingRow(upcoming, state.today) {
                            onAction(RoutinesAction.SetStatus(upcoming.routine.id, state.today, CheckInStatus.DONE))
                        }
                    }
                }

                item(key = "week-title") {
                    val week = state.week.filterNotNull()
                    val done = week.sumOf { it.done }
                    val total = week.sumOf { it.total }
                    RoutineSection("This week", if (total > 0) "avg ${RoutineText.percent(done.toDouble() / total)}" else null)
                }
                item(key = "week") {
                    Column {
                        WeekCard(
                            week = state.week,
                            today = state.today,
                            historyOpen = state.historyOpen,
                            onDay = { onAction(RoutinesAction.OpenDay(it)) },
                            onToggleHistory = { onAction(RoutinesAction.ToggleHistory) }
                        )
                        AnimatedVisibility(
                            visible = state.historyOpen,
                            enter = fadeIn(tween(180)) + expandVertically(tween(260, easing = FastOutSlowInEasing)),
                            exit = fadeOut(tween(120)) + shrinkVertically(tween(200))
                        ) {
                            HistoryCalendar(
                                month = state.historyMonth,
                                days = state.history,
                                today = state.today,
                                firstDay = state.historyFirstDay,
                                onMonth = { onAction(RoutinesAction.ShowMonth(it)) },
                                onDay = { onAction(RoutinesAction.OpenDay(it)) },
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                item(key = "routines-title") { RoutineSection("Your routines", "${state.routines.size}") }
                items(state.routines, key = { "routine-${it.routine.id}" }) { summary ->
                    RoutineSummaryRow(summary, state.today) { onAction(RoutinesAction.OpenRoutine(summary.routine.id)) }
                }
                if (!state.readOnly) item(key = "new") {
                    OutlinedButton(
                        onClick = { onNewRoutine(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New routine")
                    }
                }
                if (state.archived.isNotEmpty() && !state.readOnly) {
                    item(key = "archived") {
                        ArchivedList(state.archived.map { it.id to listOfNotNull(it.emoji, it.name).joinToString(" ") }, onEditRoutine)
                    }
                }
            }
        }
    }

    state.openDay?.let { day ->
        DaySheet(
            date = day,
            today = state.today,
            items = state.openDayItems,
            stats = state.openDayStats,
            streaks = state.streaks,
            // Filling in: step straight on to the next day still blank.
            next = if (state.readOnly) null else state.unfilledDays.firstOrNull { it != day },
            onNext = { onAction(RoutinesAction.OpenDay(it)) },
            onStatus = { item, status -> onAction(RoutinesAction.SetStatus(item.routine.id, item.date, status)) },
            onSkip = { onAction(RoutinesAction.AskSkipReason(it)) },
            onDismiss = { onAction(RoutinesAction.CloseDay) }
        )
    }
    val detail = state.detail?.takeIf { isCurrentPage }
    if (detail != null) {
        RoutineDetailPage(
            detail = detail,
            today = state.today,
            reminder = state.reminders[detail.routine.id],
            skipping = state.skipping,
            onAction = onAction,
            onEdit = if (state.readOnly) null else ({ onEditRoutine(detail.routine.id) })
        )
    }
    // A routine's page shows its own skip sheet, on top of itself.
    state.skipping?.takeIf { detail == null }?.let { item ->
        SkipReasonSheet(
            item = item,
            onSkip = { reason ->
                onAction(RoutinesAction.SetStatus(item.routine.id, item.date, CheckInStatus.SKIPPED, reason))
            },
            onDismiss = { onAction(RoutinesAction.DismissSkip) }
        )
    }
    if (state.showCheckInSettings) {
        CheckInSettingsSheet(
            settings = state.checkIn,
            onEnabled = { onAction(RoutinesAction.SetCheckInEnabled(it)) },
            onTime = { onAction(RoutinesAction.SetCheckInTime(it)) },
            onToggleDay = { onAction(RoutinesAction.ToggleCheckInDay(it)) },
            onAlarm = { onAction(RoutinesAction.SetCheckInAlarm(it)) },
            onTry = { onAction(RoutinesAction.TryCheckIn) },
            onDismiss = { onAction(RoutinesAction.CloseCheckInSettings) }
        )
    }
}

// ── Hero ─────────────────────────────────────────────────────────────────────

/** Consistency leads: the ring, a word for it and how it's moving. Today sits right under it. */
@Composable
private fun RoutinesHero(
    today: DayStats?,
    todayLeft: Int,
    consistency: Score?,
    previous: Score?,
    perfectDays: Streak?,
    checkIn: RoutineCheckInSettings.Settings,
    pendingSync: Int,
    onCheckIn: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val onHero = colors.onPrimaryContainer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer)))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConsistencyRing(
                score = consistency,
                diameter = 124.dp,
                strokeWidth = 12.dp,
                track = onHero.copy(alpha = 0.14f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = RoutineText.percent(consistency?.fraction),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
                        color = onHero
                    )
                    Text(
                        text = "30 days",
                        style = MaterialTheme.typography.labelSmall,
                        color = onHero.copy(alpha = 0.75f)
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CONSISTENCY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = onHero.copy(alpha = 0.7f)
                )
                Text(
                    text = RoutineText.verdict(consistency?.fraction),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = onHero,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (consistency?.fraction == null) "shows up after your first day" else "over the last 30 days",
                    style = MaterialTheme.typography.bodySmall,
                    color = onHero.copy(alpha = 0.75f)
                )
                RoutineText.trendPoints(consistency, previous)?.let { points ->
                    Spacer(Modifier.height(6.dp))
                    TrendLine(points, onHero)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = streakLine(perfectDays),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = onHero,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.surface.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        TodayStrip(today, todayLeft)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface.copy(alpha = 0.45f))
                .clickable(onClick = onCheckIn)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🌙", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nightly check-in",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = onHero
                )
                Text(
                    text = if (checkIn.enabled && checkIn.days.isNotEmpty()) {
                        "${RoutineText.time(checkIn.time)} · ${checkInDays(checkIn.days)}"
                    } else {
                        "Off · tap to turn on"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = onHero.copy(alpha = 0.75f)
                )
                if (pendingSync > 0) {
                    Text(
                        text = "☁️ ${if (pendingSync == 1) "1 answer" else "$pendingSync answers"} waiting to sync",
                        style = MaterialTheme.typography.labelSmall,
                        color = onHero.copy(alpha = 0.75f)
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Check-in settings",
                tint = onHero.copy(alpha = 0.7f)
            )
        }
    }
}

/** "▲ 6% vs previous 30 days", with the arrow in green or red. */
@Composable
private fun TrendLine(points: Int, color: Color) {
    val (arrow, arrowColor) = when {
        points > 0 -> "▲ $points%" to DayDoneColor
        points < 0 -> "▼ ${-points}%" to DayMissedColor
        else -> "=" to color
    }
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = arrowColor, fontWeight = FontWeight.Black)) { append(arrow) }
            append(if (points == 0) " same as previous 30 days" else " vs previous 30 days")
        },
        style = MaterialTheme.typography.labelMedium,
        color = color.copy(alpha = 0.85f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Today so far, under the score: a small ring, what's left, and done out of due. */
@Composable
private fun TodayStrip(stats: DayStats?, left: Int) {
    val colors = MaterialTheme.colorScheme
    val onHero = colors.onPrimaryContainer
    val hasDue = stats != null && stats.total > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            if (stats != null && hasDue) {
                val track = onHero.copy(alpha = 0.14f)
                val mix = stats.mix()
                Canvas(modifier = Modifier.fillMaxSize()) { drawMixRing(mix, track, strokeWidth = 4.dp.toPx()) }
            } else {
                Text("☀️", fontSize = 16.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Today",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = onHero
            )
            Text(
                text = when {
                    left > 0 -> "$left left to answer"
                    !hasDue -> "Nothing due today"
                    stats?.missed == 0 -> "All done 🎉"
                    else -> "All answered"
                },
                style = MaterialTheme.typography.labelSmall,
                color = onHero.copy(alpha = 0.75f)
            )
        }
        Text(
            text = if (stats != null && hasDue) "${stats.done}/${stats.total}" else "—",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
            color = onHero
        )
    }
}

private fun streakLine(streak: Streak?): String = when {
    streak == null || streak.best == 0 -> "🌱 Your first perfect day starts a streak"
    streak.current >= 2 -> "🔥 ${streak.current}-day streak · best ${streak.best}"
    streak.current == 1 -> "🔥 1 perfect day · best ${streak.best}"
    else -> "Best streak: ${RoutineText.streakLength(streak.best, streak.unit)}"
}

private fun checkInDays(days: Set<DayOfWeek>): String =
    if (days.size == 7) "every night" else RoutineText.weekdays(days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) })

// ── Sections ─────────────────────────────────────────────────────────────────

/**
 * Days never answered, newest first: "3 days to fill in · Yesterday, Sat 27,
 * Thu 25". Tapping opens the newest; its sheet steps on to the next.
 */
@Composable
private fun FillInBanner(days: List<LocalDate>, today: LocalDate, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🕰️", fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (days.size == 1) "${RoutineText.shortDate(days[0], today)} isn't filled in"
                else "${days.size} days to fill in",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSecondaryContainer
            )
            Text(
                text = if (days.size == 1) "Counts as missed until you do"
                else days.take(3).joinToString(", ") { RoutineText.shortDate(it, today) } +
                    if (days.size > 3) " +${days.size - 3}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSecondaryContainer.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "Fill in",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = colors.onSecondaryContainer
        )
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    )
}

@Composable
private fun UpcomingRow(item: UpcomingItem, today: LocalDate, onDoneEarly: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val routine = item.routine
    val isChore = routine.schedule == RoutineSchedule.INTERVAL
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceContainer)
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EmojiBadge(routine.emoji, status = null, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = routine.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isChore) {
                    "Due ${RoutineText.dueIn(item.date, today, sentenceStart = false)} · ${RoutineText.schedule(routine).lowercase()}"
                } else {
                    "Starts ${RoutineText.dueIn(item.date, today, sentenceStart = false)}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isChore) {
            TextButton(onClick = onDoneEarly) { Text("❤️ Done") }
        }
    }
}

/**
 * Monday to Sunday, like Digital Wellbeing: each day's bar stacks done, skipped
 * and missed, with what's still open today left as track. Tap a day to see or
 * fix it; History opens underneath for any earlier month.
 */
@Composable
private fun WeekCard(
    week: List<DayStats?>,
    today: LocalDate,
    historyOpen: Boolean,
    onDay: (LocalDate) -> Unit,
    onToggleHistory: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            DayOfWeek.entries.forEachIndexed { index, day ->
                val date = monday.plusDays(index.toLong())
                DayBar(
                    label = RoutineText.narrowDay(day),
                    stats = week.getOrNull(index),
                    today = today,
                    isToday = date == today,
                    onClick = { onDay(date) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChartLegend(modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onToggleHistory)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "History",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.primary
                )
                Icon(
                    imageVector = if (historyOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (historyOpen) "Hide history" else "Show history",
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DayBar(
    label: String,
    stats: DayStats?,
    today: LocalDate,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val mix = stats?.mix()
    val fraction = stats?.fraction
    // Grows up from the bottom when the week comes into view.
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(durationMillis = 600, easing = FastOutSlowInEasing)) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = stats != null, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when {
                mix?.perfect == true -> "✓"
                fraction == null -> ""
                else -> RoutineText.percent(fraction).removeSuffix("%")
            },
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (mix?.perfect == true) DayDoneColor else colors.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(colors.surfaceContainerHighest.copy(alpha = if (stats == null) 0.5f else 1f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (mix != null && mix.all > 0) {
                // Top to bottom: still open (the track), missed, skipped, done.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(grow.value),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    if (mix.open > 0) Spacer(Modifier.weight(mix.open.toFloat()))
                    if (mix.missed > 0) Segment(DayMissedColor, mix.missed)
                    if (mix.skipped > 0) Segment(DaySkippedColor, mix.skipped)
                    if (mix.done > 0) Segment(DayDoneColor, mix.done)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isToday) FontWeight.Black else FontWeight.Medium),
            color = if (isToday) colors.primary else colors.onSurfaceVariant
        )
    }
}

@Composable
private fun ColumnScope.Segment(color: Color, count: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(count.toFloat())
            .background(color)
    )
}

@Composable
private fun RoutineSummaryRow(summary: RoutineSummary, today: LocalDate, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val routine = summary.routine
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EmojiBadge(routine.emoji, status = null, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = routine.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = RoutineText.summary(routine, today, summary.nextDue),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = RoutineText.percent(summary.consistency.fraction),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            Text(
                text = summary.streak.takeIf { it.current > 0 }
                    ?.let { "🔥 ${RoutineText.streakLength(it.current, it.unit)}" }
                    ?: "30 days",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArchivedList(archived: List<Pair<Long, String>>, onEdit: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(if (expanded) "Hide archived" else "Show archived (${archived.size})")
        }
        if (expanded) {
            archived.forEach { (id, label) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onEdit(id) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .alpha(0.8f)
                )
            }
        }
    }
}

// ── First run ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyRoutines(
    error: String?,
    archived: List<Pair<Long, String>>,
    onTemplate: (RoutineTemplate) -> Unit,
    onCreate: () -> Unit,
    onRetry: () -> Unit,
    onEditArchived: (Long) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dims = Dimens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dims.screenHorizontalPadding + 8.dp)
            .padding(top = 28.dp, bottom = dims.screenBottomPadding + LocalFloatingBarClearance.current),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (error != null) {
            Notice("Couldn't reach the server. $error")
            TextButton(onClick = onRetry) { Text("Try again") }
            Spacer(Modifier.height(12.dp))
        }
        Text("🌱", fontSize = 56.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Build routines that stick",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Habits to build, things to quit, and challenges. Tick them off through the day, or answer a quick check-in every night.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "START WITH ONE OF THESE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = colors.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            RoutineTemplates.forEach { template ->
                Text(
                    text = "${template.emoji} ${template.name}",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh)
                        .clickable { onTemplate(template) }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCreate) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Create your own")
        }
        if (archived.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ArchivedList(archived, onEditArchived)
        }
    }
}
