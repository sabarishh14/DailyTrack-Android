package com.example.dailytrack_mobile.presentation.screens.vault

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.data.vault.VaultCrypto
import com.example.dailytrack_mobile.data.vault.VaultUi
import com.example.dailytrack_mobile.data.vault.VaultWords
import com.example.dailytrack_mobile.presentation.components.FullScreenPage
import com.example.dailytrack_mobile.presentation.components.navigationBarPadding
import com.example.dailytrack_mobile.presentation.screens.invest.components.PageHeader
import com.example.dailytrack_mobile.presentation.screens.settings.components.SettingsCard
import com.example.dailytrack_mobile.presentation.screens.settings.components.SettingsClickItem
import com.example.dailytrack_mobile.presentation.screens.settings.components.SettingsDivider
import com.example.dailytrack_mobile.presentation.screens.settings.components.SettingsNavItem
import com.example.dailytrack_mobile.presentation.util.Dimens

// The private vault's screens, as on the web (components/Vault.jsx): its row in
// Settings › Privacy & Security and the page it opens, the lock over Investments,
// and the 🔒 beside the portfolio's title.

private val PrivateGreen = Color(0xFF2ECC71)

// ── Settings ────────────────────────────────────────────────────────────────

/** Settings › Privacy & Security: the vault's row, opening its page. */
@Composable
internal fun VaultSettingsCard(vm: VaultVM = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    var open by rememberSaveable { mutableStateOf(false) }
    SettingsCard {
        SettingsNavItem(
            icon = if (ui.state == "on") Icons.Default.Lock else Icons.Default.LockOpen,
            title = "Private vault",
            subtitle = when {
                ui.state == "unknown" -> "Checking…"
                ui.state == "on" && ui.open -> "On · unlocked on this phone"
                ui.state == "on" -> "On · locked on this phone"
                else -> "Only you can read your investments"
            },
            onClick = { open = true }
        )
    }
    if (open) VaultPage(vm, onDismiss = { open = false })
}

