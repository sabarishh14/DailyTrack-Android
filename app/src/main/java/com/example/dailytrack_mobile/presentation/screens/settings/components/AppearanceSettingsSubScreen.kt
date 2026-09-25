package com.example.dailytrack_mobile.presentation.screens.settings.components

import com.example.dailytrack_mobile.presentation.components.topBarIconButtonColors
import com.example.dailytrack_mobile.presentation.screens.settings.ThemePreviewColors
import com.example.dailytrack_mobile.presentation.screens.settings.previewColorsFor
import com.example.dailytrack_mobile.presentation.screens.settings.AutoLockTimeoutDialog
import com.example.dailytrack_mobile.presentation.screens.settings.SettingsAction
import com.example.dailytrack_mobile.presentation.screens.settings.SettingsState
import com.example.dailytrack_mobile.presentation.screens.settings.UpdateStatus

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.example.dailytrack_mobile.data.local.security.AppLockManager
import com.example.dailytrack_mobile.data.local.security.LockType
import com.example.dailytrack_mobile.presentation.screens.lock.components.PinVerifyDialog
import com.example.dailytrack_mobile.presentation.theme.AppTheme
import com.example.dailytrack_mobile.presentation.theme.DtOgThemeColors
import com.example.dailytrack_mobile.presentation.theme.GreenThemeColors
import com.example.dailytrack_mobile.presentation.theme.JuneOledThemeColors
import com.example.dailytrack_mobile.presentation.theme.PurpleThemeColors
import com.example.dailytrack_mobile.presentation.theme.TealThemeColors
import com.example.dailytrack_mobile.presentation.theme.ThemeMode
import com.example.dailytrack_mobile.presentation.theme.YellowThemeColors
import com.example.dailytrack_mobile.presentation.util.BiometricHelper
import com.example.dailytrack_mobile.presentation.util.Dimens

// ─────────────────────────────────────────────────────────────────────────────
// Appearance Settings Sub-Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSettingsSubScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    val dims = Dimens.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isDarkTheme = when (state.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    FilledIconButton(colors = topBarIconButtonColors(), onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(dims.iconSizeMedium)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = dims.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = dims.screenBottomPadding)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCard {
                        ThemeModeSelector(
                            currentMode = state.themeMode,
                            onModeSelected = { onAction(SettingsAction.OnThemeModeChanged(it)) }
                        )
                    }

                    AnimatedVisibility(
                        visible = isDarkTheme,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        SettingsCard {
                            SettingsToggleItem(
                                icon = Icons.Default.DarkMode,
                                title = "True black (OLED)",
                                subtitle = "Pure black background for dark theme",
                                checked = state.withAmoled,
                                onCheckedChange = { onAction(SettingsAction.OnAmoledToggled(it)) }
                            )
                        }
                    }

                    SettingsCard {
                        ThemeSectionInCard(
                            selectedTheme = state.selectedTheme,
                            onThemeSelected = { onAction(SettingsAction.OnThemeChanged(it)) }
                        )
                    }
                }
            }
        }
    }
}

