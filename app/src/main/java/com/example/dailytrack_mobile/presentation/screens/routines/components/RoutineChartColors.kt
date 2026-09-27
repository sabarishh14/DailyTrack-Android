package com.example.dailytrack_mobile.presentation.screens.routines.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.dailytrack_mobile.domain.routines.DayStats
import java.time.LocalDate

// Charts (the week's bars, the History calendar, today's ring) use colours anyone
// reads at a glance. The answer buttons keep their emoji.
internal val DayDoneColor = Color(0xFF34C759)
internal val DaySkippedColor = Color(0xFFFFC300)
internal val DayMissedColor = Color(0xFFFF453A)

/** A day's mix for drawing. Blanks are misses once the day is over, and still open today. */
internal data class DayMix(val done: Int, val skipped: Int, val missed: Int, val open: Int) {
    val all: Int get() = done + skipped + missed + open

    /** Everything that counted was done (skips excused). */
    val perfect: Boolean get() = done > 0 && missed == 0 && open == 0
}

internal fun DayStats.mix(today: LocalDate): DayMix {
    val over = date < today
    return DayMix(
        done = done,
        skipped = skipped,
        missed = missed + if (over) unanswered else 0,
        open = if (over) 0 else unanswered
    )
}

/** A thin ring split into done, skipped and missed, over a track for what's still open. */
internal fun DrawScope.drawMixRing(mix: DayMix, track: Color, strokeWidth: Float) {
    val inset = strokeWidth / 2
    val topLeft = Offset(inset, inset)
    val arc = Size(size.width - strokeWidth, size.height - strokeWidth)
    drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(strokeWidth))
    val all = mix.all
    if (all == 0) return
    var angle = -90f
    for ((count, color) in listOf(mix.done to DayDoneColor, mix.skipped to DaySkippedColor, mix.missed to DayMissedColor)) {
        if (count == 0) continue
        val sweep = 360f * count / all
        drawArc(color, angle, sweep, false, topLeft, arc, style = Stroke(strokeWidth))
        angle += sweep
    }
}

/** ● done ● skipped ● missed */
@Composable
internal fun ChartLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LegendEntry(DayDoneColor, "done")
        LegendEntry(DaySkippedColor, "skipped")
        LegendEntry(DayMissedColor, "missed")
    }
}

@Composable
private fun LegendEntry(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
