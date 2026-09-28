package com.example.dailytrack_mobile.presentation.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.data.local.routines.RoutineAlarmSettings
import com.example.dailytrack_mobile.data.local.routines.RoutineAlarmSettings.AlarmKind
import com.example.dailytrack_mobile.notification.routines.AlarmSounds
import com.example.dailytrack_mobile.presentation.components.DailyTrackTimePickerDialog
import com.example.dailytrack_mobile.presentation.components.topBarIconButtonColors
import com.example.dailytrack_mobile.presentation.screens.settings.components.AlarmSoundSheet
import com.example.dailytrack_mobile.presentation.screens.settings.components.SettingsCard
import com.example.dailytrack_mobile.presentation.screens.settings.components.SettingsClickItem
import com.example.dailytrack_mobile.presentation.screens.settings.components.SettingsDivider
import com.example.dailytrack_mobile.presentation.screens.settings.components.SettingsSectionLabel
import com.example.dailytrack_mobile.presentation.screens.settings.components.SettingsToggleItem
import com.example.dailytrack_mobile.presentation.util.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The daily tracking reminder, and how Routines alarms ring. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersSettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val dims = Dimens.current
    var showTimePicker by remember { mutableStateOf(false) }
    var soundSheet by remember { mutableStateOf<AlarmKind?>(null) }
    val alarmVM: RoutineAlarmSettingsVM = hiltViewModel()
    val alarm by alarmVM.settings.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) onAction(SettingsAction.OnReminderToggled(true))
    }

    fun toggleReminder(on: Boolean) {
        val needsPermission = on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else onAction(SettingsAction.OnReminderToggled(on))
    }

    val parsedTime = remember(state.reminderTime) {
        runCatching { LocalTime.parse(state.reminderTime) }.getOrDefault(LocalTime.of(21, 0))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Reminders & alarms",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    FilledIconButton(colors = topBarIconButtonColors(), onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(dims.iconSizeMedium)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = dims.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = dims.screenBottomPadding)
        ) {
            // ── Daily reminder ──────────────────────────────────────────────
            item { SettingsSectionLabel("Daily reminder") }
            item {
                SettingsCard {
                    SettingsToggleItem(
                        icon = if (state.isReminderEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        title = "Remind me to track",
                        subtitle = "Expenses, habits and activities",
                        checked = state.isReminderEnabled,
                        onCheckedChange = ::toggleReminder
                    )
                    Column(modifier = Modifier.alpha(if (state.isReminderEnabled) 1f else 0.45f)) {
                        SettingsDivider()
                        SettingsClickItem(
                            icon = Icons.Default.Schedule,
                            title = "Time",
                            subtitle = formatTime(parsedTime),
                            enabled = state.isReminderEnabled,
                            onClick = { showTimePicker = true }
                        )
                        SettingsDivider()
                        DaysRow(
                            days = state.reminderDays,
                            enabled = state.isReminderEnabled,
                            onToggle = { onAction(SettingsAction.OnReminderDayToggled(it)) }
                        )
                    }
                    SettingsDivider()
                    SettingsClickItem(
                        icon = Icons.Default.NotificationsActive,
                        title = "Send a test notification",
                        onClick = { onAction(SettingsAction.OnSendTestNotification) }
                    )
                }
            }

            // ── Routine alarms ──────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionLabel("Routine alarms")
                Text(
                    text = "For routines, and the nightly check-in, set to ring like an alarm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }
            item {
                SettingsCard {
                    SoundItem(Icons.Default.Alarm, "Routine alarm sound", alarm.reminderSound) { soundSheet = AlarmKind.REMINDER }
                    SettingsDivider()
                    SoundItem(Icons.Default.Bedtime, "Nightly check-in sound", alarm.checkInSound) { soundSheet = AlarmKind.CHECK_IN }
                    SettingsDivider()
                    SettingsToggleItem(
                        icon = Icons.Default.Vibration,
                        title = "Vibrate",
                        checked = alarm.vibrate,
                        onCheckedChange = alarmVM::setVibrate
                    )
                    SettingsDivider()
                    SettingsToggleItem(
                        icon = Icons.Default.GraphicEq,
                        title = "Gentle start",
                        subtitle = "Starts soft, louder over 30 seconds",
                        checked = alarm.gentle,
                        onCheckedChange = alarmVM::setGentle
                    )
                    SettingsDivider()
                    SnoozeRow(alarm.snoozeMinutes, alarmVM::setSnooze)
                }
            }
            item {
                OutlinedButton(
                    onClick = alarmVM::test,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Test alarm")
                }
            }
            item {
                Text(
                    text = "Set a routine to ring from its Reminder, and the nightly check-in from the Routines page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    if (showTimePicker) {
        DailyTrackTimePickerDialog(
            initialTime = parsedTime,
            is24Hour = false,
            onTimeSelected = { newTime ->
                onAction(SettingsAction.OnReminderTimeChanged(newTime))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    soundSheet?.let { kind ->
        AlarmSoundSheet(
            title = if (kind == AlarmKind.CHECK_IN) "Nightly check-in sound" else "Routine alarm sound",
            selected = alarm.soundFor(kind),
            onPick = { alarmVM.setSound(kind, it) },
            onDismiss = { soundSheet = null }
        )
    }
}

/** A sound choice, showing the sound's name. */
@Composable
private fun SoundItem(icon: ImageVector, title: String, value: String?, onClick: () -> Unit) {
    val context = LocalContext.current
    val name by produceState(if (value == RoutineAlarmSettings.SILENT) "Silent" else "…", value) {
        this.value = withContext(Dispatchers.IO) { AlarmSounds.title(context, value) }
    }
    SettingsClickItem(
        icon = icon,
        title = title,
        subtitle = name,
        trailing = {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        },
        onClick = onClick
    )
}

@Composable
private fun DaysRow(days: Set<DayOfWeek>, enabled: Boolean, onToggle: (DayOfWeek) -> Unit) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)) {
        RowLabel(Icons.Default.EventRepeat, "Repeat", repeatSummary(days))
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DayOfWeek.entries.forEach { day ->
                Pill(
                    text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    selected = day in days,
                    enabled = enabled,
                    circle = true
                ) { onToggle(day) }
            }
        }
    }
}

@Composable
private fun SnoozeRow(minutes: Int, onPick: (Int) -> Unit) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)) {
        RowLabel(Icons.Default.Snooze, "Snooze", "$minutes minutes")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoutineAlarmSettings.SNOOZE_CHOICES.forEach { choice ->
                Pill(text = "$choice", selected = choice == minutes, enabled = true, circle = false) { onPick(choice) }
            }
        }
    }
}

@Composable
private fun RowLabel(icon: ImageVector, title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, enabled: Boolean, circle: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surfaceContainerHighest,
        animationSpec = tween(200),
        label = "pill"
    )
    Box(
        modifier = Modifier
            .then(if (circle) Modifier.size(38.dp) else Modifier.height(36.dp))
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (circle) 0.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
        )
    }
}

private fun formatTime(time: LocalTime): String =
    time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())).lowercase(Locale.getDefault())

private fun repeatSummary(days: Set<DayOfWeek>): String = when {
    days.size == 7 -> "Daily"
    days.isEmpty() -> "Never"
    days.size == 5 && DayOfWeek.SATURDAY !in days && DayOfWeek.SUNDAY !in days -> "Weekdays"
    days.size == 2 && DayOfWeek.SATURDAY in days && DayOfWeek.SUNDAY in days -> "Weekends"
    else -> days.sortedBy { it.value }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}
