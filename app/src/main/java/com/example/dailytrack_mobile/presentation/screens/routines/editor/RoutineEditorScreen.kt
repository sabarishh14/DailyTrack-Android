package com.example.dailytrack_mobile.presentation.screens.routines.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.domain.routines.IntervalUnit
import com.example.dailytrack_mobile.domain.routines.RoutineKind
import com.example.dailytrack_mobile.domain.routines.RoutineSchedule
import com.example.dailytrack_mobile.domain.routines.bit
import com.example.dailytrack_mobile.presentation.components.SheetContentInsets
import com.example.dailytrack_mobile.presentation.components.rememberSheetHeight
import com.example.dailytrack_mobile.presentation.screens.routines.RoutineEditorTarget
import com.example.dailytrack_mobile.presentation.screens.routines.RoutineTemplates
import com.example.dailytrack_mobile.presentation.screens.routines.RoutineText
import com.example.dailytrack_mobile.presentation.screens.routines.components.DayPill
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** New or existing routine: name, type, how often, and whether it's a challenge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutineEditorScreen(
    target: RoutineEditorTarget,
    onDirtyStateChanged: (Boolean) -> Unit,
    onFinished: (message: String) -> Unit,
    viewModel: RoutineEditorVM = hiltViewModel()
) {
    // A new session per visit; kept across rotation so a half-filled form survives.
    val session = rememberSaveable(target) { UUID.randomUUID().toString() }
    LaunchedEffect(session) { viewModel.start(target, session) }

    val state by viewModel.state.collectAsState()
    val form = state.form
    val dims = Dimens.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.isDirty) { onDirtyStateChanged(state.isDirty) }
    LaunchedEffect(state.finished) {
        state.finished?.let { message ->
            onDirtyStateChanged(false)
            viewModel.consumeFinished()
            onFinished(message)
        }
    }

    if (!state.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dims.screenHorizontalPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        state.error?.let { ErrorBanner(it, onDismiss = viewModel::clearError) }

        if (state.editingId == null) {
            Section("Start from a template") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RoutineTemplates, key = { it.name }) { template ->
                        Pill(
                            text = "${template.emoji} ${template.name}",
                            selected = form.name == template.name,
                            onClick = { viewModel.applyTemplate(template) }
                        )
                    }
                }
            }
        }

        Section("Name") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { showEmojiPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(form.emoji, fontSize = 28.sp)
                }
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { value -> viewModel.update { it.copy(name = value.take(60)) } },
                    placeholder = { Text("e.g. Read 20 minutes") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Section("Type") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceCard(
                    emoji = "✨",
                    title = "Build a habit",
                    subtitle = "Something to do",
                    selected = form.kind == RoutineKind.BUILD,
                    onClick = { viewModel.update { it.copy(kind = RoutineKind.BUILD) } },
                    modifier = Modifier.weight(1f)
                )
                ChoiceCard(
                    emoji = "🚫",
                    title = "Quit something",
                    subtitle = "Something to stay off",
                    selected = form.kind == RoutineKind.AVOID,
                    onClick = { viewModel.update { it.copy(kind = RoutineKind.AVOID) } },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Section("How often") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScheduleOptions.forEach { (schedule, label) ->
                    Pill(label, selected = form.schedule == schedule) {
                        viewModel.update { it.copy(schedule = schedule) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            when (form.schedule) {
                RoutineSchedule.DAILY -> Hint("Every day, from the day it starts.")
                RoutineSchedule.DAYS -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DayOfWeek.entries.forEach { day ->
                            DayPill(
                                label = RoutineText.narrowDay(day),
                                selected = form.days and day.bit() != 0,
                                onClick = { viewModel.update { it.copy(days = it.days xor day.bit()) } }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        TextButton(onClick = { viewModel.update { it.copy(days = RoutineText.WEEKDAYS) } }) { Text("Weekdays") }
                        TextButton(onClick = { viewModel.update { it.copy(days = RoutineText.WEEKENDS) } }) { Text("Weekends") }
                    }
                }
                RoutineSchedule.WEEKLY -> {
                    Stepper(form.weeklyTarget, 1..7, "times a week") { value -> viewModel.update { it.copy(weeklyTarget = value) } }
                    Spacer(Modifier.height(8.dp))
                    Hint("Any days you like. It's judged when the week ends, so a slow Monday is fine.")
                }
                RoutineSchedule.MONTHLY -> {
                    Stepper(form.monthlyTarget, 1..31, "times a month") { value -> viewModel.update { it.copy(monthlyTarget = value) } }
                    Spacer(Modifier.height(8.dp))
                    Hint("Any days you like. It's judged when the month ends.")
                }
                RoutineSchedule.INTERVAL -> {
                    Stepper(form.every, 1..365, "") { value -> viewModel.update { it.copy(every = value) } }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UnitOptions.forEach { (unit, label) ->
                            Pill(if (form.every == 1) label.removeSuffix("s") else label, selected = form.unit == unit) {
                                viewModel.update { it.copy(unit = unit) }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    DateRow("Next due", form.startDate) { showDatePicker = true }
                    Spacer(Modifier.height(8.dp))
                    Hint("Shows up when it's due and stays until it's done. The next one is counted from the day you do it.")
                }
            }
        }

        if (form.canBeChallenge) {
            Section("Challenge") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { viewModel.update { it.copy(isChallenge = !it.isChallenge) } }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Make it a challenge",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "A countdown with a finish line",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = form.isChallenge,
                        onCheckedChange = { on -> viewModel.update { it.copy(isChallenge = on) } }
                    )
                }
                AnimatedVisibility(visible = form.isChallenge) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ChallengeLengths.forEach { days ->
                                Pill("$days days", selected = form.challengeDays == days) {
                                    viewModel.update { it.copy(challengeDays = days) }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (form.challengeEnd < LocalDate.now()) {
                            Text(
                                text = "This challenge ended on ${RoutineText.weekdayDate(form.challengeEnd)}. Make it longer to keep it going.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Hint("Ends on ${RoutineText.weekdayDate(form.challengeEnd)}")
                        }
                    }
                }
            }
        }

        if (form.schedule != RoutineSchedule.INTERVAL) {
            Section("Starts") {
                DateRow("First day", form.startDate) { showDatePicker = true }
                if (state.editingId == null) {
                    val pastDays = remember(form) { viewModel.pastDaysFor(form) }
                    if (pastDays.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        ToggleRow(
                            title = "I've kept it up since then",
                            subtitle = "Marks the ${pastDays.size} days before today as done. Change any of them later in History.",
                            checked = state.fillPastOnCreate,
                            onChange = viewModel::setFillPastOnCreate
                        )
                    }
                } else if (state.pastBlank.isNotEmpty() || state.markedPast != null) {
                    Spacer(Modifier.height(8.dp))
                    PastDaysCard(
                        blank = state.pastBlank,
                        marked = state.markedPast,
                        onMarkAll = viewModel::markPastDone
                    )
                }
            }
        }

        Button(
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = if (state.editingId == null) "Add routine" else "Save changes",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        if (state.editingId != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { viewModel.setArchived(!state.archived) },
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.archived) "Restore" else "Archive")
                }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = !state.isSaving,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Delete")
                }
            }
            Hint("Archiving hides it and keeps its history. Deleting removes both.")
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showDatePicker) {
        RoutineDatePicker(
            initial = form.startDate,
            onPicked = { date -> viewModel.update { current -> current.copy(startDate = date) } },
            onDismiss = { showDatePicker = false }
        )
    }
    if (showEmojiPicker) {
        EmojiPickerSheet(
            current = form.emoji,
            onPicked = { emoji ->
                viewModel.update { it.copy(emoji = emoji) }
                showEmojiPicker = false
            },
            onDismiss = { showEmojiPicker = false }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${form.name}?") },
            text = { Text("Its whole history goes too. Archive it instead to keep the history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

private val ScheduleOptions = listOf(
    RoutineSchedule.DAILY to "Every day",
    RoutineSchedule.DAYS to "Some days",
    RoutineSchedule.WEEKLY to "Times a week",
    RoutineSchedule.MONTHLY to "Times a month",
    RoutineSchedule.INTERVAL to "Every few…"
)

private val UnitOptions = listOf(
    IntervalUnit.DAY to "days",
    IntervalUnit.WEEK to "weeks",
    IntervalUnit.MONTH to "months"
)

private val ChallengeLengths = listOf(7, 14, 21, 30, 60, 90)

private val EmojiChoices = listOf(
    "✅", "🪥", "📖", "📚", "🏋️", "🏃", "🚶", "🚴", "🏊", "🧘", "🏸", "🏓",
    "🏏", "⚽", "💧", "🥗", "🍎", "🥛", "🍳", "🍬", "🍺", "☕", "🚭", "📵",
    "💤", "🛏️", "🌅", "🌙", "🧹", "🧺", "🌬️", "🪴", "🐶", "💊", "🦷", "🧴",
    "🛁", "💰", "📝", "🎸", "🎨", "🧠", "💻", "🙏", "📞", "❤️", "☀️", "🎯"
)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Editing: fill in every blank day before today at once, or confirm it was done. */
@Composable
private fun PastDaysCard(blank: List<LocalDate>, marked: Int?, onMarkAll: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.secondaryContainer)
            .padding(14.dp)
    ) {
        if (marked != null && blank.isEmpty()) {
            Text(
                text = "✓ Marked $marked ${if (marked == 1) "day" else "days"} as done",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSecondaryContainer
            )
            Text(
                text = "Change any single day from History on the Routines page.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSecondaryContainer.copy(alpha = 0.8f)
            )
        } else {
            val count = blank.size
            Text(
                text = "🕰️ ${if (count == 1) "1 earlier day has" else "$count earlier days have"} no answer",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSecondaryContainer
            )
            Text(
                text = "Since ${RoutineText.weekdayDate(blank.first())}. Kept it up? Fill them all in at once, " +
                    "then change any single day from History.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSecondaryContainer.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onMarkAll) {
                Text(if (count == 1) "❤️ Mark it as done" else "❤️ Mark all $count as done")
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.surfaceContainerHigh,
        label = "pill"
    )
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
        color = if (selected) colors.onPrimary else colors.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

@Composable
private fun ChoiceCard(
    emoji: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        label = "choiceCard"
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .border(
                width = 1.5.dp,
                color = if (selected) colors.primary.copy(alpha = 0.6f) else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) colors.onPrimaryContainer else colors.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant
        )
    }
}

@Composable
private fun Stepper(value: Int, range: IntRange, label: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = { onChange((value - 1).coerceIn(range)) },
                enabled = value > range.first
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Fewer")
            }
            Text(
                text = "$value",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 44.dp)
            )
            FilledTonalIconButton(
                onClick = { onChange((value + 1).coerceIn(range)) },
                enabled = value < range.last
            ) {
                Icon(Icons.Default.Add, contentDescription = "More")
            }
        }
        if (label.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DateRow(label: String, date: LocalDate, onClick: () -> Unit) {
    val today = LocalDate.now()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = when (date) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> RoutineText.weekdayDate(date)
            },
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Material's date picker works in UTC days, so dates go in and out as UTC midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineDatePicker(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { millis ->
                    onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("OK", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = pickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiPickerSheet(current: String, onPicked: (String) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = SheetContentInsets,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = rememberSheetHeight(0.8f))
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Text(
                text = "Pick an emoji",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(52.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(EmojiChoices) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (emoji == current) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .clickable { onPicked(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 26.sp)
                    }
                }
            }
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it.take(8) },
                placeholder = { Text("Or type any emoji") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    if (custom.isNotBlank()) {
                        TextButton(onClick = { onPicked(custom.trim()) }) { Text("Use") }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
