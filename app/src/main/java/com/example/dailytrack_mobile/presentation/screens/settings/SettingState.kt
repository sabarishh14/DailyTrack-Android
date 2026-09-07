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