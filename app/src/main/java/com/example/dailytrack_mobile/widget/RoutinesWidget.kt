package com.example.dailytrack_mobile.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.dailytrack_mobile.MainActivity
import com.example.dailytrack_mobile.data.repository.RoutinesRepository
import com.example.dailytrack_mobile.data.repository.engine
import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import com.example.dailytrack_mobile.notification.routines.RoutineAlarmService
import com.example.dailytrack_mobile.notification.routines.RoutineCheckInNotifier
import com.example.dailytrack_mobile.notification.routines.RoutineReminderNotifier
import com.example.dailytrack_mobile.presentation.navigation.Routes
import com.example.dailytrack_mobile.presentation.screens.routines.RoutineText
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Today's routines on the home screen, at any size: from a 1×1 consistency ring up
 * to a scrolling list where each routine can be ticked with ❤️ (or 😭 / ⏭ when
 * there's room). It reads the phone's own copy, so it works offline, and taps are
 * saved the same way as in the app.
 */
class RoutinesWidget : GlanceAppWidget() {

    // Laid out for the exact size it's given, so every size gets its own arrangement.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = load(context)
        provideContent {
            GlanceTheme { Content(context, model) }
        }
    }

    companion object {
        private const val STALE_AFTER_MS = 15 * 60_000L
        private const val REFRESH_TIMEOUT_MS = 5_000L

        /** Redraws every Routines widget on the home screen. */
        suspend fun refreshAll(context: Context) {
            runCatching { RoutinesWidget().updateAll(context) }
        }

        internal fun repository(context: Context): RoutinesRepository =
            EntryPointAccessors.fromApplication(context.applicationContext, RoutinesWidgetEntryPoint::class.java)
                .routinesRepository()

        private suspend fun load(context: Context): WidgetModel {
            val repository = repository(context)
            var snapshot = repository.load()
            // Answers given on the web should show up too: catch up if the copy is old.
            if (snapshot.owner != null && System.currentTimeMillis() - (snapshot.syncedAt ?: 0L) > STALE_AFTER_MS) {
                withTimeoutOrNull(REFRESH_TIMEOUT_MS) { repository.refresh() }
                snapshot = repository.load()
            }
            if (snapshot.owner == null) return WidgetModel(signedIn = false)
            val today = LocalDate.now()
            val engine = snapshot.engine()
            val stats = engine.dayStats(today)
            val items = engine.dayItems(today)
                .filter { it.required || it.status != null || it.progress?.met == false }
                .sortedBy { it.status != null } // still open first
                .map { item ->
                    WidgetItem(
                        id = item.routine.id,
                        emoji = item.routine.emoji?.takeIf { it.isNotBlank() } ?: "✅",
                        name = item.routine.name,
                        line = RoutineText.itemLine(item, null),
                        status = item.status
                    )
                }
            return WidgetModel(
                signedIn = true,
                hasRoutines = engine.routines.isNotEmpty(),
                consistency = engine.consistency(today).fraction,
                done = stats.done,
                total = stats.total,
                left = items.count { it.status == null },
                streak = engine.perfectDays(today).current,
                items = items
            )
        }
    }
}

class RoutinesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RoutinesWidget()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RoutinesWidgetEntryPoint {
    fun routinesRepository(): RoutinesRepository
}

internal data class WidgetItem(
    val id: Long,
    val emoji: String,
    val name: String,
    val line: String,
    val status: CheckInStatus?
)

internal data class WidgetModel(
    val signedIn: Boolean,
    val hasRoutines: Boolean = false,
    val consistency: Double? = null,
    val done: Int = 0,
    val total: Int = 0,
    val left: Int = 0,
    val streak: Int = 0,
    val items: List<WidgetItem> = emptyList()
) {
    val percent: String get() = consistency?.let { "${(it * 100).roundToInt()}%" } ?: "—"
    val todayLine: String
        get() = when {
            items.isEmpty() -> "Nothing due today"
            left == 0 -> if (total > 0 && done == total) "All done today 🎉" else "All answered today"
            else -> "$done of $total done · $left left"
        }
}

// ── Answering ────────────────────────────────────────────────────────────────

private val RoutineIdKey = ActionParameters.Key<Long>("routine_id")
private val StatusKey = ActionParameters.Key<String>("status")
private const val CLEAR = "clear"

