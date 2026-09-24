package com.example.dailytrack_mobile.presentation.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A fixed height for bottom-sheet content, as a fraction of the screen.
 *
 * Sheet content must not size itself with `fillMaxHeight(fraction)` or
 * `fillMaxSize()`: those resolve against the space the sheet is offering *right
 * now*, which changes while the sheet is being dragged. The content re-measures
 * every frame, the sheet recomputes its anchors from the new size, and the two
 * feed back into each other — visible as the sheet jittering when pushed up.
 * Deriving the height from the screen keeps it constant through any drag.
 */
@Composable
fun rememberSheetHeight(fraction: Float): Dp {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    return remember(screenHeightDp, fraction) { (screenHeightDp * fraction).dp }
}

/**
 * Pass as `contentWindowInsets` to every ModalBottomSheet.
 *
 * The default also pads the top by however much of the status bar the sheet
 * currently overlaps. A fling springs the sheet past its resting point into the
 * status bar; that padding makes the sheet taller, which moves its resting
 * anchor, which moves the sheet — and it oscillates without ever settling. No
 * sheet here is tall enough to need top padding, so only the bottom is kept.
 */
val SheetContentInsets: @Composable () -> WindowInsets = {
    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
}
