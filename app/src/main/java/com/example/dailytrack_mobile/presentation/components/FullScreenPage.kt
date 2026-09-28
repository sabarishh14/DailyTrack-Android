package com.example.dailytrack_mobile.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * A page over everything, edge to edge, for long scrolling content. Unlike a
 * bottom sheet, scrolling back to the top can never close it: only Back or
 * [onDismiss] does. The content starts below the status bar; lists should end
 * clear of the gesture bar with [navigationBarPadding].
 */
@Composable
fun FullScreenPage(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val pageColor = MaterialTheme.colorScheme.surface
        MatchSystemBarsTo(pageColor)

        val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(tween(180)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 12 }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pageColor)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                content = content
            )
        }
    }
}

/** How much a list inside a [FullScreenPage] needs at the bottom to end clear of the gesture bar. */
@Composable
fun navigationBarPadding(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** The page draws behind the system bars, so their icons must suit its colour. */
@Composable
private fun MatchSystemBarsTo(pageColor: Color) {
    val view = LocalView.current
    val lightBars = pageColor.luminance() > 0.5f
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }
}
