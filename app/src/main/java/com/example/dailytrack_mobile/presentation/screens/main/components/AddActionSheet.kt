package com.example.dailytrack_mobile.presentation.screens.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dailytrack_mobile.presentation.navigation.Routes
import com.example.dailytrack_mobile.data.local.auth.AccessModule
import com.example.dailytrack_mobile.presentation.access.LocalAccess
import com.example.dailytrack_mobile.presentation.util.Dimens

data class ActionItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddActionSheet(
    onActionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val access = LocalAccess.current
    val actions = listOf(
        ActionItem(
            title = "Money",
            subtitle = "Transaction",
            icon = Icons.Default.CurrencyRupee,
            route = Routes.AddMoney.route
        ),
        ActionItem(
            title = "Activity",
            subtitle = "Workout / Habit",
            icon = Icons.Default.FitnessCenter,
            route = Routes.AddActivity.route
        ),
        ActionItem(
            title = "Movie",
            subtitle = "Cinema & Shows",
            icon = Icons.Default.Movie,
            route = Routes.AddMovie.route
        ),
        ActionItem(
            title = "Asset",
            subtitle = "Manual & FD",
            icon = Icons.Default.AccountBalance,
            route = Routes.AddAsset.route
        )
    ).filter { addActionAllowed(it.route, access) }
val dims = Dimens.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        contentWindowInsets = com.example.dailytrack_mobile.presentation.components.SheetContentInsets,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        // Wraps its content: the sheet is only as tall as the options need.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = dims.screenHorizontalPadding,
                    end = dims.screenHorizontalPadding,
                    bottom = dims.itemSpacingLarge
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
        ) {
            Text(
                text = "What would you like to add?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = dims.itemSpacingSmall)
            )

            actions.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
                ) {
                    row.forEach { action ->
                        ActionCard(
                            item = action,
                            onClick = {
                                onActionSelected(action.route)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Keep a lone last option half-width, aligned with the grid.
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ActionCard(
    item: ActionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(dims.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dims.itemSpacingMedium + 2.dp, vertical = dims.itemSpacingMedium + 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
        ) {
            Box(
                modifier = Modifier
                    .size(dims.avatarSizeMedium)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(dims.iconSizeMedium)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** Which "Add" shortcut needs which module's edit access. */
fun addActionAllowed(route: String, access: com.example.dailytrack_mobile.data.local.auth.AccessInfo): Boolean = when (route) {
    Routes.AddMoney.route -> access.canEdit(AccessModule.MONEY)
    Routes.AddActivity.route -> access.canEdit(AccessModule.GYM)
    Routes.AddMovie.route -> access.canEdit(AccessModule.SABDEKHO)
    Routes.AddAsset.route, Routes.AddInvestment.route, Routes.SyncBroker.route -> access.canEdit(AccessModule.INVEST)
    else -> true
}
