package com.example.dailytrack_mobile.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColor
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.dailytrack_mobile.MainActivity
import com.example.dailytrack_mobile.R
import com.example.dailytrack_mobile.data.local.auth.AuthManager
import com.example.dailytrack_mobile.data.local.datastore.InvestPreferencesManager
import com.example.dailytrack_mobile.data.repository.InvestmentsRepository
import com.example.dailytrack_mobile.data.repository.MoneyRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// What every DailyTrack widget shares: the launcher's corners and the
// wallpaper's colours (GlanceTheme), soft cards, and the same few pieces of
// type. Money and investments show percentages and shapes only, never amounts:
// a widget is on show to anyone who picks up the phone.
// ─────────────────────────────────────────────────────────────────────────────

/** Every widget, redrawn: after a save, a sign-in or a sign-out. */
object DailyTrackWidgets {
    /**
     * Moves on every change the money widgets show. Glance keeps a widget's
     * session open for a while and only redraws it on an update, so an open
     * one reloads when this moves ([rememberReloading]) instead of redrawing
     * what it read when it opened.
     */
    internal val moneyVersion = MutableStateFlow(0L)

    suspend fun refreshAll(context: Context) {
        RoutinesWidget.refreshAll(context)
        refreshMoney(context)
    }

    /** The money widgets only, after a transaction, budget or holding changes. */
    suspend fun refreshMoney(context: Context) {
        moneyVersion.update { it + 1 }
        listOf(MoneyWidget(), InvestmentsWidget(), QuickAddWidget()).forEach { runCatching { it.updateAll(context) } }
    }
}

/**
 * [initial], then [reload]ed whenever the money widgets' data changes while
 * this widget is open; [startVersion] is the version [initial] was read at.
 */
@Composable
internal fun <T> rememberReloading(initial: T, startVersion: Long, reload: suspend () -> T): T {
    val version by DailyTrackWidgets.moneyVersion.collectAsState()
    var value by remember { mutableStateOf(initial) }
    LaunchedEffect(version) {
        if (version != startVersion) value = reload()
    }
    return value
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetsEntryPoint {
    fun moneyRepository(): MoneyRepository
    fun investmentsRepository(): InvestmentsRepository
    fun investPreferences(): InvestPreferencesManager
    fun authManager(): AuthManager
}

internal fun widgetDeps(context: Context): WidgetsEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetsEntryPoint::class.java)

/**
 * The last good look of each widget, so a phone without signal still shows
 * something. Only percentages and names are kept, never amounts.
 */
internal class WidgetCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
    fun read(key: String): String? = prefs.getString(key, null)
    fun write(key: String, value: String) = prefs.edit().putString(key, value).apply()

    companion object {
        /** Someone else is signing in: nothing of the last person's may show. */
        fun clear(context: Context) =
            context.applicationContext.getSharedPreferences("widget_cache", Context.MODE_PRIVATE).edit().clear().apply()
    }
}

// ── Colours ──────────────────────────────────────────────────────────────────

/** Under the warning line, near it, over it: as the app's budgets and cards read. */
internal val GoodColor = Color(0xFF34C759)
internal val WarnColor = Color(0xFFFFB020)
internal val BadColor = Color(0xFFFF453A)

internal val CardRadius = 16.dp

/** Where an amount would be: hidden, for now, on the home screen. */
internal const val MASKED = "₹ ••••"

/** A status colour washed over the theme, a touch stronger at night. */
internal fun wash(color: Color): ColorProvider =
    DayNightColor(day = color.copy(alpha = 0.16f), night = color.copy(alpha = 0.28f))

/** How full a budget is: green, amber from 80%, red once over. */
internal fun levelColor(fraction: Float): Color = when {
    fraction > 1f -> BadColor
    fraction >= 0.8f -> WarnColor
    else -> GoodColor
}

internal fun percent(fraction: Float): String = "${(fraction * 100).roundToInt()}%"

/** "+12.4%" / "−3.0%", one decimal. */
internal fun signedPercent(value: Double): String =
    (if (value >= 0) "+" else "−") + String.format(java.util.Locale.US, "%.1f", kotlin.math.abs(value)) + "%"

// ── Opening the app ──────────────────────────────────────────────────────────

/**
 * Opens the app on [route]. [key] tells otherwise-alike intents apart (one per
 * category chip), or the launcher would hand every chip the same one.
 */
internal fun openApp(context: Context, route: String, extras: Map<String, String> = emptyMap(), key: String = route): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setData(Uri.parse("dailytrack://widget/" + Uri.encode(key)))
            .putExtra("route", route)
            .apply { extras.forEach { (k, v) -> putExtra(k, v) } }
    )

// ── Pieces ───────────────────────────────────────────────────────────────────

