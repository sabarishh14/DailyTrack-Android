package com.example.dailytrack_mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
        val uri = intent?.data ?: return null
        return when {
            (uri.scheme == "dailytrack" && uri.host == "add_money") ||
            (uri.scheme == "myapp" && uri.host == "input_form") -> Routes.AddMoney.route
            else -> null
        }
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

            // Simple navigation state
            var currentScreen by rememberSaveable { mutableStateOf("Main") }

            // Switch to Main screen if a deep link is received while viewing Settings
            LaunchedEffect(pendingDeepLinkRoute) {
                if (pendingDeepLinkRoute != null) {
                    currentScreen = "Main"
                }
            }

            // App Lock State
            var isAppLocked by rememberSaveable { mutableStateOf(false) }
            var hasInitializedLock by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()

            // Initial lock check when app lock is loaded
            LaunchedEffect(state.isAppLockEnabled) {
                if (!hasInitializedLock && state.isAppLockEnabled) {
                    val timeout = state.lockTimeout
                    if (timeout == LockTimeout.IMMEDIATELY) {
                        isAppLocked = true
                    } else {
                        val lastBg = appLockManager.getLastBackgroundTimestamp()
                        if (lastBg == 0L) {
                            isAppLocked = true
                        } else {
                            val elapsedSeconds = (System.currentTimeMillis() - lastBg) / 1000
                            if (elapsedSeconds >= timeout.seconds) {
                                isAppLocked = true
                            }
                        }
                    }
                    hasInitializedLock = true
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
            DisposableEffect(lifecycleOwner, state.isAppLockEnabled, state.lockTimeout) {
                val observer = LifecycleEventObserver { _, event ->
                    if (state.isAppLockEnabled) {
                        when (event) {
                            Lifecycle.Event.ON_STOP -> {
                                val now = System.currentTimeMillis()
                                coroutineScope.launch {
                                    appLockManager.setLastBackgroundTimestamp(now)
                                }
                                if (state.lockTimeout == LockTimeout.IMMEDIATELY) {
                                    isAppLocked = true
                                }
                            }
                            Lifecycle.Event.ON_START -> {
                                if (hasInitializedLock && !isAppLocked) {
                                    val timeout = state.lockTimeout
                                    if (timeout != LockTimeout.IMMEDIATELY) {
                                        coroutineScope.launch {
                                            val lastBg = appLockManager.getLastBackgroundTimestamp()
                                            if (lastBg > 0L) {
                                                val elapsedSeconds = (System.currentTimeMillis() - lastBg) / 1000
                                                if (elapsedSeconds >= timeout.seconds) {
                                                    isAppLocked = true
                                                }
                                            }
                                        }
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
                                    targetRoute = pendingDeepLinkRoute,
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