@Composable
private fun VaultPage(vm: VaultVM, onDismiss: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val friends by vm.friends.collectAsState()
    val dims = Dimens.current
    var turningOn by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }   // passphrase | recovery | off
    LaunchedEffect(Unit) { vm.clearError() }

    FullScreenPage(onDismiss = { if (busy == null) onDismiss() }) {
        PageHeader(
            title = "Private vault",
            subtitle = when {
                ui.state == "on" && ui.open -> "On · unlocked on this phone"
                ui.state == "on" -> "On · locked on this phone"
                ui.moving -> "Moving your investments in…"
                else -> "Off"
            },
            onBack = { if (busy == null) onDismiss() }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dims.screenHorizontalPadding)
                .padding(bottom = navigationBarPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Hero(on = ui.state == "on")

            when {
                ui.state == "on" && ui.open -> {
                    SettingsCard {
                        SettingsClickItem(Icons.Default.Lock, "Lock on this phone", "The next open asks for your passphrase", enabled = busy == null) { vm.lock() }
                        SettingsDivider()
                        SettingsClickItem(Icons.Default.Key, "Change passphrase", "Nothing is locked again", enabled = busy == null) { dialog = "passphrase" }
                        SettingsDivider()
                        SettingsClickItem(Icons.Default.Receipt, "New recovery key", "Opens the vault if you forget the passphrase", enabled = busy == null) { dialog = "recovery" }
                        if (friends.isNotEmpty()) {
                            SettingsDivider()
                            SettingsClickItem(
                                Icons.Default.People, "Friends",
                                friends.joinToString("\n") { "${it.viewer}: ${if (it.granted) "can open it" else "once they turn on their vault"}" },
                                enabled = false
                            ) {}
                        }
                    }
                    SettingsCard {
                        SettingsClickItem(Icons.Default.LockOpen, "Turn off", "Store your investments readable on the server again", enabled = busy == null) { dialog = "off" }
                    }
                    busy?.let { Progress(it) }
                }
                ui.state == "on" -> Panel {
                    Text("Locked on this phone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    UnlockForm(vm)
                }
                turningOn -> TurnOnFlow(vm, ui, onCancel = { turningOn = false }, onDone = { turningOn = false })
                else -> Button(
                    onClick = { turningOn = true },
                    enabled = ui.state != "unknown" && !ui.moving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Turn on", fontWeight = FontWeight.SemiBold) }
            }
            if (!turningOn && ui.state != "on") error?.let { ErrorText(it) }
            if (ui.state == "on" && ui.open) error?.let { ErrorText(it) }

            HowItWorks()
        }
    }

    when (dialog) {
        "passphrase" -> PassphraseDialog(vm) { dialog = null }
        "recovery" -> RecoveryDialog(vm, ui.email.orEmpty()) { dialog = null }
        "off" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Turn off the private vault?") },
            text = { Text("Your investments go back to being stored readable on the server.") },
            confirmButton = { TextButton(onClick = { dialog = null; vm.turnOff {} }) { Text("Turn off") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Hero(on: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .background((if (on) PrivateGreen else colors.primary).copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (on) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = null,
                tint = if (on) PrivateGreen else colors.primary, modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("Only you can read your investments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            "Locked with a passphrase only you know. Not even the app's admin can read them.",
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp)
        )
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(Dimens.current.cardCornerRadius), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun ErrorText(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp))

@Composable
private fun Progress(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CheckRow(checked: Boolean, label: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) }.padding(end = 8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SecretField(
    value: String, onChange: (String) -> Unit, label: String,
    shown: Boolean = false, onToggleShown: (() -> Unit)? = null,
    isError: Boolean = false, supporting: String? = null, enabled: Boolean = true, mono: Boolean = false,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        isError = isError,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (shown) KeyboardType.Ascii else KeyboardType.Password,
            capitalization = if (mono) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
            autoCorrectEnabled = false, imeAction = ImeAction.Done
        ),
        textStyle = if (mono) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
        trailingIcon = onToggleShown?.let {
            {
                IconButton(onClick = it) {
                    Icon(if (shown) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (shown) "Hide" else "Show")
                }
            }
        },
        supportingText = supporting?.let { { Text(it) } },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Turning the vault on: a passphrase, the recovery key, then the move. */
@Composable
private fun TurnOnFlow(vm: VaultVM, ui: VaultUi, onCancel: () -> Unit, onDone: () -> Unit) {
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    var step by rememberSaveable { mutableStateOf(1) }
    var pass by rememberSaveable { mutableStateOf("") }
    var again by rememberSaveable { mutableStateOf("") }
    var shown by rememberSaveable { mutableStateOf(false) }
    var remember by rememberSaveable { mutableStateOf(true) }
    var saved by rememberSaveable { mutableStateOf(false) }
    val code = rememberSaveable { VaultCrypto.newRecoveryKey() }
    val working = busy != null
    val hasKeys = ui.hasKeys

    Panel {
        if (step == 1) {
            SecretField(
                pass, { pass = it }, if (hasKeys) "Your vault passphrase" else "Passphrase",
                shown = shown, onToggleShown = { shown = !shown }, enabled = !working,
                isError = pass.isNotEmpty() && !VaultVM.longEnough(pass),
                supporting = if (hasKeys) "The one you picked when you set it up before" else "At least ${VaultVM.MIN_PASSPHRASE} characters. A few words beat symbols."
            )
            if (!hasKeys) {
                SecretField(
                    again, { again = it }, "Once more", shown = shown, enabled = !working,
                    isError = again.isNotEmpty() && !VaultVM.same(again, pass),
                    supporting = if (again.isNotEmpty() && !VaultVM.same(again, pass)) "They don't match" else null
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { CheckRow(remember, "Remember on this phone", !working) { remember = it } }
                if (!hasKeys) TextButton(onClick = { VaultWords.suggest().let { pass = it; again = it; shown = true } }) { Text("Suggest one") }
                else TextButton(onClick = { vm.startOver(); pass = "" }, enabled = !working) { Text("Start over") }
            }
            busy?.let { Progress(it) }
            error?.let { ErrorText(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCancel, enabled = !working, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("Cancel") }
                Button(
                    onClick = { if (hasKeys) vm.turnOn(pass, code, remember, onDone) else step = 2 },
                    enabled = !working && VaultVM.longEnough(pass) && (hasKeys || VaultVM.same(pass, again)),
                    modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)
                ) { Text(if (hasKeys) "Lock" else "Next", fontWeight = FontWeight.SemiBold) }
            }
        } else {
            Text("Your recovery key", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "It opens your investments if you forget the passphrase. Save it somewhere safe, away from this phone: without it and your passphrase, nobody can get them back.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            RecoveryKeyBox(code, ui.email.orEmpty())
            CheckRow(saved, "I've saved my recovery key", !working) { saved = it }
            if (working) busy?.let { Progress(it) }
            else if (error == null) Text(
                "Your investments are copied into the vault and checked. Only then is the readable copy removed.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            error?.let { ErrorText("$it Your investments weren't changed.") }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { step = 1 }, enabled = !working, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("Back") }
                Button(
                    onClick = { vm.turnOn(pass, code, remember, onDone) },
                    enabled = saved && !working,
                    modifier = Modifier.weight(1.4f).height(48.dp), shape = RoundedCornerShape(14.dp)
                ) { Text(if (working) "Locking…" else "Lock my investments", fontWeight = FontWeight.SemiBold, maxLines = 1) }
            }
        }
    }
}

private fun copySensitive(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

@Composable
private fun RecoveryKeyBox(code: String, email: String) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.5.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copySensitive(context, "Recovery key", code); copied = true }, shape = RoundedCornerShape(12.dp)) {
                    Text(if (copied) "Copied ✓" else "Copy")
                }
                OutlinedButton(
                    onClick = {
                        val text = "DailyTrack private vault: recovery key for $email\n\n$code\n\nIt opens your investments if you forget your passphrase. Keep it somewhere safe."
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Save recovery key"))
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun PassphraseDialog(vm: VaultVM, onClose: () -> Unit) {
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    var pass by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (busy == null) onClose() },
        title = { Text("New passphrase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecretField(pass, { pass = it }, "New passphrase", shown = true, enabled = busy == null,
                    supporting = "At least ${VaultVM.MIN_PASSPHRASE} characters")
                TextButton(onClick = { pass = VaultWords.suggest() }) { Text("Suggest one") }
                error?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.changePassphrase(pass, onClose) }, enabled = busy == null && VaultVM.longEnough(pass)) {
                Text(if (busy != null) "Saving…" else "Change")
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = busy == null) { Text("Cancel") } }
    )
}

@Composable
private fun RecoveryDialog(vm: VaultVM, email: String, onClose: () -> Unit) {
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    var code by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (busy == null && code == null) onClose() },
        title = { Text("New recovery key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (code == null) Text("Your old recovery key stops working, and this one takes its place.")
                else RecoveryKeyBox(code!!, email)
                error?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            if (code == null) TextButton(onClick = { vm.newRecoveryKey { code = it } }, enabled = busy == null) {
                Text(if (busy != null) "Saving…" else "Make a new one")
            } else TextButton(onClick = onClose) { Text("I've saved it") }
        },
        dismissButton = { if (code == null) TextButton(onClick = onClose, enabled = busy == null) { Text("Cancel") } }
    )
}

@Composable
private fun HowItWorks() {
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(Dimens.current.cardCornerRadius), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("How it works", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = colors.onSurfaceVariant)
            }
            AnimatedVisibility(open) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "Your passphrase never leaves your phone. It unlocks a key that encrypts your investments before they're saved.",
                        "The server keeps only scrambled data: nobody running it, the admin included, can read it.",
                        "Prices are looked up by stock and fund name, never with your amounts. A CAS import passes through the server once and isn't kept.",
                        "Friends you share Investments with get their own copy of the key once they turn on their vault.",
                        "Forgot the passphrase? Your recovery key opens it. Lose both, and nobody can get them back.",
                    ).forEach { line ->
                        Row {
                            Text("•", color = colors.primary, modifier = Modifier.width(14.dp))
                            Text(line, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ── Investments ─────────────────────────────────────────────────────────────

/** Unlock: the passphrase, or the recovery key. */
@Composable
private fun UnlockForm(vm: VaultVM, onDone: () -> Unit = {}) {
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    var secret by rememberSaveable { mutableStateOf("") }
    var recovery by rememberSaveable { mutableStateOf(false) }
    var remember by rememberSaveable { mutableStateOf(true) }
    var shown by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SecretField(
            secret, { secret = it }, if (recovery) "Recovery key" else "Passphrase",
            shown = shown || recovery, onToggleShown = if (recovery) null else ({ shown = !shown }),
            enabled = busy == null, mono = recovery
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CheckRow(remember, "Remember on this phone", busy == null) { remember = it } }
            TextButton(onClick = { recovery = !recovery; secret = ""; vm.clearError() }) {
                Text(if (recovery) "Use passphrase" else "Use recovery key")
            }
        }
        error?.let { ErrorText(it) }
        Button(
            onClick = { vm.unlock(secret, recovery, remember, onDone) },
            enabled = busy == null && secret.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)
        ) {
            if (busy != null) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text("Unlock", fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Over Investments while the vault is locked (or a friend's can't be opened yet). */
@Composable
fun VaultGate(ui: VaultUi, vm: VaultVM = hiltViewModel()) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerHigh,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 48.dp).widthIn(max = 440.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(56.dp).background(colors.primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = colors.primary, modifier = Modifier.size(26.dp))
                }
                val (title, text) = when (ui.reason) {
                    "no-keys" -> "Private" to "${ui.owner} keeps Investments in a private vault. Turn on your own in Settings › Privacy & Security, and it opens for you next time they use the app."
                    "no-grant" -> "Almost there" to "It opens for you next time ${ui.owner} uses the app."
                    else -> "Your investments are locked" to
                        if (ui.viewing) "Unlock your own vault to open ${ui.owner}'s." else "Enter your passphrase to open them on this phone."
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                if (ui.reason == "locked") {
                    Spacer(Modifier.height(4.dp))
                    UnlockForm(vm)
                }
            }
        }
    }
}

/** "🔒 Private" beside the portfolio's title (or a quiet 🔓): tap for what it means. */
@Composable
fun VaultChip(ui: VaultUi, canChange: Boolean) {
    if (ui.state == "unknown" || (ui.state != "on" && !canChange)) return
    val on = ui.state == "on"
    var open by remember { mutableStateOf(false) }
    val tint = if (on) PrivateGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    Box {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (on) PrivateGreen.copy(alpha = 0.12f) else Color.Transparent,
            border = if (on) BorderStroke(1.dp, PrivateGreen.copy(alpha = 0.35f)) else null,
            modifier = Modifier.clickable { open = true }
        ) {
            Row(Modifier.padding(horizontal = if (on) 6.dp else 2.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (on) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = if (on) "Private vault on" else "Private vault off",
                    tint = tint, modifier = Modifier.size(11.dp))
                if (on) {
                    Spacer(Modifier.width(3.dp))
                    Text("Private", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 0.5.sp), color = tint)
                }
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(14.dp)) {
            Text(
                text = when {
                    on && ui.viewing -> "${ui.owner} keeps these in a private vault. Only their devices, and yours, can read them; not even the app's admin."
                    on -> "Locked with your passphrase. Only your devices can read your investments; not even the app's admin."
                    else -> "Stored readable on the server. Turn on the private vault in Settings › Privacy & Security to lock them with a passphrase only you know."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).widthIn(max = 260.dp)
            )
        }
    }
}
