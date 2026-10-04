package com.example.dailytrack_mobile.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.dailytrack_mobile.presentation.components.transaction.bankColor
import com.example.dailytrack_mobile.presentation.components.transaction.formatRupees

/**
 * One amount an alert is measured against (an account's minimum, a card's
 * monthly budget), with Save, Remove (when one is set) and Cancel.
 */
@Composable
fun AmountLimitDialog(
    account: String,
    icon: ImageVector,
    title: String,
    explanation: String,
    current: Double?,
    placeholder: String,
    onSave: (Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(current?.let { "%.0f".format(it) } ?: "") }
    val value = text.trim().toDoubleOrNull()
    val accent = bankColor(account) ?: MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() || it == '.' } },
                    prefix = { Text("₹") },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = value != null && value > 0) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onSave(null) }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** Set, change or remove how much a credit card should be used for in a month. */
@Composable
fun CardBudgetDialog(
    card: String,
    usedThisMonth: Double,
    budget: Double?,
    onSave: (Double?) -> Unit,
    onDismiss: () -> Unit
) = AmountLimitDialog(
    account = card,
    icon = Icons.Default.CreditCard,
    title = "Monthly budget for $card",
    explanation = "Get an alert when spending on this card goes over it in a month. It starts again on the 1st. " +
        "Used this month: ₹${formatRupees(usedThisMonth)}.",
    current = budget,
    placeholder = "20000",
    onSave = onSave,
    onDismiss = onDismiss
)
