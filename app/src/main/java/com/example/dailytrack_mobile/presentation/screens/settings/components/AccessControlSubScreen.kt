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
import com.example.dailytrack_mobile.presentation.access.LocalAccess
import com.example.dailytrack_mobile.presentation.screens.settings.AccessControlState
import com.example.dailytrack_mobile.presentation.screens.settings.AccessControlVM
import com.example.dailytrack_mobile.presentation.screens.settings.AccessDraft
import com.example.dailytrack_mobile.presentation.util.Dimens

private val AVATAR_COLORS = listOf(0xFF6366F1, 0xFF0EA5E9, 0xFF10B981, 0xFFF59E0B, 0xFFEC4899, 0xFF8B5CF6, 0xFF14B8A6, 0xFFF43F5E)

private fun avatarColor(email: String) = Color(AVATAR_COLORS[email.sumOf { it.code } % AVATAR_COLORS.size])

/** Admin only: who can sign in. Everyone who can has their own, separate data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccessControlSubScreen(
    onNavigateBack: () -> Unit,
    viewModel: AccessControlVM = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val dims = Dimens.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
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
            TopAppBar(
                title = {
                    Text(
                        text = draft?.email ?: "People",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                onAdd = viewModel::add,
                onEdit = viewModel::edit,
                onAnswer = viewModel::answer,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding)
            )
        } else {
            RoleEditor(
                draft = draft,
                isSelf = draft.email.equals(LocalAccess.current.email, ignoreCase = true),
                onRole = viewModel::setRole,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun PeopleList(
    state: AccessControlState,
    currentEmail: String,
    onAdd: (String) -> Unit,
    onEdit: (com.example.dailytrack_mobile.data.remote.dto.AccessUserDto) -> Unit,
    onAnswer: (String, Boolean) -> Unit,
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
                    enabled = newEmail.isNotBlank() && !state.isSaving,
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
                if (state.requests.isNotEmpty()) {
                    item { Spacer(Modifier.height(4.dp)); SettingsSectionLabel("Requests") }
                    items(state.requests, key = { "req-" + it.email }) { request ->
                        SettingsCard {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(avatarColor(request.email)),
                                    contentAlignment = Alignment.Center
                                ) { Text(request.email.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold) }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        request.email,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    request.name?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                TextButton(onClick = { onAnswer(request.email, false) }) { Text("Decline") }
                                Button(onClick = { onAnswer(request.email, true) }) { Text("Approve") }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(4.dp)); SettingsSectionLabel("Owners") }
                items(state.owners) { email ->
                    PersonRow(
                        email = email,
                        badge = "OWNER",
                        badgeColor = Color(0xFFF59E0B),
                        isYou = email.equals(currentEmail, ignoreCase = true),
                        onClick = null
                    )
                }
                item { Spacer(Modifier.height(4.dp)); SettingsSectionLabel("People") }
                if (state.users.isEmpty()) item {
                    Text(
                        "Nobody else yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                items(state.users, key = { it.email }) { user ->
                    val admin = user.role == "admin"
                    PersonRow(
                        email = user.email,
                        badge = if (admin) "ADMIN" else "MEMBER",
                        badgeColor = if (admin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
            Text(
                text = if (isYou) "$email (you)" else email,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
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

@Composable
private fun RoleEditor(
    draft: AccessDraft,
    isSelf: Boolean,
    onRole: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = dims.screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsSectionLabel("Role")
        val roles = listOf("member" to "Member", "admin" to "Admin")
        ConnectedToggleGroup(
            options = roles.map { it.first },
            selected = draft.role,
            onSelect = onRole,
            label = { key -> roles.first { it.first == key }.second },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = if (draft.role == "admin") "Can add and remove people. Never sees anyone else’s data."
            else "Uses DailyTrack with their own data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        if (isSelf && draft.role != "admin") {
            Text(
                text = "You won't be able to come back here.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFF59E0B),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
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
            OutlinedButton(
                onClick = onRemove,
                enabled = !isSaving,
                colors = if (draft.confirmRemove) ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) else ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(if (draft.confirmRemove) "Tap to confirm" else "Remove") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel, enabled = !isSaving) { Text("Cancel") }
            Button(onClick = onSave, enabled = !isSaving) { Text(if (isSaving) "Saving…" else "Save") }
        }
    }
}
