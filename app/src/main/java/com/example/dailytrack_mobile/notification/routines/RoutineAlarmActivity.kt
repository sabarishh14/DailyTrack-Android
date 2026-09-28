package com.example.dailytrack_mobile.notification.routines

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.data.local.datastore.ThemeManager
import com.example.dailytrack_mobile.data.local.routines.RoutineAlarmSettings.AlarmKind
import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import com.example.dailytrack_mobile.domain.routines.DayItem
import com.example.dailytrack_mobile.domain.routines.DayStats
import com.example.dailytrack_mobile.presentation.screens.routines.RoutineText
import com.example.dailytrack_mobile.presentation.screens.routines.components.DaySkippedColor
import com.example.dailytrack_mobile.presentation.screens.routines.components.DoneColor
import com.example.dailytrack_mobile.presentation.screens.routines.components.EmojiBadge
import com.example.dailytrack_mobile.presentation.screens.routines.components.MissedColor
import com.example.dailytrack_mobile.presentation.screens.routines.components.drawMixRing
import com.example.dailytrack_mobile.presentation.screens.routines.components.mix
import com.example.dailytrack_mobile.presentation.theme.DailyTrackTheme
import com.example.dailytrack_mobile.presentation.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A Routines alarm over the lock screen, like the clock's. Opening it, touching
 * it or pressing a volume key silences it; then it's answered right here: one
 * routine's Done / Snooze / Dismiss, or every routine still open tonight.
 */
@AndroidEntryPoint
class RoutineAlarmActivity : ComponentActivity() {

