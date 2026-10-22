package com.example.dailytrack_mobile.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import com.example.dailytrack_mobile.R
import com.example.dailytrack_mobile.data.local.routines.RoutinesSnapshot
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
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Today's routines on the home screen, at any size: from a 1×1 consistency ring up
 * to a scrolling list where each routine is answered with ❤️ / 😭 / ⏭ (just ❤️ when
 * narrow). It reads the phone's own copy, so it works offline, and taps are saved
 * the same way as in the app.
 */
class RoutinesWidget : GlanceAppWidget() {

    // Laid out for the exact size it's given, so every size gets its own arrangement.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = repository(context)
        repository.load() // the phone's copy, from disk, so the first frame is already right
        provideContent {
            // Follows the copy as it changes, wherever from — the app, a notification,
            // an alarm or a tap here — so an open widget never shows a stale day.
            // (Glance keeps a widget's session open for a while and would otherwise
            // redraw what it read when it opened.)
            val snapshot by repository.snapshot.collectAsState()
            val model = remember(snapshot, LocalDate.now()) { modelOf(snapshot) }
            // Answers given on the web should show up too: catch up if the copy is
            // old, in the background so the widget never waits on the network.
            LaunchedEffect(Unit) {
                val current = repository.snapshot.value
                if (current?.owner != null && System.currentTimeMillis() - (current.syncedAt ?: 0L) > STALE_AFTER_MS) {
                    withTimeoutOrNull(REFRESH_TIMEOUT_MS) { repository.refresh() }
                }
            }
            GlanceTheme { Content(context, model) }
        }
    }

    companion object {
        private const val STALE_AFTER_MS = 15 * 60_000L
        private const val REFRESH_TIMEOUT_MS = 15_000L

        /** Redraws every Routines widget on the home screen. */
        suspend fun refreshAll(context: Context) {
            runCatching { RoutinesWidget().updateAll(context) }
        }

        internal fun repository(context: Context): RoutinesRepository =
            EntryPointAccessors.fromApplication(context.applicationContext, RoutinesWidgetEntryPoint::class.java)
                .routinesRepository()

        private fun modelOf(snapshot: RoutinesSnapshot?): WidgetModel {
            if (snapshot == null || snapshot.owner == null) return WidgetModel(signedIn = false)
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

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        scheduleNewDay(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_NEW_DAY) {
            // Past midnight: today's list starts again (and the next one is booked).
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RoutinesWidgetReceiver::class.java))
            if (ids.isNotEmpty()) onUpdate(context, manager, ids)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.getSystemService(AlarmManager::class.java)?.cancel(newDayIntent(context))
    }

    private companion object {
        const val ACTION_NEW_DAY = "com.example.dailytrack_mobile.widget.NEW_DAY"

        fun newDayIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, RoutinesWidgetReceiver::class.java).setAction(ACTION_NEW_DAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /**
         * A minute past midnight, so the morning's widget shows today and not
         * yesterday. Inexact is fine (no alarm permission), and the half-hourly
         * update books it again if it's ever dropped.
         */
        fun scheduleNewDay(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val at = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
                .plusMinutes(1).toInstant().toEpochMilli()
            runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC, at, newDayIntent(context)) }
        }
    }
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
            else -> "$done of $total done"
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
//
// The app's look on the home screen: the launcher's own corners and the
// wallpaper's colours (GlanceTheme), each routine a soft card with its emoji
// badge washed in its answer's colour, and quiet icons that fill in once picked.

/** As in the app (RoutineRow): a heart for done, a tear for missed; skipped stays neutral. */
private val DoneColor = Color(0xFFFF4D6D)
private val MissedColor = Color(0xFF5B8DEF)

private val RowHeight = 52.dp
private val RowGap = 6.dp
private val HeaderRing = 42.dp

