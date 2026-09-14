package com.example.dailytrack_mobile.presentation.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.data.remote.dto.MediaSearchResultDto
import com.example.dailytrack_mobile.presentation.navigation.Routes
import com.example.dailytrack_mobile.presentation.navigation.components.BottomNavBar
import com.example.dailytrack_mobile.presentation.screens.activities.ActivitiesScreen
import com.example.dailytrack_mobile.presentation.screens.home.HomeScreen
import com.example.dailytrack_mobile.presentation.screens.invest.InvestmentsScreen
import com.example.dailytrack_mobile.presentation.screens.money.MoneyAction
import com.example.dailytrack_mobile.presentation.screens.money.MoneyCashFlowTab
import com.example.dailytrack_mobile.presentation.screens.money.MoneyDialogsAndSheets
import com.example.dailytrack_mobile.presentation.screens.money.MoneyTransactionsTab
import com.example.dailytrack_mobile.presentation.screens.money.MoneyVM
import com.example.dailytrack_mobile.presentation.screens.sabdekho.SabdekhoScreen
import com.example.dailytrack_mobile.presentation.screens.forms.*
import com.example.dailytrack_mobile.presentation.screens.analytics.AnalyticsScreen
import com.example.dailytrack_mobile.presentation.screens.home.components.HomeTopBar
import com.example.dailytrack_mobile.presentation.screens.main.components.AddActionSheet
import com.example.dailytrack_mobile.presentation.util.Dimens
import kotlinx.coroutines.launch

private const val PAGE_HOME = 0
private const val PAGE_CASH_FLOW = 1
private const val PAGE_TRANSACTIONS = 2
private const val PAGE_ACTIVITIES = 3
private const val PAGE_INVESTMENTS = 4
private const val PAGE_SABDEKHO = 5
private const val MAIN_PAGES_COUNT = 6

private fun pageToRoute(page: Int): String = when (page) {
    PAGE_HOME -> Routes.Home.route
    PAGE_CASH_FLOW, PAGE_TRANSACTIONS -> Routes.Money.route
    PAGE_ACTIVITIES -> Routes.Activities.route
    PAGE_INVESTMENTS -> Routes.Investments.route
    PAGE_SABDEKHO -> Routes.Sabdekho.route
    else -> Routes.Home.route
}

