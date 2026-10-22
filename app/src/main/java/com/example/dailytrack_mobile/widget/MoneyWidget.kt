package com.example.dailytrack_mobile.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.dailytrack_mobile.data.repository.MoneyRepository
import com.example.dailytrack_mobile.data.repository.isCcAccount
import com.example.dailytrack_mobile.presentation.navigation.Routes
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

/**
 * This month's budgets on the home screen: how much of them is used, each one
 * as a bar, and the cards' monthly budgets under them. Percentages only — the
 * amounts stay in the app. A tap opens Budgets.
 */
class MoneyWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val start = DailyTrackWidgets.moneyVersion.value
        val initial = load(context)
        provideContent {
            // Reloads when the app saves something, even while the widget is open.
            val model = rememberReloading(initial, start) { load(context) }
            GlanceTheme { Content(context, model) }
        }
    }

    private companion object {
        const val CACHE_KEY = "money"
        const val LOAD_TIMEOUT_MS = 8_000L

        suspend fun load(context: Context): MoneyModel {
            val deps = widgetDeps(context)
            if (deps.authManager().userEmailFlow.first() == null) return MoneyModel(MoneyModel.Status.SIGNED_OUT)
            val cache = WidgetCache(context)
            val fresh = withTimeoutOrNull(LOAD_TIMEOUT_MS) { fetch(deps.moneyRepository()) }
            if (fresh != null) {
                cache.write(CACHE_KEY, fresh.toJson())
                return fresh
            }
            // No signal: the last look, if it's still this month's.
            return cache.read(CACHE_KEY)?.let { MoneyModel.fromJson(it) }
                ?.takeIf { it.month == monthKey(LocalDate.now()) }
                ?.copy(daysLeft = daysLeft(LocalDate.now()))
                ?: MoneyModel(MoneyModel.Status.UNAVAILABLE)
        }

        suspend fun fetch(repo: MoneyRepository): MoneyModel? = coroutineScope {
            val today = LocalDate.now()
            val month = monthKey(today)
            val budgetsCall = async { repo.getBudgets(forceRefresh = true) }
            val txCall = async { repo.getTransactions(limit = 1000, month = month, forceRefresh = true) }
            val accountsCall = async { repo.getAccounts(forceRefresh = true) }
            val budgets = budgetsCall.await().getOrNull() ?: return@coroutineScope null
            val transactions = txCall.await().getOrNull()?.transactions ?: return@coroutineScope null
            val accounts = accountsCall.await().getOrNull().orEmpty()

            // Spent as the budgets count it: expenses this month, not what's left
            // out of the spending analyser, and not money moved to savings.
            val spent = transactions
                .filter {
                    it.type.equals("Debit", ignoreCase = true) && !it.excludeAnalytics && it.date.startsWith(month) &&
                        !it.heading.equals("Savings", ignoreCase = true) && !it.heading.equals("Saving", ignoreCase = true)
                }
                .groupBy { it.heading }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
            val limits = budgets.filter { it.monthlyLimit > 0.0 }
            val lines = limits
                .map { MoneyLine(it.category, ((spent[it.category] ?: 0.0) / it.monthlyLimit).toFloat()) }
                .sortedWith(compareByDescending<MoneyLine> { it.fraction > 1f }.thenByDescending { it.fraction })
            val overall = if (limits.isEmpty()) null
                else (limits.sumOf { spent[it.category] ?: 0.0 } / limits.sumOf { it.monthlyLimit }).toFloat()
            val cards = accounts
                .filter { isCcAccount(it.account) && (it.monthlyBudget ?: 0.0) > 0.0 }
                .map { card ->
                    val used = card.budgetUsedThisMonth ?: card.usedThisMonth ?: 0.0
                    MoneyLine(card.account.removePrefix("CC-").trim(), (used / card.monthlyBudget!!).toFloat())
                }
                .sortedByDescending { it.fraction }
            MoneyModel(
                status = MoneyModel.Status.OK,
                month = month,
                monthLabel = today.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()),
                daysLeft = daysLeft(today),
                overall = overall,
                budgets = lines,
                cards = cards
            )
        }

        fun monthKey(day: LocalDate) = String.format(Locale.US, "%04d-%02d", day.year, day.monthValue)
        fun daysLeft(day: LocalDate) = day.lengthOfMonth() - day.dayOfMonth
    }
}

class MoneyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MoneyWidget()
}

/** One budget (or card budget): its name and how much of it is used, 1 being all of it. */
internal data class MoneyLine(val name: String, val fraction: Float)

internal data class MoneyModel(
    val status: Status,
    val month: String = "",
    val monthLabel: String = "",
    val daysLeft: Int = 0,
    /** All budgets together; null when none is set. */
    val overall: Float? = null,
    /** Over first, then fullest first. */
    val budgets: List<MoneyLine> = emptyList(),
    val cards: List<MoneyLine> = emptyList()
) {
    enum class Status { OK, SIGNED_OUT, UNAVAILABLE }

    /** What the ring shows: all budgets, or the fullest card when only cards have one. */
    val headline: Float? get() = overall ?: cards.firstOrNull()?.fraction

    val daysLine: String get() = when (daysLeft) {
        0 -> "Last day"
        1 -> "1 day left"
        else -> "$daysLeft days left"
    }

    fun toJson(): String = JSONObject()
        .put("month", month)
        .put("monthLabel", monthLabel)
        .put("daysLeft", daysLeft)
        .put("overall", overall?.toDouble() ?: JSONObject.NULL)
        .put("budgets", lines(budgets))
        .put("cards", lines(cards))
        .toString()

    companion object {
        private fun lines(list: List<MoneyLine>) = JSONArray().apply {
            list.forEach { put(JSONObject().put("name", it.name).put("fraction", it.fraction.toDouble())) }
        }

        private fun lines(array: JSONArray?) = (0 until (array?.length() ?: 0)).map { i ->
            val o = array!!.getJSONObject(i)
            MoneyLine(o.getString("name"), o.getDouble("fraction").toFloat())
        }

        fun fromJson(json: String): MoneyModel? = runCatching {
            val o = JSONObject(json)
            MoneyModel(
                status = Status.OK,
                month = o.optString("month"),
                monthLabel = o.optString("monthLabel"),
                daysLeft = o.optInt("daysLeft"),
                overall = if (o.isNull("overall")) null else o.getDouble("overall").toFloat(),
                budgets = lines(o.optJSONArray("budgets")),
                cards = lines(o.optJSONArray("cards"))
            )
        }.getOrNull()
    }
}

// ── Layout ───────────────────────────────────────────────────────────────────

private val LineHeight = 46.dp
private val LineGap = 6.dp
private val LabelHeight = 22.dp
private val HeaderRing = 42.dp

/** A row of the list: a section label or a budget. */
private sealed interface MoneyEntry {
    data class Label(val text: String) : MoneyEntry
    data class Line(val line: MoneyLine) : MoneyEntry
}

@Composable
private fun Content(context: Context, model: MoneyModel) {
    val size = LocalSize.current
    val tiny = size.width < 110.dp && size.height < 110.dp
    WidgetPanel(openApp(context, Routes.Budgets.route)) {
        val headline = model.headline
        when {
            model.status == MoneyModel.Status.SIGNED_OUT -> Message("🔒", "Open DailyTrack to sign in")
            model.status == MoneyModel.Status.UNAVAILABLE -> Message("💸", "Open DailyTrack to load your budgets")
            headline == null -> Message("🎯", "Set a budget in DailyTrack")
            tiny -> HeadlineRing(headline, min(size.width, size.height) - 12.dp)
            size.height < 110.dp -> Strip(model, headline, size.width, size.height)
            size.width < 180.dp && size.height < 220.dp -> Compact(model, headline, size.width, size.height)
            else -> Full(model, headline, size.width, size.height)
        }
    }
}

@Composable
private fun HeadlineRing(fraction: Float, size: Dp, caption: String? = null) =
    ProgressRing(fraction, levelColor(fraction), size, percent(fraction), caption)