@Composable
private fun Content(context: Context, model: WidgetModel) {
    val size = LocalSize.current
    val tiny = size.width < 110.dp && size.height < 110.dp
    WidgetPanel(openApp(context, Routes.Routines.route)) {
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

/** One cell tall: the ring, today, and the next routine to mark done when there's room. */
@Composable
private fun Strip(model: WidgetModel, width: Dp, height: Dp) {
    val next = model.items.firstOrNull { it.status == null }
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Ring(model.consistency, min(height - 16.dp, 56.dp), model.percent)
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Title("Routines")
            Subtitle(model.todayLine)
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
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = if (model.items.isEmpty()) "Nothing due" else "${model.done}/${model.total} today",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            maxLines = 1
        )
        if (height >= 170.dp && model.streak >= 1) {
            Spacer(GlanceModifier.height(4.dp))
            Chip("🔥 ${model.streak}")
        }
    }
}

/** Bigger: a header, then today's routines as cards to answer; the list scrolls once it's tall. */
@Composable
private fun Full(model: WidgetModel, width: Dp, height: Dp) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Ring(model.consistency, HeaderRing, model.percent)
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Title("Routines")
                Subtitle(model.todayLine)
            }
            if (width >= 220.dp && model.streak >= 2) {
                Spacer(GlanceModifier.width(8.dp))
                Chip("🔥 ${model.streak}")
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        when {
            model.items.isEmpty() -> Box(
                modifier = GlanceModifier.fillMaxSize().cornerRadius(CardRadius).background(GlanceTheme.colors.surface),
                contentAlignment = Alignment.Center
            ) {
                Subtitle("Nothing due today. Enjoy it 🌿")
            }
            height >= 230.dp -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(model.items, itemId = { it.id }) { item ->
                    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = RowGap)) {
                        ItemRow(item, width)
                    }
                }
            }
            else -> {
                // As many cards as fit, with "+N more" when some don't.
                val room = height - 24.dp - HeaderRing - 10.dp
                val fits = ((room + RowGap) / (RowHeight + RowGap)).toInt().coerceAtLeast(1)
                val shown = if (model.items.size > fits) model.items.take(fits - 1).ifEmpty { model.items.take(1) } else model.items
                shown.forEachIndexed { i, item ->
                    if (i > 0) Spacer(GlanceModifier.height(RowGap))
                    ItemRow(item, width)
                }
                val hidden = model.items.size - shown.size
                if (hidden > 0) {
                    Text(
                        text = "+$hidden more",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        modifier = GlanceModifier.padding(start = 6.dp, top = 6.dp)
                    )
                }
            }
        }
    }
}

/** A routine as a card: its emoji badge, name and detail, then the answers. */
@Composable
private fun ItemRow(item: WidgetItem, width: Dp) {
    val answered = item.status != null
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(RowHeight)
            .cornerRadius(CardRadius)
            .background(GlanceTheme.colors.surface)
            .padding(start = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EmojiBadge(item.emoji, item.status)
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.name,
                style = TextStyle(
                    color = if (answered) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            if (width >= 240.dp && item.line.isNotEmpty()) {
                Text(
                    text = item.line,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    maxLines = 1
                )
            }
        }
        if (width >= 280.dp) {
            StatusButton(item, CheckInStatus.DONE)
            StatusButton(item, CheckInStatus.MISSED)
            StatusButton(item, CheckInStatus.SKIPPED)
        } else {
            // Narrow: one button, ❤️ until it's answered, then the answer (tap to clear).
            StatusButton(item, item.status ?: CheckInStatus.DONE)
        }
    }
}

