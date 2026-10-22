package com.example.dailytrack_mobile.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
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
import com.example.dailytrack_mobile.R
import com.example.dailytrack_mobile.presentation.components.transaction.EntryType
import com.example.dailytrack_mobile.presentation.navigation.Routes
import com.example.dailytrack_mobile.presentation.screens.money.CategoryEmojis
import kotlinx.coroutines.flow.first

/**
 * Adding a transaction from the home screen: a big + opens the Add screen, and
 * the categories used most open it with that category already picked. Nothing
 * here is private, so it shows in full.
 */
class QuickAddWidget : GlanceAppWidget() {

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
        const val MAX_CATEGORIES = 10

        /** From what the phone already knows: no network, so it's instant and works offline. */
        suspend fun load(context: Context): QuickAddModel {
            val deps = widgetDeps(context)
            if (deps.authManager().userEmailFlow.first() == null) return QuickAddModel(signedIn = false)
            val repo = deps.moneyRepository()
            val categories = repo.entryHistory()
                .categoriesFor(EntryType.EXPENSE, fallback = repo.getCachedCategories())
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .take(MAX_CATEGORIES)
            return QuickAddModel(signedIn = true, categories = categories)
        }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

internal data class QuickAddModel(
    val signedIn: Boolean,
    /** Expense categories, most used first. */
    val categories: List<String> = emptyList()
)

// ── Layout ───────────────────────────────────────────────────────────────────

private val ChipHeight = 38.dp
private val ChipGap = 8.dp
private val PillHeight = 44.dp

/** Roughly how wide a chip will be: Glance can't measure, and can't wrap a row. */
private fun chipWidth(category: String): Dp = (category.length * 7.5f + 52f).dp

@Composable
private fun Content(context: Context, model: QuickAddModel) {
    val size = LocalSize.current
    val tiny = size.width < 110.dp && size.height < 110.dp
    val add = openApp(context, Routes.AddMoney.route)
    WidgetPanel(add) {
        when {
            !model.signedIn -> Message("🔒", "Open DailyTrack to sign in")
            tiny -> AddCircle(context, min(size.width, size.height) - 12.dp)
            size.height < 110.dp -> Strip(context, model, size.width)
            else -> Full(context, model, size.width, size.height)
        }
    }
}

/** One cell: a round + in the theme's main colour. */
@Composable
private fun AddCircle(context: Context, size: Dp) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size / 2)
            .background(GlanceTheme.colors.primary)
            .clickable(openApp(context, Routes.AddMoney.route)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_add),
            contentDescription = "Add a transaction",
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
            modifier = GlanceModifier.size(size * 0.45f)
        )
    }
}

/** "+ Add" as a filled pill; just the + when [compact]. */
@Composable
private fun AddPill(context: Context, compact: Boolean) {
    Row(
        modifier = GlanceModifier
            .height(PillHeight)
            .cornerRadius(PillHeight / 2)
            .background(GlanceTheme.colors.primary)
            .clickable(openApp(context, Routes.AddMoney.route))
            .padding(horizontal = if (compact) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_add),
            contentDescription = "Add a transaction",
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
            modifier = GlanceModifier.size(20.dp)
        )
        if (!compact) {
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = "Add",
                style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
    }
}

/** A category as a tonal chip; a tap opens Add with it picked. */
@Composable
private fun CategoryChip(context: Context, category: String) {
    Row(
        modifier = GlanceModifier
            .height(ChipHeight)
            .cornerRadius(ChipHeight / 2)
            .background(GlanceTheme.colors.secondaryContainer)
            .clickable(openApp(context, Routes.AddMoney.route, mapOf("category" to category), key = "add:$category"))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${CategoryEmojis.forCategory(category)} $category",
            style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1
        )
    }
}

/** The chips that fit in [width], in order. */
private fun fitting(categories: List<String>, width: Dp): List<String> {
    var left = width
    val out = mutableListOf<String>()
    for (c in categories) {
        val need = chipWidth(c) + if (out.isEmpty()) 0.dp else ChipGap
        if (need > left) break
        out += c
        left -= need
    }
    return out
}

/** One cell tall: the + then as many categories as fit beside it. */
@Composable
private fun Strip(context: Context, model: QuickAddModel, width: Dp) {
    val compact = width < 200.dp
    val pill = if (compact) 44.dp else 92.dp
    val chips = fitting(model.categories, width - 24.dp - pill - ChipGap)
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        AddPill(context, compact)
        chips.forEach { category ->
            Spacer(GlanceModifier.width(ChipGap))
            CategoryChip(context, category)
        }
    }
}

/** Bigger: a header with the +, then the categories in as many rows as fit. */
@Composable
private fun Full(context: Context, model: QuickAddModel, width: Dp, height: Dp) {
    val rowsRoom = height - 24.dp - PillHeight - 12.dp
    val maxRows = ((rowsRoom + ChipGap) / (ChipHeight + ChipGap)).toInt().coerceAtLeast(0)
    // Fill rows one after another, each with the chips that fit.
    val rows = mutableListOf<List<String>>()
    var rest = model.categories
    while (rest.isNotEmpty() && rows.size < maxRows) {
        val row = fitting(rest, width - 24.dp).ifEmpty { listOf(rest.first()) }
        rows += row
        rest = rest.drop(row.size)
    }
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Title("Quick add")
                Subtitle(if (rows.isEmpty()) "Add a transaction" else "Tap a category")
            }
            Spacer(GlanceModifier.width(8.dp))
            AddPill(context, compact = width < 200.dp)
        }
        rows.forEach { row ->
            Spacer(GlanceModifier.height(if (row === rows.first()) 12.dp else ChipGap))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                row.forEachIndexed { i, category ->
                    if (i > 0) Spacer(GlanceModifier.width(ChipGap))
                    CategoryChip(context, category)
                }
            }
        }
    }
}
