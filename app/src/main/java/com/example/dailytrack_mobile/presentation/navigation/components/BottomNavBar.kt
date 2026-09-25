package com.example.dailytrack_mobile.presentation.navigation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.dailytrack_mobile.presentation.navigation.Routes
import com.example.dailytrack_mobile.data.local.auth.AccessModule
import com.example.dailytrack_mobile.presentation.access.LocalAccess

data class BottomNavItem(
    val label: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/**
 * Floating M3 Expressive toolbar: icon-only tabs in a pill, with the optional
 * "Add" FAB docked to its end. [onFabClick] = null hides the FAB.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onFabClick: (() -> Unit)? = null,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    modifier: Modifier = Modifier
) {
    val access = LocalAccess.current
    val allItems = listOf(
        BottomNavItem(
            label = "Home",
            route = Routes.Home.route,
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home
        ),
        BottomNavItem(
            label = "Money",
            route = Routes.Money.route,
            selectedIcon = Icons.Filled.AccountBalanceWallet,
            unselectedIcon = Icons.Outlined.AccountBalanceWallet
        ),
        BottomNavItem(
            label = "Activities",
            route = Routes.Activities.route,
            selectedIcon = Icons.Filled.FitnessCenter,
            unselectedIcon = Icons.Outlined.FitnessCenter
        ),
        BottomNavItem(
            label = "Investments",
            route = Routes.Investments.route,
            selectedIcon = Icons.AutoMirrored.Filled.TrendingUp,
            unselectedIcon = Icons.AutoMirrored.Outlined.TrendingUp
        ),
        BottomNavItem(
            label = "Sabdekho",
            route = Routes.Sabdekho.route,
            selectedIcon = Icons.Filled.AutoStories,
            unselectedIcon = Icons.Outlined.AutoStories
        )
    )
    val items = allItems.filter { item ->
        when (item.route) {
            Routes.Money.route -> access.canView(AccessModule.MONEY)
            Routes.Activities.route -> access.canView(AccessModule.GYM)
            Routes.Investments.route -> access.canView(AccessModule.INVEST)
            Routes.Sabdekho.route -> access.canView(AccessModule.SABDEKHO)
            else -> true
        }
    }

    val colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
        toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
    val tabs: @Composable RowScope.() -> Unit = {
        items.forEach { item ->
            ToolbarTab(
                item = item,
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) }
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Clip so the toolbar vanishes at this box's edge when it scrolls away
            // (instead of peeking into the gesture-nav area); the top padding
            // leaves room for its shadow.
            .clipToBounds()
            .padding(top = 8.dp, bottom = FloatingToolbarDefaults.ScreenOffset),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (onFabClick != null) {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = colors,
                scrollBehavior = scrollBehavior,
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = onFabClick,
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                    }
                },
                content = tabs
            )
        } else {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = colors,
                scrollBehavior = scrollBehavior,
                content = tabs
            )
        }
    }
}

@Composable
private fun ToolbarTab(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "tabContainer"
    )
    val contentColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tabContent"
    )

    Surface(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .size(52.dp, 40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.label,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
