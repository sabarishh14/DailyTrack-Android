package com.example.dailytrack_mobile.presentation.screens.settings

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
import com.example.dailytrack_mobile.presentation.screens.settings.components.AboutSettingsSubScreen
import com.example.dailytrack_mobile.presentation.screens.settings.components.AppUpdatesSubScreen
import com.example.dailytrack_mobile.presentation.screens.settings.components.AppearanceSettingsSubScreen
import com.example.dailytrack_mobile.presentation.screens.settings.components.GeneralSettingsSubScreen
import com.example.dailytrack_mobile.presentation.screens.settings.components.PrivacySecuritySettingsSubScreen
import com.example.dailytrack_mobile.presentation.screens.settings.components.SettingsCard
import com.example.dailytrack_mobile.presentation.screens.settings.components.SettingsNavItem
import com.example.dailytrack_mobile.presentation.screens.settings.components.SyncSettingsSubScreen
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
// Theme preview helper
// ─────────────────────────────────────────────────────────────────────────────

internal data class ThemePreviewColors(
    val top: Color,
    val bottomLeft: Color,
    val bottomRight: Color
)

internal fun previewColorsFor(theme: AppTheme): ThemePreviewColors = when (theme) {
    AppTheme.YELLOW -> ThemePreviewColors(
        top = YellowThemeColors.lightScheme.primaryContainer,
        bottomLeft = YellowThemeColors.lightScheme.secondaryContainer,
        bottomRight = YellowThemeColors.lightScheme.tertiaryContainer
    )
    AppTheme.GREEN -> ThemePreviewColors(
        top = GreenThemeColors.lightScheme.primaryContainer,
        bottomLeft = GreenThemeColors.lightScheme.secondaryContainer,
        bottomRight = GreenThemeColors.lightScheme.tertiaryContainer
    )
    AppTheme.TEAL -> ThemePreviewColors(
        top = TealThemeColors.lightScheme.primaryContainer,
        bottomLeft = TealThemeColors.lightScheme.secondaryContainer,
        bottomRight = TealThemeColors.lightScheme.tertiaryContainer
    )
    AppTheme.PURPLE -> ThemePreviewColors(
        top = PurpleThemeColors.lightScheme.primaryContainer,
        bottomLeft = PurpleThemeColors.lightScheme.secondaryContainer,
        bottomRight = PurpleThemeColors.lightScheme.tertiaryContainer
    )
    AppTheme.JUNE_OLED -> ThemePreviewColors(
        top = JuneOledThemeColors.lightScheme.primaryContainer,
        bottomLeft = JuneOledThemeColors.lightScheme.secondaryContainer,
        bottomRight = JuneOledThemeColors.lightScheme.tertiaryContainer
    )
    AppTheme.DT_OG -> ThemePreviewColors(
        top = Color(0xFF6366F1),
        bottomLeft = Color(0xFF06B6D4),
        bottomRight = Color(0xFF8B5CF6)
    )
    AppTheme.MILES_MORALES -> ThemePreviewColors(
        top = Color(0xFFFF2E4D),
        bottomLeft = Color(0xFF0B0A0C),
        bottomRight = Color(0xFF9D6BFF)
    )
    AppTheme.SYNTHWAVE -> ThemePreviewColors(
        top = Color(0xFFFF3EA5),
        bottomLeft = Color(0xFF2DE2E6),
        bottomRight = Color(0xFFFFB03A)
    )
    AppTheme.CATPPUCCIN_MOCHA -> ThemePreviewColors(
        top = Color(0xFFCBA6F7),
        bottomLeft = Color(0xFF89B4FA),
        bottomRight = Color(0xFFF5C2E7)
    )
}


private data class SettingsCategoryItem(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val keywords: List<String>
)

