package com.example.dailytrack_mobile.presentation.screens.settings.components

import com.example.dailytrack_mobile.presentation.screens.settings.ThemePreviewColors
import com.example.dailytrack_mobile.presentation.screens.settings.previewColorsFor
import com.example.dailytrack_mobile.presentation.screens.settings.AutoLockTimeoutDialog
import com.example.dailytrack_mobile.presentation.screens.settings.SettingsAction
import com.example.dailytrack_mobile.presentation.screens.settings.SettingsState
import com.example.dailytrack_mobile.presentation.screens.settings.SyncTaskState
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
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
// Sync Settings Sub-Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SyncSettingsSubScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    val dims = Dimens.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // Whole-ledger actions need unrestricted money edit; see ACCESS_CONTROL.md.
    val access = com.example.dailytrack_mobile.presentation.access.LocalAccess.current
    val canPushTransactions = access.fullMoneyAccess
    val canPushInvestments = access.canEdit(com.example.dailytrack_mobile.data.local.auth.AccessModule.INVEST)
    val canReconcile = access.fullMoneyAccess
    val canImportLetterboxd = access.canEdit(com.example.dailytrack_mobile.data.local.auth.AccessModule.SABDEKHO)

    // Checking the Sheets queue is a network call, so it waits until the screen
    // that shows it is actually open.
    LaunchedEffect(Unit) { if (canPushTransactions) onAction(SettingsAction.OnSyncScreenOpened) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        text = "Sync",
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
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val syncSubtitle = when {
                        state.isSyncing -> "Syncing all pages..."
                        state.syncStatusMessage != null -> state.syncStatusMessage
                        else -> "Re-hydrate all pages by calling APIs again"
                    }
                    val syncSubtitleColor = when {
                        state.isSyncing -> MaterialTheme.colorScheme.primary
                        state.isLastSyncSuccess == true -> Color(0xFF2ECC71)
                        state.isLastSyncSuccess == false -> MaterialTheme.colorScheme.error
                        else -> null
                    }
                    SettingsCard {
                        SettingsClickItem(
                            icon = Icons.Default.Sync,
                            title = "Force Sync",
                            subtitle = syncSubtitle,
                            subtitleColor = syncSubtitleColor,
                            enabled = !state.isSyncing,
                            trailing = {
                                if (state.isSyncing) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            },
                            onClick = { onAction(SettingsAction.OnForceSyncClicked) }
                        )
                    }
                    val serverStatusSubtitle = when {
                        state.isRefreshingServerStatus -> "Checking..."
                        state.serverStatusResult == true -> "Online (Reachable)"
                        state.serverStatusResult == false -> "Offline (Unreachable)"
                        else -> "Check if backend is reachable"
                    }
                    val serverStatusSubtitleColor = when {
                        state.isRefreshingServerStatus -> MaterialTheme.colorScheme.primary
                        state.serverStatusResult == true -> Color(0xFF2ECC71)
                        state.serverStatusResult == false -> MaterialTheme.colorScheme.error
                        else -> null
                    }
                    SettingsCard {
                        SettingsClickItem(
                            icon = Icons.Default.Cloud,
                            title = "Server Status",
                            subtitle = serverStatusSubtitle,
                            subtitleColor = serverStatusSubtitleColor,
                            enabled = !state.isRefreshingServerStatus,
                            trailing = {
                                if (state.isRefreshingServerStatus) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            },
                            onClick = { onAction(SettingsAction.OnServerStatusClicked) }
                        )
                    }
                }
            }

            // -----------------------------------------------------------------
            // Push to Google Sheets
            //
            // "Force Sync" above pulls the server's data down; these push the
            // app's data outward, which is a different enough intent to warrant
            // its own heading rather than more rows in the same card.
            // -----------------------------------------------------------------
            if (canPushTransactions || canPushInvestments) item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionLabel("Push to Sheets")
            }

            if (canPushTransactions || canPushInvestments) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val pendingCount = state.pendingSheetSyncCount
                    if (canPushTransactions) SettingsCard {
                        SyncActionItem(
                            icon = Icons.Default.UploadFile,
                            title = "Transactions",
                            idleSubtitle = when {
                                pendingCount == null -> "Send new transactions to your sheet"
                                pendingCount == 0 -> "Nothing pending"
                                else -> "$pendingCount waiting to be pushed"
                            },
                            task = state.sheetTransactionSync,
                            onClick = { onAction(SettingsAction.OnPushTransactionsToSheets) }
                        )
                    }
                    if (canPushInvestments) SettingsCard {
                        SyncActionItem(
                            icon = Icons.Default.ShowChart,
                            title = "Investments",
                            idleSubtitle = "Send new portfolio snapshots to your sheet",
                            task = state.sheetInvestmentSync,
                            onClick = { onAction(SettingsAction.OnPushInvestmentsToSheets) }
                        )
                    }
                }
            }

            // -----------------------------------------------------------------
            // Imports & reconciliation
            // -----------------------------------------------------------------
            if (canReconcile || canImportLetterboxd) item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionLabel("Import & reconcile")
            }

            if (canReconcile || canImportLetterboxd) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canReconcile) SettingsCard {
                        SyncActionItem(
                            icon = Icons.Default.Balance,
                            title = "Reconcile Balances",
                            idleSubtitle = "Compare tracked vs. bank balances",
                            task = state.balanceReconcile,
                            onClick = { onAction(SettingsAction.OnReconcileBalancesSheetVisible(true)) }
                        )
                    }
                    if (canImportLetterboxd) SettingsCard {
                        SyncActionItem(
                            icon = Icons.Default.Movie,
                            title = "Import from Letterboxd",
                            idleSubtitle = state.letterboxdUsername.takeIf { it.isNotBlank() }
                                ?.let { "Pull recent logs for @$it" }
                                ?: "Pull your recent film logs",
                            task = state.letterboxdSync,
                            onClick = { onAction(SettingsAction.OnLetterboxdDialogVisible(true)) }
                        )
                    }
                }
            }
        }
    }

    if (state.isLetterboxdDialogVisible) {
        LetterboxdUsernameDialog(
            initialUsername = state.letterboxdUsername,
            onConfirm = { onAction(SettingsAction.OnLetterboxdSyncStarted(it)) },
            onDismiss = { onAction(SettingsAction.OnLetterboxdDialogVisible(false)) }
        )
    }

    if (state.showReconcileBalancesSheet) {
        ReconcileBalancesSheet(
            accounts = state.reconcileAccounts,
            isLoadingAccounts = state.isLoadingReconcileAccounts,
            scanTask = state.balanceReconcile,
            onScanClicked = { onAction(SettingsAction.OnReconcileBalances) },
            onDismiss = { onAction(SettingsAction.OnReconcileBalancesSheetVisible(false)) }
        )
    }
}

