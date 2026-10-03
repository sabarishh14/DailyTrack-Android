package com.example.dailytrack_mobile.presentation.screens.settings.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.presentation.components.topBarIconButtonColors
import com.example.dailytrack_mobile.presentation.screens.settings.SharingVM
import com.example.dailytrack_mobile.presentation.util.Dimens

/** The modules someone can share, as the server names them. */
internal val SHARE_MODULES = listOf(
    "money" to "💰 Money",
    "gym" to "🌱 Routines",
    "invest" to "📈 Investments",
    "sabdekho" to "📺 SabDekho"
)

private val AVATAR_COLORS = listOf(0xFF6366F1, 0xFF0EA5E9, 0xFF10B981, 0xFFF59E0B, 0xFFEC4899, 0xFF8B5CF6, 0xFF14B8A6, 0xFFF43F5E)
private fun avatarColor(email: String) = Color(AVATAR_COLORS[email.sumOf { it.code } % AVATAR_COLORS.size])

/** Settings → Sharing: let friends view your data (read-only), and view theirs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharingSubScreen(
    onNavigateBack: () -> Unit,
    viewModel: SharingVM = hiltViewModel()
) {
    BackHandler { onNavigateBack() }
    val state by viewModel.state.collectAsState()
    val viewAs by viewModel.viewAs.collectAsState()
    val withMe by viewModel.sharedWithMe.collectAsState()
    val dims = Dimens.current
    val snackbar = remember { SnackbarHostState() }
    var email by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(listOf("money")) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sharing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    FilledIconButton(colors = topBarIconButtonColors(), onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(dims.iconSizeMedium))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = dims.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = dims.screenBottomPadding)
        ) {
            // Share with someone
            if (viewAs == null) item {
                SettingsCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it; viewModel.clearError() },
                                placeholder = { Text("Friend's email") },
                                singleLine = true,
                                isError = state.error != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { viewModel.share(email, picked); email = "" }),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.share(email, picked); email = "" },
                                enabled = email.isNotBlank() && !state.busy,
                                modifier = Modifier.height(56.dp)
                            ) { Text("Share") }
                        }
                        ModuleChips(picked) { id -> picked = if (id in picked) picked - id else picked + id }
                        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }

            if (viewAs == null && state.mine.isNotEmpty()) {
                item { Spacer(Modifier.height(4.dp)); SettingsSectionLabel("They can view") }
                items(state.mine, key = { it.viewer }) { share ->
                    SettingsCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Avatar(share.viewer)
                                Text(
                                    share.viewer,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.stop(share) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Stop sharing", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            ModuleChips(share.modules) { id -> viewModel.toggle(share, id) }
                        }
                    }
                }
            }

            if (withMe.isNotEmpty()) {
                item { Spacer(Modifier.height(4.dp)); SettingsSectionLabel("Shared with you") }
                items(withMe, key = { it.owner }) { share ->
                    SettingsCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Avatar(share.owner)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    share.owner,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    SHARE_MODULES.filter { it.first in share.modules }.joinToString("  ·  ") { it.second },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (viewAs == share.owner) {
                                Text("Viewing", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            } else {
                                FilledTonalButton(onClick = { viewModel.switchView(share.owner); onNavigateBack() }) { Text("View") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModuleChips(selected: List<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SHARE_MODULES.forEach { (id, label) ->
            FilterChip(selected = id in selected, onClick = { onToggle(id) }, label = { Text(label) })
        }
    }
}

@Composable
private fun Avatar(email: String) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(avatarColor(email)),
        contentAlignment = Alignment.Center
    ) {
        Text(email.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/** Shown under the top bar while someone else's data is on screen. */
@Composable
fun ViewingBar(owner: String, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.primary.copy(alpha = 0.12f))
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("👀", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(8.dp))
        Text(
            text = owner,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(" · view only", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        FilledTonalButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp), modifier = Modifier.height(32.dp)) {
            Text("Back to mine", style = MaterialTheme.typography.labelMedium)
        }
    }
}
