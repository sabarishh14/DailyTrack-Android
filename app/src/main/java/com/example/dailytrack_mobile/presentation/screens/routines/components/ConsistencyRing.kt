package com.example.dailytrack_mobile.presentation.screens.routines.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.example.dailytrack_mobile.domain.routines.Score

/**
 * The consistency score as a ring: green for what was kept, red for what was
 * missed (skips are excused, so they're in neither). Just the track until there's a score.
 */
@Composable
internal fun ConsistencyRing(
    score: Score?,
    diameter: Dp,
    strokeWidth: Dp,
    track: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val fraction = score?.fraction?.toFloat()
    val kept by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "consistencyKept"
    )
    val missed by animateFloatAsState(
        targetValue = fraction?.let { 1f - it } ?: 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "consistencyMissed"
    )
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arc = Size(size.width - stroke, size.height - stroke)
            // Round ends only while one colour fills the ring alone; butt ends where two meet.
            val cap = if (kept > 0.001f && missed > 0.001f) StrokeCap.Butt else StrokeCap.Round
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            if (kept > 0.001f) {
                drawArc(DayDoneColor, -90f, 360f * kept, false, topLeft, arc, style = Stroke(stroke, cap = cap))
            }
            if (missed > 0.001f) {
                drawArc(DayMissedColor, -90f + 360f * kept, 360f * missed, false, topLeft, arc, style = Stroke(stroke, cap = cap))
            }
        }
        content()
    }
}
