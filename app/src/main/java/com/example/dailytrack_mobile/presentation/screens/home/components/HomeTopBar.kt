package com.example.dailytrack_mobile.presentation.screens.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.dailytrack_mobile.data.local.auth.AuthManager
import com.example.dailytrack_mobile.presentation.util.Dimens
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..11  -> "Good morning"
    in 12..16 -> "Good afternoon"
    else      -> "Good evening"
}

private fun extractDisplayName(fullName: String?, email: String?): String {
    if (!fullName.isNullOrBlank()) {
        val first = fullName.trim().split("\\s+".toRegex()).firstOrNull { it.isNotBlank() }
        if (!first.isNullOrBlank()) {
            return first.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
    if (!email.isNullOrBlank()) {
        val emailPrefix = email.substringBefore("@").replace(Regex("[._0-9]"), " ").trim()
        val firstFromEmail = emailPrefix.split("\\s+".toRegex()).firstOrNull { it.isNotBlank() }
        if (!firstFromEmail.isNullOrBlank()) {
            return firstFromEmail.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
    return "Sabarish"
}

@Composable
fun HomeTopBar(
    onNavigateToSettings: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    userName: String? = null,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current
    val context = LocalContext.current
    val authManager = remember { AuthManager(context.applicationContext) }
    val storedName by authManager.userNameFlow.collectAsState(initial = null)
    val storedEmail by authManager.userEmailFlow.collectAsState(initial = null)

    val firebaseName = remember {
        try {
            FirebaseAuth.getInstance().currentUser?.displayName
        } catch (_: Exception) {
            null
        }
    }
    val firebaseEmail = remember {
        try {
            FirebaseAuth.getInstance().currentUser?.email
        } catch (_: Exception) {
            null
        }
    }

    val resolvedFullName = userName ?: storedName ?: firebaseName
    val resolvedEmail = storedEmail ?: firebaseEmail

    val userGreetingName = remember(resolvedFullName, resolvedEmail) {
        extractDisplayName(resolvedFullName, resolvedEmail)
    }

    // Recomputes every minute so the greeting updates live as time passes
    val greetingText by produceState(initialValue = greeting()) {
        while (true) {
            delay(60_000L)
            value = greeting()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(
                horizontal = dims.screenHorizontalPadding,
                vertical = 8.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Left: Greeting Text (Hello User) ─────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "$greetingText,",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$userGreetingName 👋",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // ── Right: Analytics + Settings Capsule (no Search) ──────────────────
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.height(40.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (com.example.dailytrack_mobile.presentation.access.LocalAccess.current
                        .canView(com.example.dailytrack_mobile.data.local.auth.AccessModule.MONEY)
                ) IconButton(
                    onClick = onNavigateToAnalytics,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "Analytics",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