    private var alarm by mutableStateOf<RoutineAlarmService.Alarm?>(null)
    private var silenced by mutableStateOf(false)
    private var touched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (!take(intent)) {
            finish()
            return
        }
        val themes = ThemeManager(applicationContext)
        setContent {
            // Always dark: it's usually night, and it's a screen that lights up a dark room.
            DailyTrackTheme(
                themeMode = ThemeMode.DARK,
                appTheme = themes.getInitialTheme(),
                withAmoled = themes.getInitialAmoled()
            ) {
                alarm?.let { current ->
                    AlarmScreen(
                        alarm = current,
                        silenced = silenced,
                        onDone = { done(current) },
                        onSnooze = { snooze(current) },
                        onDismiss = { dismiss(current) },
                        onClose = ::finish,
                        onTimeout = { if (!touched) finish() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        take(intent)
    }

    /** Shows [intent]'s alarm; opened from its notification, it's silenced at once. */
    private fun take(intent: Intent): Boolean {
        val next = RoutineAlarmService.Alarm.from(intent) ?: return false
        alarm = next
        touched = false
        silenced = RoutineAlarmService.ringing == null
        if (intent.getBooleanExtra(EXTRA_OPENED, false)) silence()
        return true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            touched = true
            silence()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            touched = true
            silence()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun silence() {
        if (silenced) return
        silenced = true
        RoutineAlarmService.silence(this)
    }

    private fun stopTest(alarm: RoutineAlarmService.Alarm) {
        RoutineAlarmService.stop(this)
        RoutineReminderNotifier.cancel(this, alarm.routineId)
    }

    private fun done(alarm: RoutineAlarmService.Alarm) {
        if (alarm.test) {
            stopTest(alarm)
        } else {
            send(RoutineCheckInReceiver.ACTION_ANSWER, alarm) {
                putExtra(RoutineCheckInReceiver.EXTRA_STATUS, CheckInStatus.DONE.key)
                putExtra(RoutineCheckInReceiver.EXTRA_FROM_REMINDER, true)
            }
        }
        finish()
    }

    private fun snooze(alarm: RoutineAlarmService.Alarm) {
        if (alarm.test) {
            stopTest(alarm)
        } else {
            val action = if (alarm.kind == AlarmKind.CHECK_IN) RoutineCheckInReceiver.ACTION_SNOOZE_CHECK_IN else RoutineCheckInReceiver.ACTION_SNOOZE
            send(action, alarm)
        }
        finish()
    }

    private fun dismiss(alarm: RoutineAlarmService.Alarm) {
        if (alarm.test) {
            stopTest(alarm)
        } else {
            val action = if (alarm.kind == AlarmKind.CHECK_IN) RoutineCheckInReceiver.ACTION_LATER_CHECK_IN else RoutineCheckInReceiver.ACTION_DISMISS
            send(action, alarm)
        }
        finish()
    }

    private fun send(action: String, alarm: RoutineAlarmService.Alarm, extras: Intent.() -> Unit = {}) {
        // Also stops the fallback ringing notification, if that's what's ringing.
        RoutineReminderNotifier.cancel(this, alarm.routineId)
        sendBroadcast(
            Intent(this, RoutineCheckInReceiver::class.java)
                .setAction(action)
                .putExtra(RoutineCheckInReceiver.EXTRA_ROUTINE_ID, alarm.routineId)
                .putExtra(RoutineCheckInReceiver.EXTRA_DATE, alarm.date)
                .apply(extras)
        )
    }

    companion object {
        private const val EXTRA_OPENED = "alarm_opened"

        /** [opened]: tapped from its notification, rather than shown over the lock screen by itself. */
        fun intent(context: Context, alarm: RoutineAlarmService.Alarm, opened: Boolean): Intent =
            alarm.putInto(Intent(context, RoutineAlarmActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(EXTRA_OPENED, opened)
    }
}

// ── Screen ───────────────────────────────────────────────────────────────────

private val Night = Color(0xFF06070B)
private val Soft = Color.White.copy(alpha = 0.66f)
private val Faint = Color.White.copy(alpha = 0.08f)
private val Clock24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val Clock12 = DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH)
private val AmPm = DateTimeFormatter.ofPattern("a", Locale.ENGLISH)
private val LongDay = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

@Composable
private fun AlarmScreen(
    alarm: RoutineAlarmService.Alarm,
    silenced: Boolean,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    onTimeout: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(alarm) {
        delay(RoutineAlarmService.RING_FOR_MS)
        onTimeout()
    }
    val transition = rememberInfiniteTransition(label = "alarm")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "pulse"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val glow = if (silenced) 0.5f else breathe
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(colors.primary.copy(alpha = 0.34f * glow), Color.Transparent),
                        center = Offset(size.width / 2, size.height * 0.3f),
                        radius = size.width * 1.1f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(colors.tertiary.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width, size.height),
                        radius = size.width
                    )
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RingingChip(silenced)
            if (alarm.kind == AlarmKind.CHECK_IN) {
                CheckIn(alarm, onSnooze = onSnooze, onLater = onDismiss, onClose = onClose)
            } else {
                Reminder(alarm, silenced, pulse, onDone = onDone, onSnooze = onSnooze, onDismiss = onDismiss)
            }
        }
    }
}

@Composable
private fun RingingChip(silenced: Boolean) {
    AnimatedContent(
        targetState = silenced,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
        label = "ringingChip"
    ) { quiet ->
        Text(
            text = if (quiet) "🔕  Silenced" else "🔔  Ringing · tap anywhere to silence",
            style = MaterialTheme.typography.labelLarge,
            color = Soft,
            modifier = Modifier
                .clip(CircleShape)
                .background(Faint)
                .padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

/** The time, kept current, and the date. */
@Composable
private fun Clock(large: Boolean) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = LocalDateTime.now()
            delay(60_000L - (current.second * 1_000L + current.nano / 1_000_000))
            now = LocalDateTime.now()
        }
    }
    val is24 = DateFormat.is24HourFormat(context)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = now.format(if (is24) Clock24 else Clock12),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = if (large) 92.sp else 60.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = if (large) (-3).sp else (-2).sp
            ),
            color = Color.White
        )
        if (!is24) {
            Text(
                text = now.format(AmPm),
                style = MaterialTheme.typography.titleLarge,
                color = Soft,
                modifier = Modifier.padding(start = 8.dp, bottom = if (large) 20.dp else 12.dp)
            )
        }
    }
    Text(text = now.format(LongDay), style = MaterialTheme.typography.titleMedium, color = Soft)
}

// ── One routine ──────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.Reminder(
    alarm: RoutineAlarmService.Alarm,
    silenced: Boolean,
    pulse: Float,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    Spacer(Modifier.height(28.dp))
    Clock(large = true)
    Spacer(Modifier.weight(1f))
    PulsingEmoji(alarm.emoji, pulse, active = !silenced)
    Spacer(Modifier.height(20.dp))
    Text(
        text = alarm.name,
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        color = Color.White,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = alarm.text,
        style = MaterialTheme.typography.bodyLarge,
        color = Soft,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.weight(1f))
    Button(
        onClick = onDone,
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Text(
            text = if (alarm.test) "Stop" else "❤️   Done",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        if (!alarm.test) {
            SoftButton("💤  ${alarm.snoozeMinutes} min", onSnooze, Modifier.weight(1f))
        }
        SoftButton("Dismiss", onDismiss, Modifier.weight(1f))
    }
}