/** A tap on ❤️ / 😭 / ⏭: saved on the phone at once, then sent to the server. */
class RoutinesWidgetAnswer : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val routineId = parameters[RoutineIdKey] ?: return
        val status = parameters[StatusKey]?.takeIf { it != CLEAR }?.let(CheckInStatus::from)
        val repository = RoutinesWidget.repository(context)
        val today = LocalDate.now()
        repository.setCheckIn(routineId, today, status)
        if (status != null) {
            // Its reminder, or its alarm if it's ringing, has done its job.
            RoutineAlarmService.stopIfFor(context, routineId)
            RoutineReminderNotifier.cancel(context, routineId)
        }
        RoutineCheckInNotifier.syncIfShowing(context, repository.load().engine(), today)
        RoutinesWidget.refreshAll(context)
        withTimeoutOrNull(8_000L) { repository.flush() }
    }
}

// ── Layout ───────────────────────────────────────────────────────────────────

private val DoneColor = Color(0xFFFF4D6D)
private val MissedColor = Color(0xFF5B8DEF)
private val SkippedColor = Color(0xFFFFC300)

@Composable
private fun Content(context: Context, model: WidgetModel) {
    val size = LocalSize.current
    val tiny = size.width < 110.dp && size.height < 110.dp
    val openRoutines = actionStartActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("route", Routes.Routines.route)
    )
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(22.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(openRoutines)
            .padding(if (tiny) 6.dp else 12.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            !model.signedIn -> Message("🔒", "Open DailyTrack to sign in")
            !model.hasRoutines -> Message("🌱", "Add a routine in DailyTrack")
            tiny -> Ring(model.consistency, min(size.width, size.height) - 12.dp, model.percent)
            size.height < 110.dp -> Strip(model, size.width, size.height)
            size.width < 180.dp && size.height < 220.dp -> Compact(model, size.width, size.height)
            else -> Full(model, size.width, size.height)
        }
    }
}

/** One cell tall: the ring, today, and a ❤️ for the next routine when there's room. */
@Composable
private fun Strip(model: WidgetModel, width: Dp, height: Dp) {
    val next = model.items.firstOrNull { it.status == null }
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Ring(model.consistency, min(height - 16.dp, 64.dp), model.percent)
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = "${model.percent} consistent",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Text(
                text = model.todayLine,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1
            )
        }
        if (next != null && width >= 250.dp) {
            Spacer(GlanceModifier.width(8.dp))
            NextButton(next, showName = width >= 330.dp)
        }
    }
}

/** A small square: the ring big, today's count and the streak under it. */
@Composable
private fun Compact(model: WidgetModel, width: Dp, height: Dp) {
    val ring = min(width - 16.dp, height - 52.dp).coerceIn(40.dp, 120.dp)
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ring(model.consistency, ring, model.percent, caption = if (ring >= 76.dp) "30 days" else null)
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = if (model.items.isEmpty()) "Nothing due" else "${model.done}/${model.total} today",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            maxLines = 1
        )
        if (height >= 170.dp && model.streak >= 1) {
            Text(
                text = "🔥 ${model.streak} perfect ${if (model.streak == 1) "day" else "days"}",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1
            )
        }
    }
}