// ─────────────────────────────────────────────────────────────────────────────
// Main Settings Screen – Root with sub-screen routing
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    appLockManager: AppLockManager? = null,
    onAction: (SettingsAction) -> Unit
) {
    val context = LocalContext.current
    val dims = Dimens.current
    val effectiveLockManager = appLockManager ?: remember { AppLockManager(context) }
    var currentSubScreen by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val categories = remember(state.isAppLockEnabled, state.appVersion, state.updateStatus, state.latestUpdateInfo, state.isUserAdmin) {
        listOfNotNull(
            // Admins only: who can sign in and what they can see (ACCESS_CONTROL.md)
            if (state.isUserAdmin) SettingsCategoryItem(
                id = "AccessControl",
                icon = Icons.Default.AdminPanelSettings,
                title = "Access Control",
                subtitle = "Who can see and change what",
                keywords = listOf("access", "people", "users", "share", "permissions", "roles", "admin", "email", "invite")
            ) else null,
            SettingsCategoryItem(
                id = "General",
                icon = Icons.Default.Tune,
                title = "General",
                subtitle = "Demo mode, sample data, preferences",
                keywords = listOf("demo", "sample", "test", "data", "reset", "general", "preferences")
            ),
            SettingsCategoryItem(
                id = "Reminders",
                icon = Icons.Default.Alarm,
                title = "Reminders",
                subtitle = "Daily tracking reminders & schedule",
                keywords = listOf("reminder", "reminders", "schedule", "scheduler", "notification", "notifications", "alarm", "daily", "alert", "time")
            ),
            SettingsCategoryItem(
                id = "Appearance",
                icon = Icons.Default.Palette,
                title = "Appearance",
                subtitle = "Theme, dark mode, colors",
                keywords = listOf("theme", "dark", "light", "amoled", "oled", "true black", "color", "wallpaper", "appearance", "palette")
            ),
            SettingsCategoryItem(
                id = "PrivacySecurity",
                icon = Icons.Default.Lock,
                title = "Privacy & Security",
                subtitle = if (state.isAppLockEnabled) "App lock enabled" else "App lock disabled",
                keywords = listOf("lock", "pin", "fingerprint", "biometric", "security", "privacy", "password", "passcode")
            ),
            SettingsCategoryItem(
                id = "Sync",
                icon = Icons.Default.Sync,
                title = "Sync",
                subtitle = "Force sync, Sheets, reconcile, imports",
                keywords = listOf(
                    "sync", "server", "cloud", "api", "refresh", "status", "hydrate",
                    "sheets", "google sheets", "push", "export", "upload",
                    "reconcile", "balance", "balances", "ocr", "screenshot", "upi",
                    "letterboxd", "import", "movies", "films"
                )
            ),
            SettingsCategoryItem(
                id = "Updates",
                icon = Icons.Default.SystemUpdate,
                title = "App Updates",
                subtitle = when (state.updateStatus) {
                    UpdateStatus.UPDATE_AVAILABLE -> "Update available (${state.latestUpdateInfo?.versionName ?: "New"})"
                    UpdateStatus.DOWNLOADING -> "Downloading update..."
                    UpdateStatus.READY_TO_INSTALL -> "Update ready to install"
                    else -> "Check for latest release"
                },
                keywords = listOf("update", "updates", "upgrade", "version", "download", "install", "apk", "latest", "new")
            ),
            SettingsCategoryItem(
                id = "About",
                icon = Icons.Default.Info,
                title = "About",
                subtitle = state.appVersion,
                keywords = listOf("about", "version", "developer", "info", "app", "update", "updates")
            )
        )
    }

    val filteredCategories = remember(searchQuery, categories) {
        if (searchQuery.isBlank()) {
            categories
        } else {
            val q = searchQuery.trim()
            categories.filter { cat ->
                cat.title.contains(q, ignoreCase = true) ||
                cat.subtitle.contains(q, ignoreCase = true) ||
                cat.keywords.any { it.contains(q, ignoreCase = true) }
            }
        }
    }

    // Material 3 Loading Dialog during Force Sync (global overlay)
    if (state.isSyncing) {
        Dialog(
            onDismissRequest = { /* Non-dismissible while in-flight */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Card(
                shape = RoundedCornerShape(dims.cardCornerRadius),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dims.cardInnerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dims.cardInnerPadding + 8.dp, vertical = dims.cardInnerPadding + 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Material 3 Expressive LoadingIndicator
                    LoadingIndicator(
                        modifier = Modifier.size(56.dp)
                    )

                    Spacer(Modifier.height(dims.itemSpacingLarge))

                    Text(
                        text = "Syncing All Pages",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(dims.itemSpacingSmall))

                    Text(
                        text = state.syncStepDescription ?: "Re-hydrating accounts, transactions, investments, activities, and media...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(dims.itemSpacingLarge))

                    // Material 3 Linear progress indicator with StrokeCap.Round for expressive visual rhythm
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }

    // Material 3 Install Unknown Apps Permission Dialog
    if (state.showInstallPermissionDialog) {
        AlertDialog(
            onDismissRequest = { onAction(SettingsAction.OnDismissInstallPermissionDialog) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Install Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "To install the updated version directly, Android requires permission for DailyTrack to install unknown apps.\n\nTap 'Open Settings' to enable the switch for DailyTrack, then return to complete installation.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { onAction(SettingsAction.OnOpenInstallPermissionSettings) },
                    shape = RoundedCornerShape(dims.buttonCornerRadius)
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onAction(SettingsAction.OnDismissInstallPermissionDialog) },
                    shape = RoundedCornerShape(dims.buttonCornerRadius)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Sub-screen routing ───────────────────────────────────────────────────
    when (currentSubScreen) {
        "AccessControl" -> {
            com.example.dailytrack_mobile.presentation.screens.settings.components.AccessControlSubScreen(
                onNavigateBack = { currentSubScreen = null }
            )
            return
        }
        "AppLockSettings" -> {
            AppLockSettingsScreen(
                state = state,
                onAction = onAction,
                onNavigateBack = { currentSubScreen = "PrivacySecurity" }
            )
            return
        }
        "General" -> {
            GeneralSettingsSubScreen(
                state = state,
                appLockManager = effectiveLockManager,
                onAction = onAction,
                onNavigateBack = { currentSubScreen = null }
            )
            return
        }
        "Reminders" -> {
            RemindersSettingsScreen(
                state = state,
                onAction = onAction,
                onNavigateBack = { currentSubScreen = null }
            )
            return
        }
        "Appearance" -> {
            AppearanceSettingsSubScreen(
                state = state,
                onAction = onAction,
                onNavigateBack = { currentSubScreen = null }
            )
            return
        }
        "PrivacySecurity" -> {
            PrivacySecuritySettingsSubScreen(
                state = state,
                onAction = onAction,
                onNavigateToAppLock = { currentSubScreen = "AppLockSettings" },
                onNavigateBack = { currentSubScreen = null }
            )
            return
        }
        "Sync" -> {
            SyncSettingsSubScreen(
                state = state,
                onAction = onAction,
                onNavigateBack = { currentSubScreen = null }
            )
            return
        }
        "Updates" -> {
            AppUpdatesSubScreen(
                state = state,
                onAction = onAction,
                onNavigateBack = { currentSubScreen = null }
            )
            return
        }
        "About" -> {
            AboutSettingsSubScreen(
                state = state,
                onAction = onAction,
                onNavigateBack = { currentSubScreen = null }
            )
            return
        }
    }

    // ── Root settings screen ─────────────────────────────────────────────────
    BackHandler {
        onAction(SettingsAction.OnBackClicked)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(SettingsAction.OnBackClicked) }) {
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
            // ── Search Bar ──────────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search settings...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(dims.iconSizeMedium)
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(dims.iconSizeSmall + 4.dp)
                                )
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(dims.cardCornerRadius),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dims.searchBarHeight)
                )
            }


            // ── Settings Categories ─────────────────────────────────────────
            item {
                if (filteredCategories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No settings match \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredCategories.forEach { category ->
                            SettingsCard {
                                SettingsNavItem(
                                    icon = category.icon,
                                    title = category.title,
                                    subtitle = category.subtitle,
                                    onClick = { currentSubScreen = category.id }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