/** The panel every widget sits on: rounded like the launcher's, a tap opens [open]. */
@Composable
internal fun WidgetPanel(open: Action, content: @Composable () -> Unit) {
    val size = LocalSize.current
    val tiny = size.width < 110.dp && size.height < 110.dp
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(R.dimen.widget_radius)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(open)
            .padding(if (tiny) 6.dp else 12.dp),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
internal fun Title(text: String) = Text(
    text = text,
    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
    maxLines = 1
)

@Composable
internal fun Subtitle(text: String) = Text(
    text = text,
    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
    maxLines = 1
)

/** A small label in a pill; [tint] washes it in a status colour instead of the theme's. */
@Composable
internal fun Chip(text: String, tint: Color? = null) {
    Box(
        modifier = GlanceModifier
            .cornerRadius(12.dp)
            .background(tint?.let { wash(it) } ?: GlanceTheme.colors.secondaryContainer)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = tint?.let { ColorProvider(it) } ?: GlanceTheme.colors.onSecondaryContainer,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

/** A short centred note: signed out, nothing yet, or no signal and nothing kept. */
@Composable
internal fun Message(emoji: String, text: String) {
    val size = LocalSize.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Text(text = emoji, style = TextStyle(fontSize = 26.sp))
        if (size.width >= 110.dp && size.height >= 90.dp) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = text,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 2
            )
        }
    }
}

/** A rounded bar [width] wide, filled to [fraction] in [color]. */
@Composable
internal fun Bar(fraction: Float, width: Dp, color: Color, height: Dp = 6.dp) {
    val fill = width * fraction.coerceIn(0f, 1f)
    Box(
        modifier = GlanceModifier
            .width(width)
            .height(height)
            .cornerRadius(height / 2)
            .background(GlanceTheme.colors.surfaceVariant)
    ) {
        // Always there (none wide at 0%): a widget update reapplies onto the old
        // views, so a fill that came and went could be left showing.
        Box(
            modifier = GlanceModifier
                .width(if (fraction > 0f) max(fill, height) else 0.dp)
                .height(height)
                .cornerRadius(height / 2)
                .background(ColorProvider(if (fraction > 0f) color else Color.Transparent))
        ) {}
    }
}

/** A ring filled to [fraction] in one colour, with [label] (and [caption]) inside. */
@Composable
internal fun ProgressRing(fraction: Float, color: Color, size: Dp, label: String, caption: String? = null) {
    val density = LocalContext.current.resources.displayMetrics.density
    Box(modifier = GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(progressRingBitmap(fraction, color, (size.value * density).roundToInt())),
            contentDescription = label,
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
                Text(text = caption, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp), maxLines = 1)
            }
        }
    }
}

/** A line of [values] with a soft fill under it, [width]×[height]: the shape only, no axes or amounts. */
@Composable
internal fun Sparkline(values: List<Float>, color: Color, width: Dp, height: Dp) {
    val density = LocalContext.current.resources.displayMetrics.density
    if (values.size < 2 || width <= 0.dp || height <= 0.dp) return
    Image(
        provider = ImageProvider(
            sparklineBitmap(values, color, (width.value * density).roundToInt(), (height.value * density).roundToInt(), density)
        ),
        contentDescription = null,
        modifier = GlanceModifier.width(width).height(height)
    )
}

// ── Drawing ──────────────────────────────────────────────────────────────────

private val TrackColor = Color(0x33808080)

private fun progressRingBitmap(fraction: Float, color: Color, sizePx: Int): Bitmap {
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
    paint.color = TrackColor.toArgb()
    canvas.drawArc(bounds, 0f, 360f, false, paint)
    val sweep = 360f * fraction.coerceIn(0f, 1f)
    if (sweep > 0f) {
        paint.color = color.toArgb()
        canvas.drawArc(bounds, -90f, sweep, false, paint)
    }
    return bitmap
}

private fun sparklineBitmap(values: List<Float>, color: Color, widthPx: Int, heightPx: Int, density: Float): Bitmap {
    val w = widthPx.coerceIn(16, 1400)
    val h = heightPx.coerceIn(8, 700)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = 2.2f * density
    val pad = stroke
    val min = values.min()
    val max = values.max()
    val span = (max - min).takeIf { it > 0f } ?: 1f
    val step = (w - 2 * pad) / (values.size - 1)
    fun x(i: Int) = pad + i * step
    fun y(v: Float) = pad + (1f - (v - min) / span) * (h - 2 * pad)

    val line = Path().apply {
        moveTo(x(0), y(values[0]))
        for (i in 1 until values.size) lineTo(x(i), y(values[i]))
    }
    val area = Path(line).apply {
        lineTo(x(values.lastIndex), h.toFloat())
        lineTo(x(0), h.toFloat())
        close()
    }
    canvas.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = LinearGradient(0f, 0f, 0f, h.toFloat(), color.copy(alpha = 0.28f).toArgb(), color.copy(alpha = 0f).toArgb(), Shader.TileMode.CLAMP)
    })
    canvas.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color.toArgb()
    })
    canvas.drawCircle(x(values.lastIndex), y(values.last()), stroke * 1.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() })
    return bitmap
}