/** Bigger: a header, then today's routines to tick; the list scrolls once it's tall. */
@Composable
private fun Full(model: WidgetModel, width: Dp, height: Dp) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Ring(model.consistency, 46.dp, model.percent)
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "Routines",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Text(
                    text = model.todayLine,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 1
                )
            }
            if (width >= 240.dp && model.streak >= 2) {
                Text(
                    text = "🔥 ${model.streak}",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        when {
            model.items.isEmpty() -> Text(
                text = "Nothing due today. Enjoy it 🌿",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp)
            )
            height >= 220.dp -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(model.items, itemId = { it.id }) { item -> ItemRow(item, width) }
            }
            else -> {
                // As many rows as fit, with "+N more" when some don't.
                val fits = ((height - 24.dp - 46.dp - 8.dp) / ROW_HEIGHT).toInt().coerceAtLeast(1)
                val shown = if (model.items.size > fits) model.items.take(fits - 1) else model.items
                shown.forEach { item -> ItemRow(item, width) }
                val hidden = model.items.size - shown.size
                if (hidden > 0) {
                    Text(
                        text = "+$hidden more",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                        modifier = GlanceModifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private val ROW_HEIGHT = 40.dp

@Composable
private fun ItemRow(item: WidgetItem, width: Dp) {
    val answered = item.status != null
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = item.emoji, style = TextStyle(fontSize = 18.sp))
        Spacer(GlanceModifier.width(8.dp))
        if (width >= 150.dp) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = item.name,
                    style = TextStyle(
                        color = if (answered) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                if (width >= 280.dp && item.line.isNotEmpty()) {
                    Text(
                        text = item.line,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                        maxLines = 1
                    )
                }
            }
        } else {
            Spacer(GlanceModifier.defaultWeight())
        }
        if (width >= 280.dp) {
            AnswerButton(item, CheckInStatus.DONE)
            AnswerButton(item, CheckInStatus.MISSED)
            AnswerButton(item, CheckInStatus.SKIPPED)
        } else {
            AnswerButton(item, CheckInStatus.DONE)
        }
    }
}

/** ❤️ / 😭 / ⏭; tapping the chosen one again clears it. */
@Composable
private fun AnswerButton(item: WidgetItem, status: CheckInStatus) {
    val selected = item.status == status
    val (emoji, color) = when (status) {
        CheckInStatus.DONE -> "❤️" to DoneColor
        CheckInStatus.MISSED -> "😭" to MissedColor
        CheckInStatus.SKIPPED -> "⏭" to SkippedColor
    }
    Spacer(GlanceModifier.width(5.dp))
    Box(
        modifier = GlanceModifier
            .size(30.dp)
            .cornerRadius(15.dp)
            .background(if (selected) ColorProvider(color.copy(alpha = 0.32f)) else GlanceTheme.colors.secondaryContainer)
            .clickable(answer(item.id, if (selected) CLEAR else status.key)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, style = TextStyle(fontSize = 13.sp))
    }
}

/** The next open routine, ready to be marked done. */
@Composable
private fun NextButton(item: WidgetItem, showName: Boolean) {
    Row(
        modifier = GlanceModifier
            .height(36.dp)
            .cornerRadius(18.dp)
            .background(GlanceTheme.colors.secondaryContainer)
            .clickable(answer(item.id, CheckInStatus.DONE.key))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "❤️ ${item.emoji}", style = TextStyle(fontSize = 14.sp))
        if (showName) {
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = item.name,
                style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1
            )
        }
    }
}

private fun answer(routineId: Long, status: String) =
    actionRunCallback<RoutinesWidgetAnswer>(actionParametersOf(RoutineIdKey to routineId, StatusKey to status))

@Composable
private fun Message(emoji: String, text: String) {
    val size = LocalSize.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Text(text = emoji, style = TextStyle(fontSize = 24.sp))
        if (size.width >= 110.dp && size.height >= 90.dp) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = text,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 2
            )
        }
    }
}

/** The consistency ring, as in the app: green for kept, red for missed, with the percentage inside. */
@Composable
private fun Ring(fraction: Double?, size: Dp, label: String, caption: String? = null) {
    Box(modifier = GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(ringBitmap(fraction)),
            contentDescription = "Consistency $label",
            modifier = GlanceModifier.size(size)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = (size.value * 0.23f).coerceIn(9f, 26f).sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp),
                    maxLines = 1
                )
            }
        }
    }
}

private const val RING_PX = 240
private const val STROKE_PX = 24f
private val RingTrack = Color(0x40808080)
private val RingKept = Color(0xFF34C759)
private val RingMissed = Color(0xFFFF453A)

private fun ringBitmap(fraction: Double?): Bitmap {
    val bitmap = Bitmap.createBitmap(RING_PX, RING_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val inset = STROKE_PX / 2
    val bounds = RectF(inset, inset, RING_PX - inset, RING_PX - inset)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_PX
    }
    paint.color = RingTrack.toArgb()
    canvas.drawArc(bounds, 0f, 360f, false, paint)
    if (fraction != null) {
        val kept = fraction.toFloat().coerceIn(0f, 1f)
        paint.strokeCap = if (kept > 0f && kept < 1f) Paint.Cap.BUTT else Paint.Cap.ROUND
        if (kept > 0f) {
            paint.color = RingKept.toArgb()
            canvas.drawArc(bounds, -90f, 360f * kept, false, paint)
        }
        if (kept < 1f) {
            paint.color = RingMissed.toArgb()
            canvas.drawArc(bounds, -90f + 360f * kept, 360f * (1f - kept), false, paint)
        }
    }
    return bitmap
}
