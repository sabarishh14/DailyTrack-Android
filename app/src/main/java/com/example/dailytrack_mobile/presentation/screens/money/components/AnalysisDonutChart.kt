package com.example.dailytrack_mobile.presentation.screens.money.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import com.example.dailytrack_mobile.presentation.components.MonthYearPickerDialog
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.dailytrack_mobile.presentation.screens.money.*
import com.example.dailytrack_mobile.presentation.theme.AppTheme
import com.example.dailytrack_mobile.presentation.theme.DtOgChartColors
import com.example.dailytrack_mobile.presentation.theme.LocalAppTheme
import com.example.dailytrack_mobile.presentation.util.Dimens


// ─────────────────────────────────────────────────────────────────────────────
// Donut Chart (Canvas: Segmented 2D for DT_OG, standard 2D for other themes)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun DonutChart(
    categories: List<SpendingCategory>,
    total: Double,
    isDtOgStyle: Boolean = false,
    centerTitle: String = "TOTAL",
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { 8.dp.toPx() }
    val sliceTotal = remember(categories) { categories.sumOf { it.amount } }

    // Calculate display sweep angles with guaranteed minimum visual width for small slices (< 2% or 1.3%)
    val displaySweeps = remember(categories, sliceTotal, isDtOgStyle) {
        if (sliceTotal <= 0.0 || categories.isEmpty()) {
            List(categories.size) { 0f }
        } else if (categories.size == 1) {
            listOf(360f)
        } else {
            val minRaw = if (isDtOgStyle) 7.5f else 6.0f
            val raw = categories.map { ((it.amount / sliceTotal) * 360.0).toFloat() }
            val positiveCount = raw.count { it > 0f }
            val effectiveMin = if (positiveCount * minRaw < 360f) minRaw else (360f / positiveCount) * 0.5f

            val boosted = BooleanArray(raw.size)
            var fixedAngleSum = 0f
            var nonBoostedRawSum = 0f

            raw.forEachIndexed { i, r ->
                if (r > 0f && r < effectiveMin) {
                    boosted[i] = true
                    fixedAngleSum += effectiveMin
                } else if (r > 0f) {
                    nonBoostedRawSum += r
                }
            }

            val remainingAngle = (360f - fixedAngleSum).coerceAtLeast(0f)
            raw.mapIndexed { i, r ->
                if (r <= 0f) 0f
                else if (boosted[i]) effectiveMin
                else if (nonBoostedRawSum > 0f) (r / nonBoostedRawSum) * remainingAngle
                else r
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isDtOgStyle) {
                // Flat 2D Donut Chart (DT_OG Theme): Thick annular segments with clean gaps & rounded corners
                val diameter = size.minDimension * 0.96f
                val rOuter = diameter / 2f
                val rInner = rOuter * 0.58f // ~42% thickness for chunky look matching reference
                val baseGap = if (categories.size > 1) 5f else 0f // 5 degree visible gap between segments
                val center = Offset(size.width / 2f, size.height / 2f)

                var startAngle = -90f
                categories.forEachIndexed { index, category ->
                    val rawSweep = displaySweeps.getOrElse(index) { 0f }
                    // Adaptive gap: never consume more than 40% of the slice
                    val effectiveGap = baseGap.coerceAtMost(rawSweep * 0.4f)
                    val sweep = (rawSweep - effectiveGap).coerceAtLeast(0f)
                    if (sweep > 0.5f) {
                        val sliceStartAngle = startAngle + (effectiveGap / 2f)
                        val slicePath = buildAnnularSectorPath(
                            center = center,
                            rInner = rInner,
                            rOuter = rOuter,
                            startAngleDeg = sliceStartAngle,
                            sweepAngleDeg = sweep,
                            cornerRadiusPx = cornerRadiusPx
                        )
                        drawPath(slicePath, category.color)
                    }
                    startAngle += rawSweep
                }
            } else {
                // Standard 2D Donut Chart (for other themes): Annular segments with softer proportions
                val diameter = size.minDimension * 0.92f
                val rOuter = diameter / 2f
                val rInner = rOuter * 0.64f  // Slightly thinner ring than DT_OG for elegance
                val baseGap = if (categories.size > 1) 3.5f else 0f // Slightly tighter gaps
                val softCornerPx = cornerRadiusPx * 0.75f  // Softer corners
                val center = Offset(size.width / 2f, size.height / 2f)

                var startAngle = -90f
                categories.forEachIndexed { index, category ->
                    val rawSweep = displaySweeps.getOrElse(index) { 0f }
                    // Adaptive gap: never consume more than 40% of the slice
                    val effectiveGap = baseGap.coerceAtMost(rawSweep * 0.4f)
                    val sweep = (rawSweep - effectiveGap).coerceAtLeast(0f)
                    if (sweep > 0.5f) {
                        val sliceStartAngle = startAngle + (effectiveGap / 2f)
                        val slicePath = buildAnnularSectorPath(
                            center = center,
                            rInner = rInner,
                            rOuter = rOuter,
                            startAngleDeg = sliceStartAngle,
                            sweepAngleDeg = sweep,
                            cornerRadiusPx = softCornerPx
                        )
                        drawPath(slicePath, category.color)
                    }
                    startAngle += rawSweep
                }
            }
        }

        // Center text overlay
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = centerTitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatCompact(total),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Builds an annular sector (donut segment) Path with rounded corners, matching d3.arc / Recharts.
 */
internal fun buildAnnularSectorPath(
    center: Offset,
    rInner: Float,
    rOuter: Float,
    startAngleDeg: Float,
    sweepAngleDeg: Float,
    cornerRadiusPx: Float
): Path {
    val path = Path()
    if (sweepAngleDeg <= 0.5f || rOuter <= rInner) return path

    val degToRad = (Math.PI / 180.0).toFloat()
    val endAngleDeg = startAngleDeg + sweepAngleDeg

    // Adapt corner radius for narrow slices to preserve a clean capsule without collapsing
    val maxCornerRadBySweep = (sweepAngleDeg * 0.35f) * degToRad * rInner
    val adaptedCornerRadiusPx = cornerRadiusPx.coerceAtMost(maxCornerRadBySweep.coerceAtLeast(2f))

    val daOuter = (adaptedCornerRadiusPx / rOuter) * (180f / Math.PI.toFloat())
    val daInner = (adaptedCornerRadiusPx / rInner) * (180f / Math.PI.toFloat())
    val daOut = daOuter.coerceAtMost(sweepAngleDeg * 0.45f)
    val daIn = daInner.coerceAtMost(sweepAngleDeg * 0.45f)
    val rCorner = adaptedCornerRadiusPx.coerceAtMost((rOuter - rInner) * 0.45f)

    val cosStart = cos(startAngleDeg * degToRad)
    val sinStart = sin(startAngleDeg * degToRad)
    val cosEnd = cos(endAngleDeg * degToRad)
    val sinEnd = sin(endAngleDeg * degToRad)

    // Points on start radial line
    val pA = Offset(center.x + (rInner + rCorner) * cosStart, center.y + (rInner + rCorner) * sinStart)
    val pB = Offset(center.x + (rOuter - rCorner) * cosStart, center.y + (rOuter - rCorner) * sinStart)
    // Corner 1 vertex (Outer Start)
    val v1 = Offset(center.x + rOuter * cosStart, center.y + rOuter * sinStart)
    // Point on outer arc start
    val aOutStartRad = (startAngleDeg + daOut) * degToRad
    val pC = Offset(center.x + rOuter * cos(aOutStartRad), center.y + rOuter * sin(aOutStartRad))

    // Corner 2 vertex (Outer End)
    val v2 = Offset(center.x + rOuter * cosEnd, center.y + rOuter * sinEnd)
    // Point on end radial line (Outer)
    val pD = Offset(center.x + (rOuter - rCorner) * cosEnd, center.y + (rOuter - rCorner) * sinEnd)
    // Point on end radial line (Inner)
    val pE = Offset(center.x + (rInner + rCorner) * cosEnd, center.y + (rInner + rCorner) * sinEnd)

    // Corner 3 vertex (Inner End)
    val v3 = Offset(center.x + rInner * cosEnd, center.y + rInner * sinEnd)
    // Point on inner arc end
    val aInEndRad = (endAngleDeg - daIn) * degToRad
    val pF = Offset(center.x + rInner * cos(aInEndRad), center.y + rInner * sin(aInEndRad))

    // Corner 4 vertex (Inner Start)
    val v4 = Offset(center.x + rInner * cosStart, center.y + rInner * sinStart)

    // Build closed path
    path.moveTo(pA.x, pA.y)
    path.lineTo(pB.x, pB.y)
    path.quadraticTo(v1.x, v1.y, pC.x, pC.y)

    val outerSweep = sweepAngleDeg - 2 * daOut
    if (outerSweep > 0.1f) {
        path.arcTo(
            rect = Rect(center.x - rOuter, center.y - rOuter, center.x + rOuter, center.y + rOuter),
            startAngleDegrees = startAngleDeg + daOut,
            sweepAngleDegrees = outerSweep,
            forceMoveTo = false
        )
    }

    path.quadraticTo(v2.x, v2.y, pD.x, pD.y)
    path.lineTo(pE.x, pE.y)
    path.quadraticTo(v3.x, v3.y, pF.x, pF.y)

    val innerSweep = sweepAngleDeg - 2 * daIn
    if (innerSweep > 0.1f) {
        path.arcTo(
            rect = Rect(center.x - rInner, center.y - rInner, center.x + rInner, center.y + rInner),
            startAngleDegrees = endAngleDeg - daIn,
            sweepAngleDegrees = -innerSweep,
            forceMoveTo = false
        )
    }

    path.quadraticTo(v4.x, v4.y, pA.x, pA.y)
    path.close()

    return path
}

/**
 * Generates a harmonious chart palette from the theme's primary color.
 * Uses HSL hue rotation to produce visually distinct yet cohesive colors.
 * The first color is always the primary itself, with subsequent colors
 * spreading across a controlled hue arc while preserving saturation/lightness family.
 */
internal fun generateThemeChartPalette(primary: Color, count: Int): List<Color> {
    if (count <= 0) return emptyList()

    // Convert primary to HSL
    val argb = (primary.alpha * 255).toInt() shl 24 or
            ((primary.red * 255).toInt() shl 16) or
            ((primary.green * 255).toInt() shl 8) or
            (primary.blue * 255).toInt()
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    val baseHue = hsl[0]      // 0-360
    val baseSat = hsl[1]      // 0-1
    val baseLit = hsl[2]      // 0-1

    // Spread hues across a 180° arc centered on the primary hue, with
    // alternating saturation/lightness tweaks for extra distinction.
    val hueSpread = when {
        count <= 2 -> 60f
        count <= 4 -> 120f
        else -> 180f
    }
    val step = if (count > 1) hueSpread / (count - 1) else 0f
    val startHue = baseHue - hueSpread / 2f

    return List(count) { i ->
        val hue = ((startHue + step * i) % 360f + 360f) % 360f
        // Alternate saturation and lightness slightly for more distinction
        val satOffset = if (i % 2 == 0) 0f else -0.08f
        val litOffset = when (i % 3) {
            0 -> 0f
            1 -> 0.04f
            else -> -0.04f
        }
        val sat = (baseSat + satOffset).coerceIn(0.25f, 1f)
        val lit = (baseLit + litOffset).coerceIn(0.30f, 0.70f)
        val outHsl = floatArrayOf(hue, sat, lit)
        val resultArgb = ColorUtils.HSLToColor(outHsl)
        Color(
            red = (resultArgb shr 16 and 0xFF) / 255f,
            green = (resultArgb shr 8 and 0xFF) / 255f,
            blue = (resultArgb and 0xFF) / 255f,
            alpha = 1f
        )
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Legend Grid (2 columns)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun LegendGrid(
    categories: List<SpendingCategory>,
    totalAmount: Double = 0.0,
    isDtOg: Boolean = false,
    onCategoryClick: (String) -> Unit
) {
    // Chunk into rows of 2
    val rows = categories.chunked(2)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { category ->
                    val percentage = if (totalAmount > 0) (category.amount / totalAmount) * 100.0 else 0.0
                    val percentageStr = "%.1f%%".format(percentage)
                    LegendItem(
                        category = category,
                        percentageText = percentageStr,
                        isDtOg = isDtOg,
                        onClick = { onCategoryClick(category.name) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // If odd number of items, fill remaining space
                if (row.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun LegendItem(
    category: SpendingCategory,
    percentageText: String? = null,
    isDtOg: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val indicatorShape = if (isDtOg) RoundedCornerShape(2.dp) else CircleShape

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Color dot / square
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(indicatorShape)
                        .background(category.color)
                )
                // Label
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            // Amount & Percentage Stack
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "₹${formatCompact(category.amount)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                if (!percentageText.isNullOrBlank()) {
                    Text(
                        text = percentageText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

