package com.example.dailytrack_mobile.presentation.screens.settings.components

import com.example.dailytrack_mobile.presentation.components.topBarIconButtonColors
import com.example.dailytrack_mobile.presentation.components.ConnectedToggleGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.data.local.auth.AccessLevel
import com.example.dailytrack_mobile.data.local.auth.AccessModule
import com.example.dailytrack_mobile.data.remote.dto.AccessUserDto
import com.example.dailytrack_mobile.presentation.access.LocalAccess
import com.example.dailytrack_mobile.presentation.screens.settings.AccessControlVM
import com.example.dailytrack_mobile.presentation.screens.settings.AccessDraft
import com.example.dailytrack_mobile.presentation.util.Dimens

private data class ModuleMeta(val module: AccessModule, val emoji: String, val label: String, val desc: String)

private val MODULE_META = listOf(
    ModuleMeta(AccessModule.MONEY, "💰", "Money", "Transactions, budgets, splits & adding"),
    ModuleMeta(AccessModule.GYM, "🏋️", "Activities", "Workout and sports log"),
    ModuleMeta(AccessModule.INVEST, "📈", "Investments", "Portfolio, holdings & assets"),
    ModuleMeta(AccessModule.SABDEKHO, "📺", "SabDekho", "Movies, shows & watch diary")
)

private val AVATAR_COLORS = listOf(0xFF6366F1, 0xFF0EA5E9, 0xFF10B981, 0xFFF59E0B, 0xFFEC4899, 0xFF8B5CF6, 0xFF14B8A6, 0xFFF43F5E)

private fun avatarColor(email: String) = Color(AVATAR_COLORS[email.sumOf { it.code } % AVATAR_COLORS.size])

