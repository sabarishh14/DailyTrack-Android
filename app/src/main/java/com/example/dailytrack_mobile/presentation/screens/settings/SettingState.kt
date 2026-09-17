package com.example.dailytrack_mobile.presentation.screens.settings

import com.example.dailytrack_mobile.BuildConfig
import com.example.dailytrack_mobile.data.local.security.LockTimeout
import com.example.dailytrack_mobile.data.local.security.LockType
import com.example.dailytrack_mobile.presentation.theme.AppTheme
import com.example.dailytrack_mobile.presentation.theme.ThemeMode

import com.example.dailytrack_mobile.data.update.AppUpdateInfo
import java.io.File
import java.time.DayOfWeek

enum class UpdateStatus {
    IDLE,
    CHECKING,
    UPDATE_AVAILABLE,
    UP_TO_DATE,
    DOWNLOADING,
    READY_TO_INSTALL,
    ERROR
}

/**
 * Progress of one manual sync action. Each row on the Sync screen owns one of
 * these so a running action can report itself without four near-identical
 * triples of fields on [SettingsState].
 */
data class SyncTaskState(
    val isRunning: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean? = null
)

data class SettingsState(
    val selectedTheme: AppTheme = AppTheme.YELLOW,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val withAmoled: Boolean = false,
    val appVersion: String = "v${BuildConfig.VERSION_NAME}",
    val developerName: String = "Sabarish SB",
    val isAppLockEnabled: Boolean = false,
    val lockType: LockType = LockType.SYSTEM,
    val lockTimeout: LockTimeout = LockTimeout.IMMEDIATELY,
    val isBiometricWithPinEnabled: Boolean = true,
    val hasCustomPin: Boolean = false,
    val isDemoModeEnabled: Boolean = false,
    val isSyncing: Boolean = false,
    val syncStatusMessage: String? = null,
    val isLastSyncSuccess: Boolean? = null,
    val syncStepDescription: String? = null,
    val sheetTransactionSync: SyncTaskState = SyncTaskState(),
    val sheetInvestmentSync: SyncTaskState = SyncTaskState(),
    val balanceReconcile: SyncTaskState = SyncTaskState(),
    val letterboxdSync: SyncTaskState = SyncTaskState(),
    /** Transactions still queued for Sheets, or null before it has been checked. */
    val pendingSheetSyncCount: Int? = null,
    val isLetterboxdDialogVisible: Boolean = false,
    val letterboxdUsername: String = "",
    val isRefreshingServerStatus: Boolean = false,
    val serverStatusResult: Boolean? = null,
    val isReminderEnabled: Boolean = false,
    val reminderTime: String = "21:00",
    val reminderDays: Set<DayOfWeek> = DayOfWeek.values().toSet(),
    val loggedInUserEmail: String? = null,
    val loggedInUserName: String? = null,
    val isUserAdmin: Boolean = false,
    val updateStatus: UpdateStatus = UpdateStatus.IDLE,
    val latestUpdateInfo: AppUpdateInfo? = null,
    val downloadProgress: Float = 0f,
    val downloadedBytesText: String? = null,
    val downloadedApkFile: File? = null,
    val updateErrorMessage: String? = null,
    val showInstallPermissionDialog: Boolean = false
)