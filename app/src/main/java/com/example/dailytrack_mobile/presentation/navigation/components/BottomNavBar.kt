package com.example.dailytrack_mobile.presentation.navigation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
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

/** Space above the bar where page content fades into the background. */
private val FadeHeight = 24.dp

/** The bar's gap from the bottom edge (M3 floating toolbar screen offset). */
private val ScreenOffset = 16.dp

/**
 * Floating nav bar: icon-only tabs in a pill, with the optional "Add" FAB beside
 * it. [onFabClick] = null hides the FAB.
 *
 * Built to stand apart from whatever scrolls beneath it, since cards use the
 * same surfaceContainer tones a plain toolbar would:
 *  - the pill takes a tone above every card (surfaceBright in dark themes, white
 *    in light ones) with a hairline rim, which reads where shadows don't;
 *  - page content fades into the background behind it instead of running into it.
 */
@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onFabClick: (() -> Unit)? = null,
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
            label = "Routines",
            route = Routes.Routines.route,
            selectedIcon = Icons.Filled.TaskAlt,
            unselectedIcon = Icons.Outlined.TaskAlt
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
            Routes.Routines.route -> access.canView(AccessModule.GYM)
            Routes.Investments.route -> access.canView(AccessModule.INVEST)
            Routes.Sabdekho.route -> access.canView(AccessModule.SABDEKHO)
            else -> true
        }
    }

    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    // The brighter of the two top tones: most themes put surfaceBright highest,
    // but June OLED has surfaceContainerHighest above it.
    val pillColor = if (isDark) {
        maxOf(colors.surfaceBright, colors.surfaceContainerHighest, compareBy { it.luminance() })
    } else {
        colors.surfaceContainerLowest
    }
    val rimColor = if (isDark) colors.onSurface.copy(alpha = 0.12f) else colors.outlineVariant.copy(alpha = 0.7f)
    val background = colors.background

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // Content fades out under the bar; the fade doesn't take touches, so
            // whatever sits beneath it stays scrollable and tappable.
            .background(
                Brush.verticalGradient(
                    0f to background.copy(alpha = 0f),
                    0.45f to background.copy(alpha = 0.8f),
                    1f to background
                )
            )
            .padding(top = FadeHeight, bottom = ScreenOffset)
    ) {
        // Sized from the width there is, so a narrow screen (or display zoom)
        // narrows the tabs instead of squeezing the Add button out of shape.
        val fabSize = if (maxWidth < 360.dp) 52.dp else 56.dp
        val fabSpace = if (onFabClick != null) fabSize + FabGap else 0.dp
        val pillChrome = 14.dp // the pill's inner padding and rim
        val perTab = (maxWidth - SideMargin * 2 - fabSpace - pillChrome) / items.size.coerceAtLeast(1)
        val tabWidth = (perTab - TabGap * 2).coerceIn(MinTabWidth, MaxTabWidth)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FabGap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = pillColor,
                contentColor = colors.onSurfaceVariant,
                border = BorderStroke(1.dp, rimColor),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEach { item ->
                        ToolbarTab(
                            item = item,
                            selected = currentRoute == item.route,
                            width = tabWidth,
                            onClick = { onNavigate(item.route) }
                        )
                    }
                }
            }

            if (onFabClick != null) {
                FloatingActionButton(
                    onClick = onFabClick,
                    shape = RoundedCornerShape(20.dp),
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 12.dp, pressedElevation = 8.dp),
                    modifier = Modifier.size(fabSize)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
            }
        }
    }
}

private val SideMargin = 12.dp
private val FabGap = 8.dp
private val TabGap = 2.dp
private val MinTabWidth = 36.dp
private val MaxTabWidth = 52.dp

@Composable
private fun ToolbarTab(
    item: BottomNavItem,
    selected: Boolean,
    width: Dp,
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
            .padding(vertical = 4.dp, horizontal = TabGap)
            .size(width, 40.dp)
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
