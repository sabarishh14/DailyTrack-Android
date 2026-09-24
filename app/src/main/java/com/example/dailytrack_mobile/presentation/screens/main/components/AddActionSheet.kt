package com.example.dailytrack_mobile.presentation.screens.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dailytrack_mobile.presentation.components.rememberSheetHeight
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(rememberSheetHeight(0.35f))
                .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingLarge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "What would you like to add?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = dims.sectionSpacing)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium),
                horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium),
                modifier = Modifier.padding(bottom = dims.screenBottomPadding)
            ) {
                items(actions) { action ->
                    ActionCard(
                        item = action,
                        onClick = {
                            onActionSelected(action.route)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ActionCard(
    item: ActionItem,
    onClick: () -> Unit
) {
    val dims = Dimens.current
    Surface(
        shape = RoundedCornerShape(dims.cardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dims.cardCornerRadius))
            .clickable { onClick() }
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
