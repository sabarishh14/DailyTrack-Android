package com.example.dailytrack_mobile.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.dailytrack_mobile.data.remote.dto.PortfolioSnapshotDto
import com.example.dailytrack_mobile.presentation.navigation.Routes
import com.example.dailytrack_mobile.presentation.screens.invest.ChartPoint
import com.example.dailytrack_mobile.presentation.screens.invest.ChartTimeRange
import com.example.dailytrack_mobile.presentation.screens.invest.InvestCategory
import com.example.dailytrack_mobile.presentation.screens.invest.InvestColors
import com.example.dailytrack_mobile.presentation.screens.invest.cutoff
import com.example.dailytrack_mobile.presentation.screens.invest.periodReturn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The portfolio on the home screen: its return overall and over the last three
 * months, and the shape of its value over them. Percentages and a line only —
 * the value itself stays in the app. Follows the categories hidden there. A tap
 * opens Invest.
 */
class InvestmentsWidget : GlanceAppWidget() {

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
        const val CACHE_KEY = "investments"
        const val LOAD_TIMEOUT_MS = 10_000L
        val RANGE = ChartTimeRange.THREE_MONTHS

        suspend fun load(context: Context): InvestModel {
            val deps = widgetDeps(context)
            if (deps.authManager().userEmailFlow.first() == null) return InvestModel(InvestModel.Status.SIGNED_OUT)
            val cache = WidgetCache(context)
            val hidden = deps.investPreferences().getInitialHiddenCategories()
            val fresh = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                deps.investmentsRepository().getFullPortfolio(forceRefresh = true).getOrNull()
            }?.let { data ->
                // Holdings as the app sorts them into its categories (InvestVM).
                val holdings = data.equityHoldings.map { Triple(InvestCategory.STOCKS, it.investedValue, it.currentValue) } +
                    data.mutualFundHoldings.map { Triple(InvestCategory.MUTUAL_FUNDS, it.investedValue, it.currentValue) } +
                    data.manualAssets.map { Triple(categoryOf(it.category), it.investedValue, it.currentValue) }
                val shown = holdings.filter { it.first !in hidden }
                build(shown.sumOf { it.second }, shown.sumOf { it.third }, data.snapshots, hidden)
            }
            if (fresh != null) {
                cache.write(CACHE_KEY, fresh.toJson())
                return fresh
            }
            return cache.read(CACHE_KEY)?.let { InvestModel.fromJson(it) } ?: InvestModel(InvestModel.Status.UNAVAILABLE)
        }

        fun build(invested: Double, current: Double, snapshots: List<PortfolioSnapshotDto>, hidden: Set<InvestCategory>): InvestModel {
            if (invested <= 0.0 && current <= 0.0) return InvestModel(InvestModel.Status.EMPTY)
            val from = RANGE.cutoff()?.toString()
            // Oldest first, the visible categories added up: the Overview chart's line.
            val points = snapshots.reversed().mapNotNull { s ->
                val date = s.date.substringBefore("T")
                if (from != null && date < from) return@mapNotNull null
                fun part(cat: InvestCategory, value: Double?) = if (cat in hidden) 0.0 else value ?: 0.0
                val curr = part(InvestCategory.STOCKS, s.currStocks) + part(InvestCategory.MUTUAL_FUNDS, s.currMf) +
                    part(InvestCategory.RETIREMENT, s.currProv) + part(InvestCategory.FD, s.currFixed) + part(InvestCategory.GOLD, s.currGold)
                val inv = part(InvestCategory.STOCKS, s.invStocks) + part(InvestCategory.MUTUAL_FUNDS, s.invMf) +
                    part(InvestCategory.RETIREMENT, s.invProv) + part(InvestCategory.FD, s.invFixed) + part(InvestCategory.GOLD, s.invGold)
                if (curr > 0.0) ChartPoint(date, inv.toFloat(), curr.toFloat()) else null
            }
            val overall = if (invested > 0.0) (current - invested) / invested * 100.0 else null
            val period = if (points.size >= 2) periodReturn(points, RANGE, invested, current).second else null
            val asOf = snapshots.firstOrNull()?.date?.substringBefore("T")?.let {
                runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) }.getOrNull()
            }.orEmpty()
            return InvestModel(InvestModel.Status.OK, overall, period, points.map { it.current }, asOf)
        }

        fun categoryOf(raw: String): InvestCategory = when (raw.uppercase()) {
            "FD", "RD", "CASH" -> InvestCategory.FD
            "EPF", "PPF", "NPS" -> InvestCategory.RETIREMENT
            "SGB", "GOLD" -> InvestCategory.GOLD
            "REALESTATE" -> InvestCategory.REAL_ESTATE
            else -> InvestCategory.STOCKS
        }
    }
}

class InvestmentsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = InvestmentsWidget()
}