/** The routine's emoji, with rings rippling out while it rings. */
@Composable
private fun PulsingEmoji(emoji: String?, pulse: Float, active: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier = Modifier.size(232.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inner = 72.dp.toPx()
            val outer = size.minDimension / 2
            if (active) {
                repeat(3) { ring ->
                    val t = (pulse + ring / 3f) % 1f
                    drawCircle(
                        color = accent.copy(alpha = (1f - t) * 0.4f),
                        radius = inner + (outer - inner) * t,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
            drawCircle(color = accent.copy(alpha = 0.14f), radius = inner + 10.dp.toPx())
        }
        Box(
            modifier = Modifier
                .size(144.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.5f), accent.copy(alpha = 0.14f)))),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji?.takeIf { it.isNotBlank() } ?: "⏰", fontSize = 64.sp)
        }
    }
}

// ── Tonight's check-in ───────────────────────────────────────────────────────

@Composable
private fun ColumnScope.CheckIn(
    alarm: RoutineAlarmService.Alarm,
    onSnooze: () -> Unit,
    onLater: () -> Unit,
    onClose: () -> Unit
) {
    val viewModel: RoutineAlarmVM = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val allAnswered = state.loaded && state.open == 0

    Spacer(Modifier.height(18.dp))
    Clock(large = false)
    Spacer(Modifier.height(22.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "🌙  Nightly check-in",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Text(
                text = when {
                    !state.loaded -> "Loading…"
                    state.items.isEmpty() -> "Nothing was due today"
                    allAnswered -> "All answered"
                    state.open == 1 -> "1 left to answer"
                    else -> "${state.open} left to answer"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Soft
            )
        }
        state.stats?.takeIf { it.total > 0 }?.let { stats -> DayRing(stats) }
    }
    Spacer(Modifier.height(14.dp))
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
    ) {
        if (!state.loaded) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                if (allAnswered && state.items.isNotEmpty()) {
                    item(key = "all-answered") { AllAnswered(state.stats) }
                }
                items(state.items, key = { it.routine.id }) { item ->
                    CheckInRow(item) { status -> viewModel.answer(item.routine.id, status) }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    if (allAnswered || (state.loaded && state.items.isEmpty())) {
        Button(
            onClick = onClose,
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text("Done for tonight  ✓", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SoftButton("💤  ${alarm.snoozeMinutes} min", onSnooze, Modifier.weight(1f))
            SoftButton("Later", onLater, Modifier.weight(1f))
        }
    }
}

/** Today so far as a small ring: done, skipped and missed. */
@Composable
private fun DayRing(stats: DayStats) {
    Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        val track = Color.White.copy(alpha = 0.12f)
        Canvas(modifier = Modifier.fillMaxSize()) { drawMixRing(stats.mix(stats.date), track, strokeWidth = 5.dp.toPx()) }
        Text(
            text = "${stats.done}/${stats.total}",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
            color = Color.White
        )
    }
}

@Composable
private fun AllAnswered(stats: DayStats?) {
    val perfect = stats != null && stats.total > 0 && stats.done == stats.total
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.32f), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f))
                )
            )
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = if (perfect) "🎉" else "✨", fontSize = 40.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (perfect) "Perfect day!" else "All answered",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        if (stats != null && stats.total > 0) {
            Text(
                text = "${stats.done} of ${stats.total} done today",
                style = MaterialTheme.typography.bodyMedium,
                color = Soft
            )
        }
    }
}

@Composable
private fun CheckInRow(item: DayItem, onAnswer: (CheckInStatus?) -> Unit) {
    val answered = item.status != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = if (answered) 0.04f else 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EmojiBadge(item.routine.emoji, item.status, size = 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.routine.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = if (answered) 0.75f else 1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val line = RoutineText.itemLine(item, null)
            if (line.isNotEmpty()) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        AnswerButton("❤️", item.status == CheckInStatus.DONE, DoneColor) {
            onAnswer(if (item.status == CheckInStatus.DONE) null else CheckInStatus.DONE)
        }
        AnswerButton("😭", item.status == CheckInStatus.MISSED, MissedColor) {
            onAnswer(if (item.status == CheckInStatus.MISSED) null else CheckInStatus.MISSED)
        }
        AnswerButton("⏭", item.status == CheckInStatus.SKIPPED, DaySkippedColor) {
            onAnswer(if (item.status == CheckInStatus.SKIPPED) null else CheckInStatus.SKIPPED)
        }
    }
}

/** One answer; picking the chosen one again clears it. */
@Composable
private fun AnswerButton(emoji: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) color.copy(alpha = 0.3f) else Faint, label = "answerBg")
    val border by animateColorAsState(if (selected) color else Color.Transparent, label = "answerBorder")
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, spring(dampingRatio = 0.45f), label = "answerScale")
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .size(42.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(background)
            .border(1.5.dp, border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 18.sp)
    }
}

@Composable
private fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Faint, contentColor = Color.White),
        modifier = modifier.height(56.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
    }
}
