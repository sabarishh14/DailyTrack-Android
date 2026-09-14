package com.example.dailytrack_mobile.presentation.screens.invest.components

import com.example.dailytrack_mobile.presentation.screens.invest.InvestCategory
import com.example.dailytrack_mobile.presentation.screens.invest.InvestColors
import com.example.dailytrack_mobile.presentation.screens.invest.InvestState
import com.example.dailytrack_mobile.presentation.screens.invest.InvestTab
import com.example.dailytrack_mobile.presentation.screens.invest.InvestmentHolding
import com.example.dailytrack_mobile.presentation.screens.invest.ChartTimeRange
import com.example.dailytrack_mobile.presentation.screens.invest.ChartPoint
import com.example.dailytrack_mobile.presentation.screens.invest.formatCompact
import com.example.dailytrack_mobile.presentation.screens.invest.formatPointDate

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import com.example.dailytrack_mobile.presentation.screens.invest.components.CategoryVisibilitySheet
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.presentation.components.DailyTrackPullToRefreshBox
import com.example.dailytrack_mobile.presentation.util.Dimens
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Advanced Chart
// ─────────────────────────────────────────────────────────────────────────────
internal fun formatMonth(dateStr: String): String {
    try {
        val date = java.time.LocalDate.parse(dateStr)
        val month = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val year = date.year.toString().takeLast(2)
        return "$month '$year"
    } catch (e: Exception) {
        return ""
    }
}