internal data class InvestModel(
    val status: Status,
    /** Total return, %. */
    val overall: Double? = null,
    /** Gained over the last three months, %, counted as the portfolio header does. */
    val period: Double? = null,
    /** The portfolio's value over those months, oldest first: drawn as a shape, never shown as numbers. */
    val series: List<Float> = emptyList(),
    /** The last snapshot's day, "18 Oct". */
    val asOf: String = ""
) {
    enum class Status { OK, EMPTY, SIGNED_OUT, UNAVAILABLE }

    fun toJson(): String = JSONObject()
        .put("overall", overall ?: JSONObject.NULL)
        .put("period", period ?: JSONObject.NULL)
        .put("series", JSONArray().apply { series.forEach { put(it.toDouble()) } })
        .put("asOf", asOf)
        .toString()

    companion object {
        fun fromJson(json: String): InvestModel? = runCatching {
            val o = JSONObject(json)
            val series = o.optJSONArray("series")
            InvestModel(
                status = Status.OK,
                overall = if (o.isNull("overall")) null else o.getDouble("overall"),
                period = if (o.isNull("period")) null else o.getDouble("period"),
                series = (0 until (series?.length() ?: 0)).map { series!!.getDouble(it).toFloat() },
                asOf = o.optString("asOf")
            )
        }.getOrNull()
    }
}

// ── Layout ───────────────────────────────────────────────────────────────────

private fun gainColor(value: Double?) = if ((value ?: 0.0) >= 0.0) InvestColors.GainGreen else InvestColors.LossRed

@Composable
private fun Content(context: Context, model: InvestModel) {
    val size = LocalSize.current
    val tiny = size.width < 110.dp && size.height < 110.dp
    WidgetPanel(openApp(context, Routes.Investments.route)) {
        when (model.status) {
            InvestModel.Status.SIGNED_OUT -> Message("🔒", "Open DailyTrack to sign in")
            InvestModel.Status.UNAVAILABLE -> Message("📈", "Open DailyTrack to load your portfolio")
            InvestModel.Status.EMPTY -> Message("🌱", "Add an investment in DailyTrack")
            InvestModel.Status.OK -> when {
                tiny -> Tiny(model)
                size.height < 110.dp -> Strip(model, size.width, size.height)
                else -> Full(model, size.width, size.height)
            }
        }
    }
}

@Composable
private fun BigReturn(model: InvestModel, fontSize: Float) = Text(
    text = model.overall?.let { signedPercent(it) } ?: "—",
    style = TextStyle(color = ColorProvider(gainColor(model.overall)), fontSize = fontSize.sp, fontWeight = FontWeight.Bold),
    maxLines = 1
)

/** "+3.2% · 3M", washed green or red. */
@Composable
private fun PeriodChip(model: InvestModel) {
    val period = model.period ?: return
    Chip("${signedPercent(period)} · 3M", tint = gainColor(period))
}

/** One cell: the overall return, big. */
@Composable
private fun Tiny(model: InvestModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        BigReturn(model, 17f)
        Text(text = "overall", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp), maxLines = 1)
    }
}

/** One cell tall: the name, the line when there's room, and the return. */
@Composable
private fun Strip(model: InvestModel, width: Dp, height: Dp) {
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Title("Portfolio")
            Subtitle(if (width >= 200.dp) "Value $MASKED" else MASKED)
        }
        if (width >= 250.dp && model.series.size >= 2) {
            Sparkline(model.series, gainColor(model.period), 84.dp, (height - 36.dp).coerceIn(20.dp, 44.dp))
            Spacer(GlanceModifier.width(12.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            BigReturn(model, 20f)
            Subtitle("overall")
        }
    }
}

/** Bigger: the header, the return big, then the last three months as a line. */
@Composable
private fun Full(model: InvestModel, width: Dp, height: Dp) {
    val narrow = width < 180.dp
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Title("Portfolio")
                Subtitle(if (narrow) MASKED else listOf("Value $MASKED", model.asOf).filter { it.isNotEmpty() }.joinToString(" · "))
            }
            if (!narrow) PeriodChip(model)
        }
        Spacer(GlanceModifier.height(8.dp))
        BigReturn(model, if (narrow) 24f else 30f)
        Subtitle(if (narrow) "overall" else "overall return")
        // Whatever height is left goes to the line.
        val lineHeight = height - 24.dp - 36.dp - 8.dp - 54.dp - 10.dp
        if (lineHeight >= 24.dp && model.series.size >= 2) {
            Spacer(GlanceModifier.height(10.dp))
            Sparkline(model.series, gainColor(model.period), width - 24.dp, lineHeight)
        } else if (narrow) {
            Spacer(GlanceModifier.height(6.dp))
            PeriodChip(model)
        }
    }
}
