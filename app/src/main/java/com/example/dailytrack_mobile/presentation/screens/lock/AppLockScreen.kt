package com.example.dailytrack_mobile.presentation.screens.lock

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.dailytrack_mobile.data.local.security.AppLockManager
import com.example.dailytrack_mobile.data.local.security.LockType
import com.example.dailytrack_mobile.presentation.screens.lock.components.PinDots
import com.example.dailytrack_mobile.presentation.screens.lock.components.PinKeypad
import com.example.dailytrack_mobile.presentation.util.BiometricHelper
import com.example.dailytrack_mobile.presentation.util.Dimens
import kotlinx.coroutines.launch

@Composable
fun AppLockScreen(
    appLockManager: AppLockManager,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val dims = Dimens.current

    val lockType by appLockManager.lockTypeFlow.collectAsState(initial = LockType.SYSTEM)
    val isBiometricWithPin by appLockManager.isBiometricWithPinEnabledFlow.collectAsState(initial = true)
    val isBiometricAvailable = remember { BiometricHelper.isBiometricAvailable(context) }
    val isDeviceLockAvailable = remember { BiometricHelper.isDeviceLockAvailable(context) }

    var enteredPin by remember { mutableStateOf("") }
    var isPinError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun triggerBiometricPrompt() {
        val activity = context as? FragmentActivity ?: return
        if (lockType == LockType.SYSTEM) {
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = "Unlock DailyTrack",
                subtitle = "Touch the fingerprint sensor or enter your device PIN",
                allowDeviceCredential = true,
                onSuccess = { onUnlocked() },
                onError = { _, _ -> },
                onFailed = {}
            )
        } else if (isBiometricAvailable && isBiometricWithPin) {
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = "Unlock DailyTrack",
                subtitle = "Touch the fingerprint sensor to unlock",
                negativeButtonText = "Use PIN",
                allowDeviceCredential = false,
                onSuccess = { onUnlocked() },
                onError = { _, _ -> },
                onFailed = {}
            )
        }
    }

    // Auto-prompt biometrics once on appear
    LaunchedEffect(lockType) {
        if (lockType == LockType.SYSTEM && isDeviceLockAvailable) {
            triggerBiometricPrompt()
        } else if (lockType == LockType.PIN && isBiometricAvailable && isBiometricWithPin) {
            triggerBiometricPrompt()
        }
    }

    fun handleDigit(digit: Int) {
        if (enteredPin.length >= 4) return
        isPinError = false
        errorMessage = null

        val updated = enteredPin + digit.toString()
        enteredPin = updated

        if (updated.length == 4) {
            coroutineScope.launch {
                val isValid = appLockManager.validatePin(updated)
                if (isValid) {
                    onUnlocked()
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isPinError = true
                    errorMessage = "Incorrect PIN. Try again."
                    enteredPin = ""
                }
            }
        }
    }

    fun handleDelete() {
        isPinError = false
        errorMessage = null
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                // App Icon Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "DailyTrack",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "DailyTrack",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (lockType == LockType.SYSTEM)
                        "App is locked. Verify your identity to continue."
                    else
                        "Enter your 4-digit PIN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (lockType == LockType.PIN) {
                    Spacer(modifier = Modifier.height(24.dp))
                    PinDots(
                        pinLength = 4,
                        enteredCount = enteredPin.length,
                        isError = isPinError
                    )
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Bottom action area
            if (lockType == LockType.SYSTEM) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        onClick = { triggerBiometricPrompt() },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(88.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Unlock",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { triggerBiometricPrompt() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = "Unlock",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            } else {
                // PIN Keypad
                PinKeypad(
                    onDigitClick = { handleDigit(it) },
                    onDeleteClick = { handleDelete() },
                    showBiometricButton = isBiometricAvailable && isBiometricWithPin,
                    onBiometricClick = { triggerBiometricPrompt() },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
}