/** The routine's own emoji in a circle washed in its answer's colour. */
@Composable
private fun EmojiBadge(emoji: String, status: CheckInStatus?) {
    Box(
        modifier = GlanceModifier
            .size(34.dp)
            .cornerRadius(17.dp)
            .background(
                when (status) {
                    CheckInStatus.DONE -> wash(DoneColor)
                    CheckInStatus.MISSED -> wash(MissedColor)
                    else -> GlanceTheme.colors.secondaryContainer
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, style = TextStyle(fontSize = 16.sp))
    }
}

/** ❤️ / 😭 / ⏭ as quiet icons; the picked one fills in with its colour, and tapping it again clears it. */
@Composable
private fun StatusButton(item: WidgetItem, status: CheckInStatus) {
    val selected = item.status == status
    val color = when (status) {
        CheckInStatus.DONE -> DoneColor
        CheckInStatus.MISSED -> MissedColor
        CheckInStatus.SKIPPED -> null
    }
    val (icon, label) = when (status) {
        CheckInStatus.DONE -> (if (selected) R.drawable.ic_widget_heart else R.drawable.ic_widget_heart_outline) to "Done"
        CheckInStatus.MISSED -> R.drawable.ic_widget_missed to "Missed"
        CheckInStatus.SKIPPED -> R.drawable.ic_widget_skip to "Skip"
    }
    val tint = when {
        selected -> color?.let { ColorProvider(it) } ?: GlanceTheme.colors.onSecondaryContainer
        item.status != null -> GlanceTheme.colors.outline   // another answer is picked
        else -> GlanceTheme.colors.onSurfaceVariant
    }
    // Always set, transparent when not picked: a widget update reapplies only what's
    // set, so a wash left out would stay behind after the answer is cleared.
    val background = when {
        !selected -> ColorProvider(Color.Transparent)
        color != null -> wash(color)
        else -> GlanceTheme.colors.secondaryContainer
    }
    Box(
        modifier = GlanceModifier
            .size(36.dp)
            .cornerRadius(18.dp)
            .background(background)
            .clickable(answer(item.id, if (selected) CLEAR else status.key)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = if (selected) "$label, tap to clear" else label,
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(20.dp)
        )
    }
}

/** The next open routine, ready to be marked done. */
@Composable
private fun NextButton(item: WidgetItem, showName: Boolean) {
    Row(
        modifier = GlanceModifier
            .height(40.dp)
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.secondaryContainer)
            .clickable(answer(item.id, CheckInStatus.DONE.key))
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_heart_outline),
            contentDescription = "Mark ${item.name} done",
            colorFilter = ColorFilter.tint(ColorProvider(DoneColor)),
            modifier = GlanceModifier.size(18.dp)
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(text = item.emoji, style = TextStyle(fontSize = 15.sp))
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

/** The consistency ring, as in the app: green for kept, red for missed, with the percentage inside. */
@Composable
private fun Ring(fraction: Double?, size: Dp, label: String, caption: String? = null) {
    val density = LocalContext.current.resources.displayMetrics.density
    Box(modifier = GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(ringBitmap(fraction, (size.value * density).roundToInt())),
            contentDescription = "Consistency $label",
            modifier = GlanceModifier.size(size)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = (size.value * 0.24f).coerceIn(9f, 26f).sp,
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

private val RingTrack = Color(0x33808080)
private val RingKept = Color(0xFF34C759)
private val RingMissed = Color(0xFFFF453A)

/**
 * Drawn at the size it's shown, so it stays sharp: a soft track, then the kept
 * and missed arcs with rounded ends and a small gap between them.
 */
private fun ringBitmap(fraction: Double?, sizePx: Int): Bitmap {
    val px = sizePx.coerceIn(48, 420)
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = px * 0.1f
    val inset = stroke / 2
    val bounds = RectF(inset, inset, px - inset, px - inset)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    paint.color = RingTrack.toArgb()
    canvas.drawArc(bounds, 0f, 360f, false, paint)
    val kept = fraction?.toFloat()?.coerceIn(0f, 1f) ?: return bitmap

    // A round end reaches past its arc by half the stroke; leave that plus a gap.
    val radius = (px - stroke) / 2
    val capDegrees = Math.toDegrees((stroke / 2 / radius).toDouble()).toFloat()
    val gap = 3f
    fun arc(color: Color, start: Float, sweep: Float) {
        paint.color = color.toArgb()
        if (sweep >= 359.9f) {
            canvas.drawArc(bounds, start, 360f, false, paint)
            return
        }
        val trimmed = sweep - 2 * capDegrees - gap
        // Too short to trim: a dot still shows it's there.
        canvas.drawArc(bounds, start + capDegrees + gap / 2, trimmed.coerceAtLeast(0.1f), false, paint)
    }
    if (kept > 0f) arc(RingKept, -90f, 360f * kept)
    if (kept < 1f) arc(RingMissed, -90f + 360f * kept, 360f * (1f - kept))
    return bitmap
}
