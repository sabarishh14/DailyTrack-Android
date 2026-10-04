package com.example.dailytrack_mobile.presentation.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import kotlin.math.floor

/**
 * One line that shrinks to fit its width instead of wrapping — for hero
 * numbers, where a balance broken over two lines looks broken. A wide font, a
 * small phone or a big number just make it a little smaller, in 5% steps so a
 * number changing under a finger doesn't make it twitch. Never grows past
 * [style]'s size.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    minScale: Float = 0.6f
) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val maxWidth = constraints.maxWidth
        val fitted = remember(text, style, maxWidth) {
            val size = style.fontSize
            if (maxWidth == Constraints.Infinity || !size.isSp) return@remember style
            val width = measurer.measure(text = text, style = style, softWrap = false, maxLines = 1).size.width
            if (width <= maxWidth) return@remember style
            val scale = (floor(maxWidth.toFloat() / width * 20f) / 20f).coerceAtLeast(minScale)
            style.copy(fontSize = size * scale)
        }
        Text(
            text = text,
            style = fitted,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}
