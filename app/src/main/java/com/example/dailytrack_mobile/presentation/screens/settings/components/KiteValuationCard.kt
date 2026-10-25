package com.example.dailytrack_mobile.presentation.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.presentation.components.ConnectedToggleGroup
import com.example.dailytrack_mobile.presentation.screens.settings.KiteValuationVM

private val KiteModes = listOf("manual", "daily")

/** Kite holdings: Manual (worth changes with a sync) or Daily (repriced every day). */
@Composable
internal fun KiteValuationCard(viewModel: KiteValuationVM = hiltViewModel()) {
    val mode by viewModel.mode.collectAsState()
    val error by viewModel.error.collectAsState()
    val colors = MaterialTheme.colorScheme

    SettingsCard {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(11.dp), color = colors.primary.copy(alpha = 0.12f), modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ShowChart, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Kite holdings",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = error ?: when (mode) {
                            "daily" -> "Repriced every day. Sync Kite after you buy or sell."
                            "manual" -> "Their worth changes when you sync Kite."
                            else -> "…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (error != null) colors.error else colors.onSurfaceVariant
                    )
                }
            }
            ConnectedToggleGroup(
                options = KiteModes,
                selected = mode,
                onSelect = viewModel::pick,
                label = { if (it == "daily") "Daily" else "Manual" },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