private fun routeToPage(route: String, preferTransactions: Boolean = false): Int? = when (route) {
    Routes.Home.route -> PAGE_HOME
    Routes.Money.route -> if (preferTransactions) PAGE_TRANSACTIONS else PAGE_CASH_FLOW
    Routes.Activities.route -> PAGE_ACTIVITIES
    Routes.Investments.route -> PAGE_INVESTMENTS
    Routes.Sabdekho.route -> PAGE_SABDEKHO
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    targetRoute: String? = null,
    onRouteConsumed: () -> Unit = {}
) {
    val mainTabRoutes = remember {
        setOf(
            Routes.Home.route,
            Routes.Money.route,
            Routes.Activities.route,
            Routes.Investments.route,
            Routes.Sabdekho.route
        )
    }

    val initialPageIndex = remember {
        val target = targetRoute ?: Routes.Home.route
        routeToPage(target) ?: PAGE_HOME
    }
    val pagerState = rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { MAIN_PAGES_COUNT }
    )

    var currentRoute by remember { mutableStateOf(targetRoute ?: Routes.Home.route) }
    var lastMoneyPage by remember { mutableIntStateOf(PAGE_CASH_FLOW) }

    val moneyViewModel: MoneyVM = hiltViewModel()
    val moneyState by moneyViewModel.state.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val dims = Dimens.current

    // Update currentRoute and sync Money tab selection only when pager has fully settled
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settledPage ->
            if (settledPage == PAGE_CASH_FLOW || settledPage == PAGE_TRANSACTIONS) {
                lastMoneyPage = settledPage
                moneyViewModel.onAction(MoneyAction.SelectTab(if (settledPage == PAGE_CASH_FLOW) 0 else 1))
            }
            val settledRoute = pageToRoute(settledPage)
            if (currentRoute in mainTabRoutes && currentRoute != settledRoute) {
                currentRoute = settledRoute
            }
        }
    }

    // Show Money action message in Snackbar
    LaunchedEffect(moneyState.actionMessage) {
        moneyState.actionMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            moneyViewModel.onAction(MoneyAction.ClearActionMessage)
        }
    }

    // React when ViewModel selects a Money tab (e.g. from AnalysisTab "View Filtered Transactions")
    LaunchedEffect(moneyState.selectedTab) {
        val targetPage = if (moneyState.selectedTab == 0) PAGE_CASH_FLOW else PAGE_TRANSACTIONS
        if (pagerState.currentPage in listOf(PAGE_CASH_FLOW, PAGE_TRANSACTIONS) &&
            pagerState.currentPage != targetPage &&
            pagerState.targetPage != targetPage
        ) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var isCurrentFormDirty by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var pendingRoute by remember { mutableStateOf<String?>(null) }
    var preselectedMediaForAddMovie by remember { mutableStateOf<MediaSearchResultDto?>(null) }

    val formRoutes = remember {
        setOf(
            Routes.AddMoney.route,
            Routes.AddActivity.route,
            Routes.AddMovie.route,
            Routes.AddAsset.route,
            Routes.AddInvestment.route,
            Routes.SyncBroker.route
        )
    }

    val isFormScreen = currentRoute in formRoutes

    // Centralized safe navigation that checks for unsaved changes
    fun navigateSafely(targetRoute: String, preferTransactions: Boolean = false) {
        if (currentRoute == targetRoute && (targetRoute != Routes.Money.route || pagerState.currentPage == (if (preferTransactions) PAGE_TRANSACTIONS else lastMoneyPage))) return

        if (isFormScreen && isCurrentFormDirty) {
            pendingRoute = targetRoute
            showDiscardDialog = true
        } else {
            isCurrentFormDirty = false
            val tabIdx = if (targetRoute == Routes.Money.route) {
                if (preferTransactions) PAGE_TRANSACTIONS else lastMoneyPage
            } else {
                routeToPage(targetRoute)
            }
            if (tabIdx != null) {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(tabIdx)
                }
            }
            currentRoute = targetRoute
        }
    }

    LaunchedEffect(targetRoute) {
        if (targetRoute != null) {
            val tabIdx = if (targetRoute == Routes.Money.route) {
                lastMoneyPage
            } else {
                routeToPage(targetRoute)
            }
            if (tabIdx != null) {
                pagerState.scrollToPage(tabIdx)
                currentRoute = targetRoute
            } else {
                navigateSafely(targetRoute)
            }
            onRouteConsumed()
        }
    }

    // Handles form save completion
    fun onFormSaved(message: String, destinationRoute: String = Routes.Home.route) {
        isCurrentFormDirty = false
        preselectedMediaForAddMovie = null
        val tabIdx = if (destinationRoute == Routes.Money.route) {
            PAGE_TRANSACTIONS
        } else {
            routeToPage(destinationRoute)
        }
        if (tabIdx != null) {
            if (tabIdx == PAGE_TRANSACTIONS || tabIdx == PAGE_CASH_FLOW) {
                lastMoneyPage = tabIdx
            }
            coroutineScope.launch {
                pagerState.scrollToPage(tabIdx)
            }
        }
        currentRoute = destinationRoute
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Clear transaction selection on back gesture if in selection mode
    BackHandler(enabled = moneyState.isSelectionMode) {
        moneyViewModel.onAction(MoneyAction.ClearTransactionSelection)
    }

    // Intercept system/hardware back gesture when not on Home screen (and not in selection mode)
    BackHandler(enabled = !moneyState.isSelectionMode && currentRoute != Routes.Home.route) {
        if (currentRoute in mainTabRoutes) {
            coroutineScope.launch { pagerState.animateScrollToPage(PAGE_HOME) }
        } else {
            navigateSafely(Routes.Home.route)
        }
    }

    val screenTitle = when (currentRoute) {
        Routes.Home.route -> "Home"
        Routes.Money.route -> "Money"
        Routes.Activities.route -> "Activities"
        Routes.Investments.route -> "Investments"
        Routes.Sabdekho.route -> "Sabdekho"
        Routes.AddMoney.route -> "Add Money"
        Routes.AddActivity.route -> "Add Activity"
        Routes.AddMovie.route -> "Add Movie"
        Routes.AddAsset.route -> "Add Asset"
        Routes.AddInvestment.route -> "Add Investment"
        Routes.SyncBroker.route -> "Syncing Broker"
        Routes.Analytics.route -> "Analytics"
        else -> "DailyTrack"
    }

    if (showAddSheet) {
        AddActionSheet(
            onActionSelected = { route ->
                if (route == Routes.AddMovie.route) {
                    preselectedMediaForAddMovie = null
                }
                navigateSafely(route)
            },
            onDismiss = {
                showAddSheet = false
            }
        )
    }

    // Unsaved changes confirmation dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = {
                showDiscardDialog = false
                pendingRoute = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(dims.iconSizeLarge)
                )
            },
            title = {
                Text(
                    text = "Discard Unsaved Details?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "The entered details are not saved and will be lost. Are you sure you want to go back?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        isCurrentFormDirty = false
                        preselectedMediaForAddMovie = null
                        val destination = pendingRoute ?: Routes.Home.route
                        pendingRoute = null
                        currentRoute = destination
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(dims.buttonCornerRadius)
                ) {
                    Text("Discard", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDiscardDialog = false
                        pendingRoute = null
                    },
                    shape = RoundedCornerShape(dims.buttonCornerRadius)
                ) {
                    Text("Keep Editing", style = MaterialTheme.typography.labelLarge)
                }
            },
            shape = RoundedCornerShape(dims.cardCornerRadius),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    Scaffold(
        topBar = {
            if (currentRoute == Routes.Home.route) {
                HomeTopBar(
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToAnalytics = {
                        navigateSafely(Routes.Analytics.route)
                    }
                )
            } else if (currentRoute != Routes.Analytics.route) {
                Column {
                    TopAppBar(
                        navigationIcon = {
                            if (currentRoute != Routes.Home.route) {
                                IconButton(onClick = {
                                    if (currentRoute in mainTabRoutes) {
                                        coroutineScope.launch { pagerState.animateScrollToPage(PAGE_HOME) }
                                    } else {
                                        navigateSafely(Routes.Home.route)
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to Home",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(dims.iconSizeMedium)
                                    )
                                }
                            }
                        },
                        title = {
                            AnimatedContent(
                                targetState = screenTitle,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(120))
                                },
                                label = "TopBarTitleAnimation"
                            ) { titleText ->
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(dims.iconSizeMedium)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.statusBarsPadding()
                    )
                    // Subtle divider between topbar and canvas
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        },
        bottomBar = {
            if (!isFormScreen) {
                val displayRoute = if (currentRoute in mainTabRoutes) {
                    pageToRoute(pagerState.targetPage)
                } else {
                    currentRoute
                }
                BottomNavBar(
                    currentRoute = displayRoute,
                    onNavigate = { targetRoute ->
                        val targetPage = if (targetRoute == Routes.Money.route) {
                            if (pagerState.currentPage == PAGE_CASH_FLOW) PAGE_CASH_FLOW
                            else if (pagerState.currentPage == PAGE_TRANSACTIONS) PAGE_TRANSACTIONS
                            else lastMoneyPage
                        } else {
                            routeToPage(targetRoute)
                        }
                        if (targetPage != null) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(targetPage)
                            }
                        } else {
                            navigateSafely(targetRoute)
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isFormScreen && !(currentRoute == Routes.Money.route && moneyState.isSelectionMode)) {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        modifier = Modifier.size(dims.iconSizeLarge)
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = if (isFormScreen) dims.screenBottomPadding else dims.screenBottomPadding + 56.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            if (currentRoute in mainTabRoutes) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !moneyState.isSelectionMode,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        PAGE_HOME -> HomeScreen()
                        PAGE_CASH_FLOW -> MoneyCashFlowTab(
                            state = moneyState,
                            onAction = moneyViewModel::onAction,
                            onNavigateToTransactions = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(PAGE_TRANSACTIONS)
                                }
                            }
                        )
                        PAGE_TRANSACTIONS -> MoneyTransactionsTab(
                            state = moneyState,
                            onAction = moneyViewModel::onAction,
                            onNavigateToCashFlow = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(PAGE_CASH_FLOW)
                                }
                            }
                        )
                        PAGE_ACTIVITIES -> ActivitiesScreen()
                        PAGE_INVESTMENTS -> InvestmentsScreen()
                        PAGE_SABDEKHO -> SabdekhoScreen(
                            onNavigateToAddMovie = { media ->
                                preselectedMediaForAddMovie = media
                                navigateSafely(Routes.AddMovie.route)
                            }
                        )
                    }
                }

                // Render Money bottom sheets and dialogs once
                MoneyDialogsAndSheets(
                    state = moneyState,
                    onAction = moneyViewModel::onAction
                )
            } else {
                when (currentRoute) {
                    Routes.Analytics.route -> AnalyticsScreen(
                        onNavigateBack = { navigateSafely(Routes.Home.route) }
                    )
                    Routes.AddMoney.route -> AddMoneyScreen(
                        onDirtyStateChanged = { isCurrentFormDirty = it },
                        onSaveSuccess = { onFormSaved("Transaction saved successfully!", Routes.Money.route) }
                    )
                    Routes.AddActivity.route -> AddActivityScreen(
                        onDirtyStateChanged = { isCurrentFormDirty = it },
                        onSaveSuccess = { onFormSaved("Activity logged successfully!", Routes.Activities.route) }
                    )
                    Routes.AddMovie.route -> AddMovieScreen(
                        initialMedia = preselectedMediaForAddMovie,
                        onDirtyStateChanged = { isCurrentFormDirty = it },
                        onSaveSuccess = { onFormSaved("Title added successfully!", Routes.Sabdekho.route) }
                    )
                    Routes.AddAsset.route -> AddAssetScreen(
                        onDirtyStateChanged = { isCurrentFormDirty = it },
                        onSaveSuccess = { onFormSaved("Asset saved successfully!", Routes.Investments.route) }
                    )
                    Routes.AddInvestment.route -> AddInvestmentScreen(
                        onDirtyStateChanged = { isCurrentFormDirty = it },
                        onSaveSuccess = { onFormSaved("Investment recorded successfully!", Routes.Investments.route) }
                    )
                    Routes.SyncBroker.route -> SyncBrokerScreen(
                        onNavigateBack = { navigateSafely(Routes.Home.route) }
                    )
                }
            }
        }
    }
}
