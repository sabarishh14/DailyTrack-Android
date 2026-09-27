package com.example.dailytrack_mobile.presentation.screens.routines.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.dailytrack_mobile.data.local.routines.RoutineCheckInSettings
import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import com.example.dailytrack_mobile.domain.routines.DayItem
import com.example.dailytrack_mobile.domain.routines.DayStats
import com.example.dailytrack_mobile.domain.routines.Streak
import com.example.dailytrack_mobile.presentation.components.DailyTrackTimePickerDialog
import com.example.dailytrack_mobile.presentation.components.SheetContentInsets
import com.example.dailytrack_mobile.presentation.components.rememberSheetHeight
import com.example.dailytrack_mobile.presentation.screens.routines.RoutineText
import java.time.DayOfWeek
import java.time.LocalDate

/** Any past day's list, to look back or fill in what was never answered. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DaySheet(
    date: LocalDate,
    today: LocalDate,
    items: List<DayItem>,
    stats: DayStats?,
    streaks: Map<Long, Streak>,
    onStatus: (DayItem, CheckInStatus?) -> Unit,
    onSkip: (DayItem) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = SheetContentInsets,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        // Sized to the day's routines, capped so a long list scrolls.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = rememberSheetHeight(0.85f))
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Text(
                text = RoutineText.longDate(date, today),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            val summary = stats?.takeIf { it.total > 0 }?.let {
                "${it.done} of ${it.total} done · ${RoutineText.percent(it.fraction)}"
            } ?: "Nothing counted this day"
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            if (items.isEmpty()) {
                Text(
                    text = "No routines were due.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.routine.id }) { item ->
                        RoutineRow(
                            item = item,
                            streak = streaks[item.routine.id],
                            onStatus = { onStatus(item, it) },
                            onSkip = { onSkip(item) }
                        )
                    }
                }
            }
        }
    }
}

private val SkipReasons = listOf("🤒 Unwell", "✈️ Travelling", "😴 Rest day", "⏳ No time", "🌧️ Weather", "🎉 Occasion")

/** Skip = a genuine reason. One tap on a reason saves it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SkipReasonSheet(
    item: DayItem,
    onSkip: (reason: String?) -> Unit,
    onDismiss: () -> Unit
) {
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
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Text(
                text = "Skip ${listOfNotNull(item.routine.emoji, item.routine.name).joinToString(" ")}?",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "For a genuine reason. A skip doesn't lower your score or break your streak.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkipReasons.forEach { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { onSkip(reason.substringAfter(' ')) }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it.take(120) },
                placeholder = { Text("Or write a reason") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (custom.isNotBlank()) onSkip(custom.trim()) }),
                trailingIcon = {
                    if (custom.isNotBlank()) {
                        IconButton(onClick = { onSkip(custom.trim()) }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Skip with this reason")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                onClick = { onSkip(null) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Skip without a reason")
            }
        }
    }
}

/** When the nightly check-in comes. Turning it on asks for notification permission first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CheckInSettingsSheet(
    settings: RoutineCheckInSettings.Settings,
    onEnabled: (Boolean) -> Unit,
    onTime: (java.time.LocalTime) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onTry: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingAfterGrant by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingAfterGrant?.invoke()
        pendingAfterGrant = null
    }
    fun withPermission(block: () -> Unit) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            block()
        } else {
            pendingAfterGrant = block
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌙", fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Nightly check-in",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "A notification asks about every routine still open. Answer ❤️ / 😭 / Skip right from it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            SettingCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (settings.enabled) onEnabled(false) else withPermission { onEnabled(true) } }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Remind me every night",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.enabled,
                        onCheckedChange = { on -> if (on) withPermission { onEnabled(true) } else onEnabled(false) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            Column(modifier = Modifier.alpha(if (settings.enabled) 1f else 0.45f)) {
                SettingCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = settings.enabled) { showTimePicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Time",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = RoutineText.time(settings.time),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SettingCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DayOfWeek.entries.forEach { day ->
                            DayPill(
                                label = RoutineText.narrowDay(day),
                                selected = day in settings.days,
                                enabled = settings.enabled,
                                onClick = { onToggleDay(day) }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { withPermission(onTry) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Try it now")
            }
        }
    }

    if (showTimePicker) {
        DailyTrackTimePickerDialog(
            initialTime = settings.time,
            onTimeSelected = {
                onTime(it)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        content()
    }
}

@Composable
internal fun DayPill(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surfaceContainerHighest,
        label = "dayPill"
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
        )
    }
}
