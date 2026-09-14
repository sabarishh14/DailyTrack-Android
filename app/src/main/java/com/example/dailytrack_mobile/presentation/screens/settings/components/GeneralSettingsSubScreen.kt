package com.example.dailytrack_mobile.presentation.screens.settings.components

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
// General Settings Sub-Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GeneralSettingsSubScreen(
    state: SettingsState,
    appLockManager: AppLockManager,
    onAction: (SettingsAction) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var showPinVerifyForDemoMode by remember { mutableStateOf(false) }

    if (showPinVerifyForDemoMode) {
        PinVerifyDialog(
            appLockManager = appLockManager,
            title = "Disable Demo Mode",
            subtitle = "Enter your 4-digit PIN to switch to live data",
            showBiometricOption = state.isBiometricWithPinEnabled,
            onSuccess = {
                showPinVerifyForDemoMode = false
                onAction(SettingsAction.OnDemoModeToggled(false))
            },
            onDismiss = { showPinVerifyForDemoMode = false }
        )
    }

    BackHandler { onNavigateBack() }
    val dims = Dimens.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        text = "General",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
            // ── Data Preferences ────────────────────────────────────────────
            item {
                SettingsSectionLabel("Data Preferences")
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCard {
                        SettingsToggleItem(
                            icon = Icons.Default.Science,
                            title = "Demo Mode",
                            subtitle = "Hydrates screens with realistic sample data stored locally",
                            checked = state.isDemoModeEnabled,
                            onCheckedChange = { requestedState ->
                                if (!requestedState) {
                                    // Turning Demo Mode OFF → verify PIN / Biometric if App Lock is enabled
                                    if (state.isAppLockEnabled) {
                                        if (state.lockType == LockType.SYSTEM) {
                                            val activity = context as? FragmentActivity
                                            if (activity != null) {
                                                BiometricHelper.showBiometricPrompt(
                                                    activity = activity,
                                                    title = "Disable Demo Mode",
                                                    subtitle = "Verify your identity to switch to live data",
                                                    allowDeviceCredential = true,
                                                    onSuccess = {
                                                        onAction(SettingsAction.OnDemoModeToggled(false))
                                                    }
                                                )
                                            } else {
                                                onAction(SettingsAction.OnDemoModeToggled(false))
                                            }
                                        } else {
                                            // LockType.PIN → Show PIN verification dialog (with biometric fallback if enabled)
                                            showPinVerifyForDemoMode = true
                                        }
                                    } else {
                                        // App Lock not enabled → directly turn off
                                        onAction(SettingsAction.OnDemoModeToggled(false))
                                    }
                                } else {
                                    // Turning Demo Mode ON → directly turn on
                                    onAction(SettingsAction.OnDemoModeToggled(true))
                                }
                            }
                        )
                    }
                    if (state.isDemoModeEnabled) {
                        SettingsCard {
                            SettingsClickItem(
                                icon = Icons.Default.RestartAlt,
                                title = "Reset Demo Data",
                                subtitle = "Restore default sample transactions & portfolio",
                                onClick = { onAction(SettingsAction.OnResetDemoDataClicked) }
                            )
                        }
                    }
                }
            }
        }
    }
}