// -----------------------------------------------------------------------------
// One manual sync row: idle copy while nothing is happening, live progress while
// it runs, and the outcome in success/error colour once it finishes.
// -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SyncActionItem(
    icon: ImageVector,
    title: String,
    idleSubtitle: String,
    task: SyncTaskState,
    onClick: () -> Unit
) {
    val subtitle = task.message ?: idleSubtitle
    val subtitleColor = when {
        task.isRunning -> MaterialTheme.colorScheme.primary
        task.isSuccess == true -> Color(0xFF2ECC71)
        task.isSuccess == false -> MaterialTheme.colorScheme.error
        else -> null
    }

    SettingsClickItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        subtitleColor = subtitleColor,
        enabled = !task.isRunning,
        trailing = {
            if (task.isRunning) {
                LoadingIndicator(modifier = Modifier.size(22.dp))
            }
        },
        onClick = onClick
    )
}

@Composable
private fun LetterboxdUsernameDialog(
    initialUsername: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf(initialUsername) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Movie, contentDescription = null) },
        title = { Text("Import from Letterboxd", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Reads your public RSS feed and adds anything not already logged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.trim() },
                    label = { Text("Username") },
                    prefix = { Text("@") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(username) },
                enabled = username.isNotBlank()
            ) {
                Text("Import", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