/** One cell tall: the ring, the month, and the budget most in trouble when there's room. */
@Composable
private fun Strip(model: MoneyModel, headline: Float, width: Dp, height: Dp) {
    val worst = model.budgets.firstOrNull()?.takeIf { it.fraction >= 0.8f }
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        HeadlineRing(headline, min(height - 16.dp, 56.dp))
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Title("Budgets")
            Subtitle("${model.monthLabel} · ${model.daysLine}")
        }
        if (worst != null && width >= 250.dp) {
            Spacer(GlanceModifier.width(8.dp))
            Chip("${worst.name} ${percent(worst.fraction)}", tint = levelColor(worst.fraction))
        }
    }
}

/** A small square: the ring big, and the days left under it. */
@Composable
private fun Compact(model: MoneyModel, headline: Float, width: Dp, height: Dp) {
    val ring = min(width - 16.dp, height - 52.dp).coerceIn(40.dp, 120.dp)
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeadlineRing(headline, ring, caption = if (ring >= 76.dp) "used" else null)
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = model.monthLabel,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            maxLines = 1
        )
        Subtitle(model.daysLine)
    }
}

/** Bigger: the header, then each budget as a card with its bar; cards' budgets after them. */
@Composable
private fun Full(model: MoneyModel, headline: Float, width: Dp, height: Dp) {
    val entries = buildList<MoneyEntry> {
        model.budgets.forEach { add(MoneyEntry.Line(it)) }
        if (model.cards.isNotEmpty()) {
            if (model.budgets.isNotEmpty()) add(MoneyEntry.Label("Cards"))
            model.cards.forEach { add(MoneyEntry.Line(it)) }
        }
    }
    val lineWidth = width - 24.dp
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeadlineRing(headline, HeaderRing)
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Title(if (model.overall != null) "Budgets" else "Card budgets")
                Subtitle("Spent $MASKED · ${model.daysLine}")
            }
            if (width >= 230.dp) {
                Spacer(GlanceModifier.width(8.dp))
                Chip(model.monthLabel)
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        if (height >= 300.dp) {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(entries) { entry ->
                    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = LineGap)) {
                        Entry(entry, lineWidth)
                    }
                }
            }
        } else {
            // As many as fit, with "+N more" when some don't.
            var room = height - 24.dp - HeaderRing - 10.dp
            val shown = mutableListOf<MoneyEntry>()
            for (entry in entries) {
                val need = (if (entry is MoneyEntry.Label) LabelHeight else LineHeight) + LineGap
                if (need > room + LineGap) break
                shown += entry
                room -= need
            }
            if (shown.lastOrNull() is MoneyEntry.Label) shown.removeAt(shown.lastIndex)
            val hidden = entries.count { it is MoneyEntry.Line } - shown.count { it is MoneyEntry.Line }
            if (hidden > 0 && shown.size > 1 && room < 18.dp) shown.removeAt(shown.lastIndex)
            shown.forEachIndexed { i, entry ->
                if (i > 0) Spacer(GlanceModifier.height(LineGap))
                Entry(entry, lineWidth)
            }
            val stillHidden = entries.count { it is MoneyEntry.Line } - shown.count { it is MoneyEntry.Line }
            if (stillHidden > 0) {
                Text(
                    text = "+$stillHidden more",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    modifier = GlanceModifier.padding(start = 6.dp, top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun Entry(entry: MoneyEntry, width: Dp) = when (entry) {
    is MoneyEntry.Label -> Text(
        text = entry.text,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        modifier = GlanceModifier.height(LabelHeight).padding(start = 6.dp, top = 4.dp)
    )
    is MoneyEntry.Line -> LineCard(entry.line, width)
}

/** A budget as a card: its name and percentage, and a bar coloured by how full it is. */
@Composable
private fun LineCard(line: MoneyLine, width: Dp) {
    val color = levelColor(line.fraction)
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(LineHeight)
            .cornerRadius(CardRadius)
            .background(GlanceTheme.colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = line.name,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = if (line.fraction > 1f) "Over · ${percent(line.fraction)}" else percent(line.fraction),
                style = TextStyle(
                    color = if (line.fraction >= 0.8f) ColorProvider(color) else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        Bar(line.fraction, width - 24.dp, color)
    }
}
