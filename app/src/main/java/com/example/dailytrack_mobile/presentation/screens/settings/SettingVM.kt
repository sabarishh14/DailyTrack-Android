package com.example.dailytrack_mobile.presentation.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.local.datastore.DemoModeManager
import com.example.dailytrack_mobile.data.local.datastore.ThemeManager
import com.example.dailytrack_mobile.data.local.demo.DemoDataManager
import com.example.dailytrack_mobile.data.local.security.AppLockManager
import com.example.dailytrack_mobile.data.repository.ActivitiesRepository
import com.example.dailytrack_mobile.data.repository.InvestmentsRepository
import com.example.dailytrack_mobile.data.repository.MoneyRepository
import com.example.dailytrack_mobile.data.repository.SabdekhoRepository
import com.example.dailytrack_mobile.presentation.theme.AppTheme
import com.example.dailytrack_mobile.presentation.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.dailytrack_mobile.data.local.reminder.ReminderManager
import com.example.dailytrack_mobile.domain.reminder.ReminderScheduler
import com.example.dailytrack_mobile.notification.NotificationsHelper
import com.example.dailytrack_mobile.data.update.AppUpdateInfo
import com.example.dailytrack_mobile.data.update.AppUpdateManager
import com.example.dailytrack_mobile.data.update.UpdateDownloadProgress
import java.io.File
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsVM @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val themeManager: ThemeManager,
    private val appLockManager: AppLockManager,
    private val demoModeManager: DemoModeManager,
    private val demoDataManager: DemoDataManager,
    private val reminderManager: ReminderManager? = null,
    private val reminderScheduler: ReminderScheduler? = null,
    private val moneyRepository: MoneyRepository? = null,
    private val activitiesRepository: ActivitiesRepository? = null,
    private val investmentsRepository: InvestmentsRepository? = null,
    private val sabdekhoRepository: SabdekhoRepository? = null,
    private val authRepository: com.example.dailytrack_mobile.data.repository.AuthRepository? = null,
    private val appUpdateManager: AppUpdateManager? = null
) : ViewModel() {

    constructor(
        context: Context,
        themeManager: ThemeManager,
        appLockManager: AppLockManager,
        demoModeManager: DemoModeManager,
        demoDataManager: DemoDataManager
    ) : this(
        context = context,
        themeManager = themeManager,
        appLockManager = appLockManager,
        demoModeManager = demoModeManager,
        demoDataManager = demoDataManager,
        reminderManager = null,
        reminderScheduler = null,
        moneyRepository = null,
        activitiesRepository = null,
        investmentsRepository = null,
        sabdekhoRepository = null,
        authRepository = null,
        appUpdateManager = null
    )

    private val _isInitialConfigLoaded = MutableStateFlow(themeManager.hasSyncCache())
    val isInitialConfigLoaded = _isInitialConfigLoaded.asStateFlow()

    private val _state = MutableStateFlow(
        SettingsState(
            selectedTheme = themeManager.getInitialTheme(),
            themeMode = themeManager.getInitialThemeMode(),
            withAmoled = themeManager.getInitialAmoled()
        )
    )
    val state = _state.asStateFlow()

    init {
        // Fallback safeguard to ensure splash screen is never stuck indefinitely
        viewModelScope.launch {
            delay(1200)
            _isInitialConfigLoaded.value = true
        }

        // Listen for theme changes from DataStore on startup
        viewModelScope.launch {
            themeManager.themeFlow.collect { savedThemeName ->
                try {
                    _state.update { it.copy(selectedTheme = AppTheme.valueOf(savedThemeName)) }
                } catch (e: Exception) {
                    _state.update { it.copy(selectedTheme = AppTheme.YELLOW) }
                }
                _isInitialConfigLoaded.value = true
            }
        }

        // Listen for theme mode changes (SYSTEM, LIGHT, DARK)
        viewModelScope.launch {
            themeManager.themeModeFlow.collect { savedModeName ->
                try {
                    _state.update { it.copy(themeMode = ThemeMode.valueOf(savedModeName)) }
                } catch (e: Exception) {
                    _state.update { it.copy(themeMode = ThemeMode.SYSTEM) }
                }
            }
        }

        // Listen for Amoled / True black changes
        viewModelScope.launch {
            themeManager.amoledFlow.collect { isAmoled ->
                _state.update { it.copy(withAmoled = isAmoled) }
            }
        }

        // Listen for app lock settings changes
        viewModelScope.launch {
            appLockManager.isAppLockEnabledFlow.collect { enabled ->
                _state.update { it.copy(isAppLockEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            appLockManager.lockTypeFlow.collect { lockType ->
                _state.update { it.copy(lockType = lockType) }
            }
        }

        viewModelScope.launch {
            appLockManager.lockTimeoutFlow.collect { timeout ->
                _state.update { it.copy(lockTimeout = timeout) }
            }
        }

        viewModelScope.launch {
            appLockManager.isBiometricWithPinEnabledFlow.collect { enabled ->
                _state.update { it.copy(isBiometricWithPinEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            appLockManager.hasCustomPinFlow.collect { hasPin ->
                _state.update { it.copy(hasCustomPin = hasPin) }
            }
        }

        // Listen for Demo Mode changes
        viewModelScope.launch {
            demoModeManager.isDemoModeEnabledFlow.collect { enabled ->
                _state.update { it.copy(isDemoModeEnabled = enabled) }
            }
        }

        // Listen for Reminder settings changes
        reminderManager?.let { rm ->
            viewModelScope.launch {
                rm.isReminderEnabledFlow.collect { enabled ->
                    _state.update { it.copy(isReminderEnabled = enabled) }
                }
            }
            viewModelScope.launch {
                rm.reminderTimeFlow.collect { time ->
                    _state.update { it.copy(reminderTime = time) }
                }
            }
            viewModelScope.launch {
                rm.reminderDaysFlow.collect { days ->
                    _state.update { it.copy(reminderDays = days) }
                }
            }
        }

        authRepository?.let { repo ->
            viewModelScope.launch {
                repo.userEmailFlow.collect { email ->
                    _state.update { it.copy(loggedInUserEmail = email) }
                }
            }
            viewModelScope.launch {
                repo.userNameFlow.collect { name ->
                    _state.update { it.copy(loggedInUserName = name) }
                }
            }
            viewModelScope.launch {
                repo.isAdminFlow.collect { isAdmin ->
                    _state.update { it.copy(isUserAdmin = isAdmin) }
                }
            }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.OnThemeChanged -> {
                viewModelScope.launch {
                    themeManager.saveTheme(action.newTheme.name)
                }
            }
            is SettingsAction.OnThemeModeChanged -> {
                viewModelScope.launch {
                    themeManager.saveThemeMode(action.newMode.name)
                }
            }
            is SettingsAction.OnAmoledToggled -> {
                viewModelScope.launch {
                    themeManager.saveAmoled(action.enabled)
                }
            }
            is SettingsAction.OnBackClicked -> {
                // Navigation is hoisted to the Screen composable
            }
            is SettingsAction.OnAppLockToggled -> {
                viewModelScope.launch {
                    appLockManager.setAppLockEnabled(action.enabled)
                }
            }
            is SettingsAction.OnLockTypeSelected -> {
                viewModelScope.launch {
                    appLockManager.setLockType(action.lockType)
                }
            }
            is SettingsAction.OnLockTimeoutSelected -> {
                viewModelScope.launch {
                    appLockManager.setLockTimeout(action.timeout)
                }
            }
            is SettingsAction.OnSaveCustomPin -> {
                viewModelScope.launch {
                    appLockManager.savePin(action.pin)
                    appLockManager.setAppLockEnabled(true)
                }
            }
            is SettingsAction.OnBiometricWithPinToggled -> {
                viewModelScope.launch {
                    appLockManager.setBiometricWithPinEnabled(action.enabled)
                }
            }
            is SettingsAction.OnDemoModeToggled -> {
                viewModelScope.launch {
                    demoModeManager.setDemoModeEnabled(action.enabled)
                }
            }
            is SettingsAction.OnResetDemoDataClicked -> {
                viewModelScope.launch {
                    demoDataManager.resetDemoData()
                }
            }
            is SettingsAction.OnForceSyncClicked -> {
                forceSyncAllPages()
            }
            is SettingsAction.OnServerStatusClicked -> {
                checkServerStatus()
            }
            is SettingsAction.OnReminderToggled -> {
                viewModelScope.launch {
                    reminderManager?.setReminderEnabled(action.enabled)
                    if (action.enabled) {
                        val time = runCatching { LocalTime.parse(_state.value.reminderTime) }.getOrDefault(LocalTime.of(21, 0))
                        reminderScheduler?.scheduleReminder(time, _state.value.reminderDays)
                    } else {
                        reminderScheduler?.cancelReminder()
                    }
                }
            }
            is SettingsAction.OnReminderTimeChanged -> {
                viewModelScope.launch {
                    val timeStr = action.time.toString()
                    reminderManager?.setReminderTime(timeStr)
                    if (_state.value.isReminderEnabled) {
                        reminderScheduler?.scheduleReminder(action.time, _state.value.reminderDays)
                    }
                }
            }
            is SettingsAction.OnReminderDayToggled -> {
                viewModelScope.launch {
                    val currentDays = _state.value.reminderDays
                    val newDays = if (currentDays.contains(action.day)) {
                        currentDays - action.day
                    } else {
                        currentDays + action.day
                    }
                    reminderManager?.setReminderDays(newDays)
                    if (_state.value.isReminderEnabled) {
                        val time = runCatching { LocalTime.parse(_state.value.reminderTime) }.getOrDefault(LocalTime.of(21, 0))
                        reminderScheduler?.scheduleReminder(time, newDays)
                    }
                }
            }
            is SettingsAction.OnSendTestNotification -> {
                NotificationsHelper(context).showReminderNotification()
            }
            is SettingsAction.OnLogoutClicked -> {
                viewModelScope.launch {
                    authRepository?.logout()
                }
            }
            is SettingsAction.OnCheckForUpdatesClicked -> {
                checkForUpdates()
            }
            is SettingsAction.OnStartUpdateDownload -> {
                startUpdateDownload()
            }
            is SettingsAction.OnInstallDownloadedApk -> {
                installDownloadedApk()
            }
            is SettingsAction.OnDismissInstallPermissionDialog -> {
                _state.update { it.copy(showInstallPermissionDialog = false) }
            }
            is SettingsAction.OnOpenInstallPermissionSettings -> {
                _state.update { it.copy(showInstallPermissionDialog = false) }
                val updateMgr = appUpdateManager ?: AppUpdateManager(context)
                updateMgr.openInstallPermissionSettings()
            }
        }
    }

    private fun checkForUpdates() {
        if (_state.value.updateStatus == UpdateStatus.CHECKING || _state.value.updateStatus == UpdateStatus.DOWNLOADING) return
        val updateMgr = appUpdateManager ?: AppUpdateManager(context)
        viewModelScope.launch {
            _state.update { it.copy(updateStatus = UpdateStatus.CHECKING, updateErrorMessage = null) }
            val result = updateMgr.checkForUpdates()
            result.fold(
                onSuccess = { updateInfo ->
                    if (updateInfo == null) {
                        _state.update {
                            it.copy(
                                updateStatus = UpdateStatus.UP_TO_DATE,
                                latestUpdateInfo = null
                            )
                        }
                    } else if (updateInfo.isUpdateAvailable) {
                        _state.update {
                            it.copy(
                                updateStatus = UpdateStatus.UPDATE_AVAILABLE,
                                latestUpdateInfo = updateInfo
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                updateStatus = UpdateStatus.UP_TO_DATE,
                                latestUpdateInfo = updateInfo
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            updateStatus = UpdateStatus.ERROR,
                            updateErrorMessage = error.localizedMessage ?: "Failed to check for updates"
                        )
                    }
                }
            )
        }
    }

    private fun startUpdateDownload() {
        val updateInfo = _state.value.latestUpdateInfo ?: return
        val updateMgr = appUpdateManager ?: AppUpdateManager(context)
        viewModelScope.launch {
            _state.update {
                it.copy(
                    updateStatus = UpdateStatus.DOWNLOADING,
                    downloadProgress = 0f,
                    downloadedBytesText = "Connecting to server...",
                    updateErrorMessage = null
                )
            }
            try {
                updateMgr.downloadApk(updateInfo.downloadUrl, updateInfo.fileName).collect { progress ->
                    when (progress) {
                        is UpdateDownloadProgress.Progress -> {
                            val mbDownloaded = progress.bytesDownloaded.toDouble() / (1024 * 1024)
                            val totalMb = progress.totalBytes.toDouble() / (1024 * 1024)
                            val text = if (progress.totalBytes > 0) {
                                String.format(Locale.US, "%.1f MB / %.1f MB (%.0f%%)", mbDownloaded, totalMb, progress.progressPercent * 100)
                            } else {
                                String.format(Locale.US, "%.1f MB downloaded", mbDownloaded)
                            }
                            _state.update {
                                it.copy(
                                    downloadProgress = progress.progressPercent,
                                    downloadedBytesText = text
                                )
                            }
                        }
                        is UpdateDownloadProgress.Completed -> {
                            _state.update {
                                it.copy(
                                    updateStatus = UpdateStatus.READY_TO_INSTALL,
                                    downloadedApkFile = progress.apkFile,
                                    downloadProgress = 1f,
                                    downloadedBytesText = "Download complete"
                                )
                            }
                            // Auto-trigger installation prompt
                            installDownloadedApk(progress.apkFile)
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        updateStatus = UpdateStatus.ERROR,
                        updateErrorMessage = e.localizedMessage ?: "Download failed"
                    )
                }
            }
        }
    }

    private fun installDownloadedApk(file: File? = _state.value.downloadedApkFile) {
        val apkFile = file ?: _state.value.downloadedApkFile ?: return
        val updateMgr = appUpdateManager ?: AppUpdateManager(context)
        if (!updateMgr.canInstallPackages()) {
            _state.update { it.copy(showInstallPermissionDialog = true) }
            return
        }
        try {
            updateMgr.installApk(apkFile)
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    updateStatus = UpdateStatus.ERROR,
                    updateErrorMessage = "Failed to launch installer: ${e.localizedMessage}"
                )
            }
        }
    }

    private fun checkServerStatus() {
        if (_state.value.isRefreshingServerStatus) return
        viewModelScope.launch {
            _state.update { it.copy(isRefreshingServerStatus = true, serverStatusResult = null) }
            val result = moneyRepository?.checkHealth()?.getOrNull() ?: false
            _state.update { it.copy(isRefreshingServerStatus = false, serverStatusResult = result) }
        }
    }

    private fun forceSyncAllPages() {
        if (_state.value.isSyncing) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isSyncing = true,
                    syncStatusMessage = "Syncing all pages...",
                    syncStepDescription = "Connecting to server..."
                )
            }

            try {
                withContext(Dispatchers.IO) {
                    if (demoModeManager.isDemoModeEnabled()) {
                        _state.update { it.copy(syncStepDescription = "Re-hydrating demo workspace...") }
                        delay(600)
                        demoDataManager.notifyDataUpdated()
                    } else {
                        _state.update { it.copy(syncStepDescription = "Fetching latest accounts, transactions, and portfolio...") }

                        // Parallel re-hydration of all API endpoints across domains with forceRefresh = true
                        coroutineScope {
                            val moneyDeferred = async {
                                runCatching {
                                    moneyRepository?.getAccounts(forceRefresh = true)?.getOrThrow()
                                    moneyRepository?.getTransactions(limit = 100, offset = 0, forceRefresh = true)?.getOrThrow()
                                    moneyRepository?.getCategories(forceRefresh = true)?.getOrThrow()
                                }
                            }
                            val activitiesDeferred = async {
                                runCatching {
                                    activitiesRepository?.getPhysicalActivities(forceRefresh = true)?.getOrThrow()
                                }
                            }
                            val investmentsDeferred = async {
                                runCatching {
                                    investmentsRepository?.getFullPortfolio(forceRefresh = true)?.getOrThrow()
                                }
                            }
                            val sabdekhoDeferred = async {
                                runCatching {
                                    sabdekhoRepository?.getMediaLibrary(
                                        limit = 60,
                                        offset = 0,
                                        type = "all",
                                        status = "WATCHING",
                                        forceRefresh = true
                                    )?.getOrThrow()
                                }
                            }

                            val moneyRes = moneyDeferred.await()
                            val activitiesRes = activitiesDeferred.await()
                            val investmentsRes = investmentsDeferred.await()
                            val sabdekhoRes = sabdekhoDeferred.await()

                            val failures = listOfNotNull(
                                moneyRes?.exceptionOrNull()?.let { "Money: ${it.message}" },
                                activitiesRes?.exceptionOrNull()?.let { "Activities: ${it.message}" },
                                investmentsRes?.exceptionOrNull()?.let { "Investments: ${it.message}" },
                                sabdekhoRes?.exceptionOrNull()?.let { "Sabdekho: ${it.message}" }
                            )

                            if (failures.size == 4) {
                                throw Exception("All services failed to respond: ${failures.first()}")
                            }

                            _state.update { it.copy(syncStepDescription = "Re-hydrating application pages...") }

                            // Trigger data update flow so all active ViewModels re-hydrate with fresh cached data
                            demoDataManager.notifyDataUpdated()
                            delay(300)

                            if (failures.isNotEmpty()) {
                                throw Exception("Partial sync: ${failures.joinToString(", ")}")
                            }
                        }
                    }
                }

                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val timeStr = timeFormat.format(Date())
                _state.update {
                    it.copy(
                        isSyncing = false,
                        syncStatusMessage = "Synced at $timeStr",
                        isLastSyncSuccess = true,
                        syncStepDescription = null
                    )
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "All pages re-hydrated successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                val isPartial = e.message?.startsWith("Partial sync") == true
                val msg = if (isPartial) e.message ?: "Partial sync" else "Sync failed: ${e.localizedMessage ?: "Unknown error"}"
                _state.update {
                    it.copy(
                        isSyncing = false,
                        syncStatusMessage = msg,
                        isLastSyncSuccess = isPartial,
                        syncStepDescription = null
                    )
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}


class SettingsVMFactory(
    private val context: Context,
    private val themeManager: ThemeManager,
    private val appLockManager: AppLockManager,
    private val demoModeManager: DemoModeManager,
    private val demoDataManager: DemoDataManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsVM::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsVM(context, themeManager, appLockManager, demoModeManager, demoDataManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}