private fun summarize(user: AccessUserDto): String {
    if (user.role == "admin") return "Full access · manages people"
    val parts = MODULE_META.mapNotNull { meta ->
        when (AccessLevel.from(user.permissions.modules[meta.module.key])) {
            AccessLevel.EDIT -> "${meta.emoji} Edit"
            AccessLevel.VIEW -> "${meta.emoji} View"
            AccessLevel.NONE -> null
        }
    }
    return if (parts.isEmpty()) "No pages shared yet" else parts.joinToString("  ·  ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccessControlSubScreen(
    onNavigateBack: () -> Unit,
    viewModel: AccessControlVM = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val dims = Dimens.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val draft = state.draft

    BackHandler { if (draft != null) viewModel.closeEditor() else onNavigateBack() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (draft == null) "Access Control" else if (draft.isNew) "Add person" else "Edit access",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (draft != null) {
                            Text(
                                text = draft.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    FilledIconButton(colors = topBarIconButtonColors(), onClick = { if (draft != null) viewModel.closeEditor() else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(dims.iconSizeMedium))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            if (draft != null) {
                EditorFooter(
                    draft = draft,
                    isSaving = state.isSaving,
                    onRemove = viewModel::remove,
                    onCancel = viewModel::closeEditor,
                    onSave = viewModel::save
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (draft == null) {
            PeopleList(
                state = state,
                currentEmail = LocalAccess.current.email,
                onAdd = viewModel::startAdd,
                onEdit = viewModel::edit,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding)
            )
        } else {
            DraftEditor(
                draft = draft,
                isSelf = draft.email.equals(LocalAccess.current.email, ignoreCase = true),
                allCategories = state.allCategories,
                allAccounts = state.allAccounts,
                onChange = viewModel::updateDraft,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun PeopleList(
    state: com.example.dailytrack_mobile.presentation.screens.settings.AccessControlState,
    currentEmail: String,
    onAdd: (String) -> Unit,
    onEdit: (AccessUserDto) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current
    var newEmail by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = dims.screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = dims.screenBottomPadding)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newEmail,
                    onValueChange = { newEmail = it },
                    placeholder = { Text("Add someone by email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAdd(newEmail); newEmail = "" }),
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onAdd(newEmail); newEmail = "" },
                    enabled = newEmail.isNotBlank(),
                    modifier = Modifier.height(56.dp)
                ) { Text("Add") }
            }
        }

        when {
            state.isLoading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            state.error != null -> item {
                SettingsCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Couldn't load people: ${state.error}", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }
            }
            else -> {
                item { Spacer(Modifier.height(4.dp)); SettingsSectionLabel("Owners") }
                items(state.owners) { email ->
                    PersonRow(
                        email = email,
                        subtitle = "Permanent · full access to everything",
                        badge = "OWNER",
                        badgeColor = Color(0xFFF59E0B),
                        isYou = email.equals(currentEmail, ignoreCase = true),
                        onClick = null
                    )
                }
                item { Spacer(Modifier.height(4.dp)); SettingsSectionLabel("People") }
                if (state.users.isEmpty()) item {
                    Text(
                        "Nobody else has access yet. Add someone above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                items(state.users, key = { it.email }) { user ->
                    PersonRow(
                        email = user.email,
                        subtitle = summarize(user),
                        badge = when {
                            user.legacy -> "LEGACY · FULL"
                            user.role == "admin" -> "ADMIN"
                            else -> "MEMBER"
                        },
                        badgeColor = when {
                            user.legacy -> MaterialTheme.colorScheme.error
                            user.role == "admin" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        isYou = user.email.equals(currentEmail, ignoreCase = true),
                        onClick = { onEdit(user) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonRow(
    email: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    isYou: Boolean,
    onClick: (() -> Unit)?
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(avatarColor(email)),
                contentAlignment = Alignment.Center
            ) {
                Text(email.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isYou) "$email (you)" else email,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = RoundedCornerShape(50), color = badgeColor.copy(alpha = 0.14f)) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            if (onClick != null) Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DraftEditor(
    draft: AccessDraft,
    isSelf: Boolean,
    allCategories: List<String>,
    allAccounts: List<String>,
    onChange: ((AccessDraft) -> AccessDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = dims.screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = dims.screenBottomPadding)
    ) {
        if (draft.legacy) item {
            Note("Added before access control, so they still have full edit access. Pick what they should have and save.", warn = true)
        }
        if (isSelf) item {
            Note("You're editing your own access. Remove your admin role and you won't be able to come back here.", warn = true)
        }

        item {
            SettingsSectionLabel("Role")
            Spacer(Modifier.height(8.dp))
            val roles = listOf("member" to "Member", "admin" to "Admin")
            ConnectedToggleGroup(
                options = roles.map { it.first },
                selected = draft.role,
                onSelect = { key -> onChange { it.copy(role = key) } },
                label = { key -> roles.first { it.first == key }.second },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (draft.role == "admin") {
            item { Note("Admins can see and change everything and manage who has access. Owners stay permanent.") }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SettingsSectionLabel("Pages") }
                    TextButton(onClick = { onChange { d -> d.copy(modules = AccessModule.entries.associateWith { AccessLevel.VIEW }) } }) { Text("View all") }
                    TextButton(onClick = { onChange { d -> d.copy(modules = AccessModule.entries.associateWith { AccessLevel.EDIT }) } }) { Text("Edit all") }
                }
            }
            items(MODULE_META) { meta ->
                val level = draft.modules[meta.module] ?: AccessLevel.NONE
                SettingsCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(meta.emoji, style = MaterialTheme.typography.titleLarge)
                            Column {
                                Text(meta.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                Text(meta.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val levels = AccessLevel.entries
                        ConnectedToggleGroup(
                            options = levels,
                            selected = level,
                            onSelect = { l -> onChange { d -> d.copy(modules = d.modules + (meta.module to l)) } },
                            label = { l -> l.key.replaceFirstChar { c -> c.uppercase() } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if ((draft.modules[AccessModule.MONEY] ?: AccessLevel.NONE) != AccessLevel.NONE) {
                item {
                    Spacer(Modifier.height(4.dp))
                    SettingsSectionLabel("Money scope")
                    Text(
                        "Limit which transactions they see. Totals, charts and budgets only count what they can see.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
                item {
                    ScopePicker("Categories", "category", allCategories, draft.categories) { v -> onChange { it.copy(categories = v) } }
                }
                item {
                    ScopePicker("Accounts", "account", allAccounts, draft.accounts) { v -> onChange { it.copy(accounts = v) } }
                }
                if (draft.categories != null) item {
                    Note("Account balances and net worth are hidden while categories are limited, because a balance includes every category.")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScopePicker(
    title: String,
    noun: String,
    options: List<String>,
    value: List<String>?,
    onChange: (List<String>?) -> Unit
) {
    val all = value == null
    val selected = value.orEmpty().toSet()
    SettingsCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        if (all) "Every $noun, including new ones" else "${selected.size} of ${options.size} selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("All", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
                Switch(checked = all, onCheckedChange = { on -> onChange(if (on) null else emptyList()) })
            }
            if (!all) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { opt ->
                        FilterChip(
                            selected = opt in selected,
                            onClick = { onChange((if (opt in selected) selected - opt else selected + opt).sorted()) },
                            label = { Text(opt) }
                        )
                    }
                }
                if (selected.isEmpty()) {
                    Text("No ${noun}s selected, so no transactions will be visible.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF59E0B))
                }
            }
        }
    }
}

@Composable
private fun Note(text: String, warn: Boolean = false) {
    val tint = if (warn) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary
    Surface(shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (warn) Icons.Default.Lock else Icons.Default.Info, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun EditorFooter(
    draft: AccessDraft,
    isSaving: Boolean,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val dims = Dimens.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = dims.screenHorizontalPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!draft.isNew) {
                OutlinedButton(
                    onClick = onRemove,
                    enabled = !isSaving,
                    colors = if (draft.confirmRemove) ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) else ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(if (draft.confirmRemove) "Tap to confirm" else "Remove") }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel, enabled = !isSaving) { Text("Cancel") }
            Button(onClick = onSave, enabled = !isSaving) {
                Text(if (isSaving) "Saving…" else if (draft.isNew) "Add person" else "Save")
            }
        }
    }
}
