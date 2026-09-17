package com.example.dailytrack_mobile.presentation.screens.settings

import com.example.dailytrack_mobile.data.local.security.LockTimeout
import com.example.dailytrack_mobile.data.local.security.LockType
import com.example.dailytrack_mobile.presentation.theme.AppTheme
import com.example.dailytrack_mobile.presentation.theme.ThemeMode

import java.time.DayOfWeek
import java.time.LocalTime

sealed interface SettingsAction {
    data class OnThemeChanged(val newTheme: AppTheme) : SettingsAction
    data class OnThemeModeChanged(val newMode: ThemeMode) : SettingsAction
    data class OnAmoledToggled(val enabled: Boolean) : SettingsAction
    object OnBackClicked : SettingsAction
    data class OnAppLockToggled(val enabled: Boolean) : SettingsAction
    data class OnLockTypeSelected(val lockType: LockType) : SettingsAction
    data class OnLockTimeoutSelected(val timeout: LockTimeout) : SettingsAction
    data class OnSaveCustomPin(val pin: String) : SettingsAction
    data class OnBiometricWithPinToggled(val enabled: Boolean) : SettingsAction
    data class OnDemoModeToggled(val enabled: Boolean) : SettingsAction
    object OnResetDemoDataClicked : SettingsAction
    object OnForceSyncClicked : SettingsAction

    // Outbound syncs & reconciliation
    object OnSyncScreenOpened : SettingsAction
    object OnPushTransactionsToSheets : SettingsAction
    object OnPushInvestmentsToSheets : SettingsAction
    object OnReconcileBalances : SettingsAction
    data class OnLetterboxdDialogVisible(val visible: Boolean) : SettingsAction
    data class OnLetterboxdSyncStarted(val username: String) : SettingsAction
    object OnServerStatusClicked : SettingsAction
    data class OnReminderToggled(val enabled: Boolean) : SettingsAction
    data class OnReminderTimeChanged(val time: LocalTime) : SettingsAction
    data class OnReminderDayToggled(val day: DayOfWeek) : SettingsAction
    object OnSendTestNotification : SettingsAction
    object OnLogoutClicked : SettingsAction
    object OnCheckForUpdatesClicked : SettingsAction
    object OnStartUpdateDownload : SettingsAction
    object OnInstallDownloadedApk : SettingsAction
    object OnDismissInstallPermissionDialog : SettingsAction
    object OnOpenInstallPermissionSettings : SettingsAction
}