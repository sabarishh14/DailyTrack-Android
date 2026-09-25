package com.example.dailytrack_mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.dailytrack_mobile.data.local.auth.AccessInfo
import com.example.dailytrack_mobile.presentation.access.LocalAccess
import com.example.dailytrack_mobile.data.local.security.AppLockManager
import com.example.dailytrack_mobile.data.local.security.LockTimeout
import com.example.dailytrack_mobile.data.repository.AuthRepository
import com.example.dailytrack_mobile.presentation.navigation.Routes
import com.example.dailytrack_mobile.presentation.screens.lock.AppLockScreen
import com.example.dailytrack_mobile.presentation.screens.login.LoginScreen
import com.example.dailytrack_mobile.presentation.screens.login.LoginViewModel
import com.example.dailytrack_mobile.presentation.screens.main.MainScreen
import com.example.dailytrack_mobile.presentation.screens.settings.SettingsAction
import com.example.dailytrack_mobile.presentation.screens.settings.SettingsScreen
import com.example.dailytrack_mobile.presentation.screens.settings.SettingsVM
import com.example.dailytrack_mobile.presentation.theme.DailyTrackTheme
import com.example.dailytrack_mobile.presentation.util.ProvideAppDimensions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var authRepository: AuthRepository

    private val settingsVM: SettingsVM by viewModels()
    private val loginVM: LoginViewModel by viewModels()

    private var pendingDeepLinkRoute by mutableStateOf<String?>(null)
    private var lastInteractionTime = System.currentTimeMillis()

    override fun onUserInteraction() {
        super.onUserInteraction()
        lastInteractionTime = System.currentTimeMillis()
    }

    private fun extractDeepLinkRoute(intent: Intent?): String? {
        if (intent == null) return null
        val uri = intent.data
        if (uri != null) {
            val uriStr = uri.toString().lowercase()
            if (uriStr.contains("add_money") || uriStr.contains("input_form")) {
                return Routes.AddMoney.route
            }
        }
        val shortcutId = intent.getStringExtra("android.intent.extra.shortcut.ID")
            ?: intent.getStringExtra("shortcut_id")
            ?: intent.getStringExtra("shortcutId")
        if (shortcutId != null && (shortcutId == "open_input_form" || shortcutId.contains("add_money") || shortcutId.contains("input_form"))) {
            return Routes.AddMoney.route
        }
        val extraRoute = intent.getStringExtra("route")
        if (!extraRoute.isNullOrBlank()) {
            if (extraRoute.contains("add_money") || extraRoute.contains("input_form")) {
                return Routes.AddMoney.route
            }
            return extraRoute
        }
        val action = intent.action
        if (action != null && (action.contains("ADD_MONEY") || action.contains("INPUT_FORM") || action.contains("open_input_form"))) {
            return Routes.AddMoney.route
        }
        return null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val route = extractDeepLinkRoute(intent)
        if (route != null) {
            pendingDeepLinkRoute = route
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold the splash screen until initial theme and auth session are loaded
        splashScreen.setKeepOnScreenCondition {
            !settingsVM.isInitialConfigLoaded.value || authRepository.isLoggedInFlow.value == null
        }

        enableEdgeToEdge()

        pendingDeepLinkRoute = extractDeepLinkRoute(intent)

        setContent {
            // Collect the settings state to get the currently selected theme and app lock
            val state by settingsVM.state.collectAsState()
            val isLoggedIn by authRepository.isLoggedInFlow.collectAsState()
            val loginState by loginVM.state.collectAsState()
            val storedAccess by authRepository.accessFlow.collectAsState()
            // Demo mode runs on local sample data, so everything is available there.
            val access = if (state.isDemoModeEnabled && isLoggedIn != true) AccessInfo.FULL
                else storedAccess ?: AccessInfo.NONE

            // Simple navigation state
            var currentScreen by rememberSaveable { mutableStateOf("Main") }

            // Switch to Main screen if a deep link is received while viewing Settings
            LaunchedEffect(pendingDeepLinkRoute) {
                if (pendingDeepLinkRoute != null) {
                    currentScreen = "Main"
                }
            }

            // App Lock State - initialized synchronously from fast cache to eliminate cold start race conditions
            val shouldInitiallyLock = remember { appLockManager.shouldLockOnColdStart() }
            var isAppLocked by rememberSaveable { mutableStateOf(shouldInitiallyLock) }
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(isAppLocked) {
                appLockManager.isSessionUnlocked = !isAppLocked
            }

            // Runtime lock check when app lock toggle or timeout setting changes
            LaunchedEffect(state.isAppLockEnabled, state.lockTimeout) {
                if (state.isAppLockEnabled && !isAppLocked) {
                    if (appLockManager.shouldLockOnColdStart()) {
                        isAppLocked = true
                    }
                }
            }

            // In-app idle lock: check periodically if user was inactive while app was left open
            LaunchedEffect(isAppLocked, state.isAppLockEnabled, state.lockTimeout) {
                if (!isAppLocked && state.isAppLockEnabled && state.lockTimeout != LockTimeout.IMMEDIATELY && state.lockTimeout.seconds > 0) {
                    while (true) {
                        kotlinx.coroutines.delay(2000L)
                        val idleSeconds = (System.currentTimeMillis() - lastInteractionTime) / 1000
                        if (idleSeconds >= state.lockTimeout.seconds) {
                            isAppLocked = true
                            break
                        }
                    }
                }
            }

            // Lifecycle observer for background/foreground transitions
            val lifecycleOwner = LocalLifecycleOwner.current

            // Keep permissions fresh while the app is in the foreground: role changes
            // apply live, and a removed user is signed out (via the 401 handler).
            LaunchedEffect(isLoggedIn, lifecycleOwner) {
                if (isLoggedIn == true) {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        while (true) {
                            authRepository.refreshAccess()
                            kotlinx.coroutines.delay(60_000L)
                        }
                    }
                }
            }
            DisposableEffect(lifecycleOwner, state.isAppLockEnabled, state.lockTimeout) {
                val activityId = System.identityHashCode(this@MainActivity)
                val observer = LifecycleEventObserver { _, event ->
                    if (state.isAppLockEnabled) {
                        when (event) {
                            Lifecycle.Event.ON_STOP -> {
                                // A newer activity instance (e.g. shortcut relaunch) already took over
                                if (appLockManager.foregroundActivityId != activityId) return@LifecycleEventObserver
                                val now = System.currentTimeMillis()
                                appLockManager.setLastBackgroundTimestampSync(now)
                                coroutineScope.launch {
                                    appLockManager.setLastBackgroundTimestamp(now)
                                }
                                if (state.lockTimeout == LockTimeout.IMMEDIATELY) {
                                    isAppLocked = true
                                }
                            }
                            Lifecycle.Event.ON_START -> {
                                appLockManager.foregroundActivityId = activityId
                                if (appLockManager.shouldLockOnResume(state.lockTimeout)) {
                                    isAppLocked = true
                                } else if (!isAppLocked) {
                                    // Back within the timeout: the background timestamp no longer applies
                                    coroutineScope.launch {
                                        appLockManager.clearLastBackgroundTimestamp()
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // Pass the state's theme configurations into DailyTrackTheme
            DailyTrackTheme(
                themeMode = state.themeMode,
                appTheme = state.selectedTheme,
                withAmoled = state.withAmoled
            ) {
                ProvideAppDimensions {
                    CompositionLocalProvider(LocalAccess provides access) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (isLoggedIn == false && !state.isDemoModeEnabled) {
                            LoginScreen(
                                state = loginState,
                                onAction = loginVM::onAction,
                                onLoginSuccess = { }
                            )
                        } else if (isAppLocked && state.isAppLockEnabled) {
                            AppLockScreen(
                                appLockManager = appLockManager,
                                onUnlocked = {
                                    isAppLocked = false
                                    lastInteractionTime = System.currentTimeMillis()
                                    coroutineScope.launch {
                                        appLockManager.clearLastBackgroundTimestamp()
                                    }
                                }
                            )
                        } else {
                            if (currentScreen == "Main") {
                                MainScreen(
                                    onNavigateToSettings = { currentScreen = "Settings" },
                                    targetRoute = if (!isAppLocked) pendingDeepLinkRoute else null,
                                    onRouteConsumed = { pendingDeepLinkRoute = null }
                                )
                            } else {
                                SettingsScreen(
                                    state = state,
                                    appLockManager = appLockManager,
                                    onAction = { action ->
                                        settingsVM.onAction(action)
                                        if (action is SettingsAction.OnBackClicked) {
                                            currentScreen = "Main"
                                        }
                                    }
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}