@Composable
internal fun AdvancedChart(
    points: List<ChartPoint>,
    isValueMode: Boolean,
    isGain: Boolean,
    showXAxis: Boolean = false,
    selectedPoint: ChartPoint? = null,
    onPointSelected: ((ChartPoint?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Not enough data", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val haptic = LocalHapticFeedback.current
    var internalSelectedPoint by remember { mutableStateOf<ChartPoint?>(null) }
    val activePoint = selectedPoint ?: internalSelectedPoint

    fun updatePoint(point: ChartPoint?) {
        if (point != null && point.date != activePoint?.date) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        internalSelectedPoint = point
        onPointSelected?.invoke(point)
    }

    val activeIndex = remember(activePoint, points) {
        if (activePoint != null) {
            val idx = points.indexOfFirst { it.date == activePoint.date }
            if (idx >= 0) idx else null
        } else null
    }
    
    val primaryColor = if (isGain) InvestColors.GainGreen else InvestColors.LossRed
    val investedColor = MaterialTheme.colorScheme.primary
    val guideLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    val yVals = if (isValueMode) {
        points.flatMap { listOf(it.current, it.invested) }
    } else {
        points.map { it.pnlPercent }
    }

    val minVal = yVals.minOrNull() ?: 0f
    val maxVal = yVals.maxOrNull() ?: 0f
    val range = (maxVal - minVal).coerceAtLeast(0.01f)
    
    val paddingTop = 28f
    val paddingBottom = if (showXAxis) 60f else 24f
    
    BoxWithConstraints(modifier = modifier) {
            // Chart part
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(points) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (points.isNotEmpty()) {
                                    val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                                    val index = (offset.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                                    val tappedPoint = points[index]
                                    if (activePoint?.date == tappedPoint.date) {
                                        updatePoint(null)
                                    } else {
                                        updatePoint(tappedPoint)
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(points) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                if (points.isNotEmpty()) {
                                    val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                                    val index = (offset.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                                    updatePoint(points[index])
                                }
                            },
                            onHorizontalDrag = { change, _ ->
                                if (points.isNotEmpty()) {
                                    val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                                    val index = (change.position.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                                    updatePoint(points[index])
                                    change.consume()
                                }
                            }
                        )
                    }
            ) {
                val textPaint = remember {
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 28f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                }

                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                    
                    fun yOf(value: Float): Float {
                        return size.height - paddingBottom - ((value - minVal) / range) * (size.height - paddingTop - paddingBottom)
                    }

                    // Dotted horizontal grid lines
                    val pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = paddingTop + i * ((size.height - paddingTop - paddingBottom) / gridLines)
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.2f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2f,
                            pathEffect = pathEffect
                        )
                    }
                    
                    if (showXAxis && points.isNotEmpty()) {
                        val labelCount = 6
                        val step = (points.size / labelCount).coerceAtLeast(1)
                        for (i in 0 until points.size step step) {
                            val x = (i * stepX).coerceIn(40f, size.width - 40f)
                            val y = size.height - 10f
                            val monthLabel = formatMonth(points[i].date)
                            drawContext.canvas.nativeCanvas.drawText(monthLabel, x, y, textPaint)
                        }
                    }

                    if (isValueMode) {
                        // Draw Invested Line
                        val invPath = Path().apply {
                            moveTo(0f, yOf(points[0].invested))
                            for (i in 1 until points.size) {
                                lineTo(i * stepX, yOf(points[i].invested))
                            }
                        }
                        drawPath(
                            path = invPath,
                            color = investedColor.copy(alpha = 0.8f),
                            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        
                        // Draw Current Line
                        val currPath = Path().apply {
                            moveTo(0f, yOf(points[0].current))
                            for (i in 1 until points.size) {
                                lineTo(i * stepX, yOf(points[i].current))
                            }
                        }
                        
                        val fillPath = Path().apply {
                            addPath(currPath)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(primaryColor.copy(alpha = 0.3f), primaryColor.copy(alpha = 0.05f), Color.Transparent)
                            ),
                            style = Fill
                        )

                        drawPath(
                            path = currPath,
                            color = primaryColor,
                            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        
                        // Subtle point dots when point count is manageable
                        if (points.size in 3..25) {
                            for (i in 0 until points.size) {
                                drawCircle(
                                    color = primaryColor.copy(alpha = 0.5f),
                                    radius = 3.5f,
                                    center = Offset(i * stepX, yOf(points[i].current))
                                )
                            }
                        }

                        // Draw end dots
                        drawCircle(color = investedColor.copy(alpha = 0.8f), radius = 6f, center = Offset((points.size - 1) * stepX, yOf(points.last().invested)))
                        drawCircle(color = primaryColor, radius = 6f, center = Offset((points.size - 1) * stepX, yOf(points.last().current)))

                        // Selected point indicators
                        if (activeIndex != null && activeIndex in points.indices) {
                            val selPt = points[activeIndex]
                            val selX = activeIndex * stepX
                            val guidePath = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                            
                            // Vertical guide line
                            drawLine(
                                color = guideLineColor,
                                start = Offset(selX, paddingTop),
                                end = Offset(selX, size.height - paddingBottom),
                                strokeWidth = 2f,
                                pathEffect = guidePath
                            )

                            // Invested marker
                            val invY = yOf(selPt.invested)
                            drawCircle(color = investedColor.copy(alpha = 0.25f), radius = 10f, center = Offset(selX, invY))
                            drawCircle(color = investedColor, radius = 5.5f, center = Offset(selX, invY))
                            drawCircle(color = Color.White, radius = 2.5f, center = Offset(selX, invY))

                            // Current marker
                            val currY = yOf(selPt.current)
                            drawCircle(color = primaryColor.copy(alpha = 0.3f), radius = 12f, center = Offset(selX, currY))
                            drawCircle(color = primaryColor, radius = 6.5f, center = Offset(selX, currY))
                            drawCircle(color = Color.White, radius = 3f, center = Offset(selX, currY))
                        }
                    } else {
                        // Return Mode
                        val returnPath = Path().apply {
                            moveTo(0f, yOf(points[0].pnlPercent))
                            for (i in 1 until points.size) {
                                lineTo(i * stepX, yOf(points[i].pnlPercent))
                            }
                        }
                        
                        val fillPath = Path().apply {
                            addPath(returnPath)
                            lineTo(size.width, yOf(0f))
                            lineTo(0f, yOf(0f))
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(primaryColor.copy(alpha = 0.3f), primaryColor.copy(alpha = 0.05f), Color.Transparent)
                            ),
                            style = Fill
                        )

                        drawPath(
                            path = returnPath,
                            color = primaryColor,
                            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        
                        if (points.size in 3..25) {
                            for (i in 0 until points.size) {
                                drawCircle(
                                    color = primaryColor.copy(alpha = 0.5f),
                                    radius = 3.5f,
                                    center = Offset(i * stepX, yOf(points[i].pnlPercent))
                                )
                            }
                        }

                        drawCircle(color = primaryColor, radius = 6f, center = Offset((points.size - 1) * stepX, yOf(points.last().pnlPercent)))

                        // Zero line
                        if (minVal < 0 && maxVal > 0) {
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.5f),
                                start = Offset(0f, yOf(0f)),
                                end = Offset(size.width, yOf(0f)),
                                strokeWidth = 2f,
                                pathEffect = pathEffect
                            )
                        }

                        // Selected point indicator for Return mode
                        if (activeIndex != null && activeIndex in points.indices) {
                            val selPt = points[activeIndex]
                            val selX = activeIndex * stepX
                            val guidePath = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                            drawLine(
                                color = guideLineColor,
                                start = Offset(selX, paddingTop),
                                end = Offset(selX, size.height - paddingBottom),
                                strokeWidth = 2f,
                                pathEffect = guidePath
                            )

                            val pnlY = yOf(selPt.pnlPercent)
                            drawCircle(color = primaryColor.copy(alpha = 0.3f), radius = 12f, center = Offset(selX, pnlY))
                            drawCircle(color = primaryColor, radius = 6.5f, center = Offset(selX, pnlY))
                            drawCircle(color = Color.White, radius = 3f, center = Offset(selX, pnlY))
                        }
                    }
                }

                // Interactive floating badge at top center of the chart
                androidx.compose.animation.AnimatedVisibility(
                    visible = activePoint != null,
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                ) {
                    if (activePoint != null) {
                        val ptGain = if (isValueMode) activePoint.current >= activePoint.invested else activePoint.pnlPercent >= 0f
                        val ptGainColor = if (ptGain) InvestColors.GainGreen else InvestColors.LossRed

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                            shadowElevation = 2.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = formatPointDate(activePoint.date),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                )

                                if (isValueMode) {
                                    Text(
                                        text = formatCompact(activePoint.current.toDouble()),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = ptGainColor
                                    )
                                    Text(
                                        text = "(${if (ptGain) "+" else ""}${String.format(java.util.Locale.US, "%.1f", activePoint.pnlPercent)}%)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = ptGainColor
                                    )
                                } else {
                                    Text(
                                        text = "${if (ptGain) "+" else ""}${String.format(java.util.Locale.US, "%.1f", activePoint.pnlPercent)}%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = ptGainColor
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .clickable { updatePoint(null) }
                                )
                            }
                        }
                    }
                }
            }
            
            // Y-Axis
            Column(
                modifier = Modifier
                    .width(34.dp)
                    .fillMaxHeight()
                    .padding(start = 2.dp, top = 16.dp, bottom = if (showXAxis) 24.dp else 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatCompactForAxis(maxVal.toDouble(), !isValueMode), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatCompactForAxis(((maxVal + minVal) / 2).toDouble(), !isValueMode), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatCompactForAxis(minVal.toDouble(), !isValueMode), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
    }
}

internal fun formatCompactForAxis(amount: Double, isPercent: Boolean = false): String {
    if (isPercent) return "%.1f%%".format(amount)
    val abs = Math.abs(amount)
    val prefix = if (amount < 0) "-" else ""
    return when {
        abs >= 1_00_00_000 -> "$prefix%.1fCr".format(abs / 1_00_00_000)
        abs >= 1_00_000    -> "$prefix%.1fL".format(abs / 1_00_000)
        abs >= 1_000       -> "$prefix%.1fK".format(abs / 1_000)
        else               -> "$prefix%.0f".format(abs)
    }
}

