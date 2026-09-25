package com.example.dailytrack_mobile.presentation.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Extra bottom space scrolling content needs so its last item can scroll clear
 * of the floating nav toolbar drawn over it. 0.dp where no toolbar is shown.
 */
val LocalFloatingBarClearance = compositionLocalOf { 0.dp }

/** Toolbar/FAB height (64dp at most) + its 16dp screen offset + 8dp breathing room. */
val FloatingBarClearance: Dp = 88.dp
