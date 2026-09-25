package com.example.dailytrack_mobile.presentation.screens.forms

import com.example.dailytrack_mobile.presentation.components.ConnectedToggleGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.presentation.util.Dimens

import androidx.hilt.navigation.compose.hiltViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Add Investment Form
// ─────────────────────────────────────────────────────────────────────────────

private enum class InvestmentCategory(val label: String, val emoji: String) {
    STOCKS("Stocks", "📊"),
    MUTUAL_FUNDS("Mutual Funds", "📈"),
    FD("FD", "🏦"),
    GOLD("Gold", "🥇"),
    NPS("NPS", "🏛️"),
    PPF("PPF", "🔒"),
    REAL_ESTATE("Real Estate", "🏠"),
    OTHER("Other", "💼")
}

private enum class InvestmentFrequency(val label: String) {
    ONE_TIME("One-time"),
    MONTHLY_SIP("Monthly SIP"),
    QUARTERLY("Quarterly"),
    YEARLY("Yearly")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddInvestmentScreen(
    formsVM: FormsVM = hiltViewModel(),
    onDirtyStateChanged: (Boolean) -> Unit = {},
    onSaveSuccess: () -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf<InvestmentCategory?>(null) }
    var selectedFrequency by remember { mutableStateOf(InvestmentFrequency.ONE_TIME) }
    var investmentName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var interestRate by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val dims = Dimens.current

    val isDirty = remember(selectedCategory, selectedFrequency, investmentName, amount, interestRate, note) {
        selectedCategory != null ||
                investmentName.isNotBlank() ||
                amount.isNotBlank() ||
                interestRate.isNotBlank() ||
                note.isNotBlank() ||
                selectedFrequency != InvestmentFrequency.ONE_TIME
    }

    LaunchedEffect(isDirty) {
        onDirtyStateChanged(isDirty)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingLarge),
        verticalArrangement = Arrangement.spacedBy(dims.sectionSpacing)
    ) {
        // ── Investment Category ───────────────────────────────────────
        SectionLabel("Investment Category")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium),
            verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
        ) {
            InvestmentCategory.entries.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text("${category.emoji} ${category.label}", style = MaterialTheme.typography.bodyMedium) },
                    shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
                )
            }
        }

        // ── Investment Name ──────────────────────────────────────────
        SectionLabel("Name / Scheme")
        OutlinedTextField(
            value = investmentName,
            onValueChange = { investmentName = it },
            placeholder = {
                Text(
                    when (selectedCategory) {
                        InvestmentCategory.STOCKS -> "e.g. Reliance Industries"
                        InvestmentCategory.MUTUAL_FUNDS -> "e.g. SBI Bluechip Fund"
                        InvestmentCategory.FD -> "e.g. SBI FD - 1Y"
                        InvestmentCategory.GOLD -> "e.g. Sovereign Gold Bond"
                        InvestmentCategory.NPS -> "e.g. NPS Tier-I"
                        InvestmentCategory.PPF -> "e.g. PPF Account"
                        InvestmentCategory.REAL_ESTATE -> "e.g. 2BHK Apartment"
                        else -> "e.g. Investment name"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(dims.buttonCornerRadius),
            modifier = Modifier.fillMaxWidth()
        )

        // ── Frequency ────────────────────────────────────────────────
        SectionLabel("Frequency")
        ConnectedToggleGroup(
            options = InvestmentFrequency.entries,
            selected = selectedFrequency,
            onSelect = { selectedFrequency = it },
            label = { it.label },
            modifier = Modifier.fillMaxWidth()
        )

        // ── Amount ───────────────────────────────────────────────────
        SectionLabel(
            if (selectedFrequency == InvestmentFrequency.ONE_TIME) "Amount"
            else "Amount per ${selectedFrequency.label.lowercase()}"
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { newValue ->
                if (newValue.all { it.isDigit() || it == '.' }) amount = newValue
            },
            placeholder = { Text("0.00", style = MaterialTheme.typography.bodyMedium) },
            prefix = { Text("₹ ", style = MaterialTheme.typography.bodyLarge) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(dims.buttonCornerRadius),
            modifier = Modifier.fillMaxWidth()
        )

        // ── Interest / Expected Return ───────────────────────────────
        SectionLabel("Expected Return / Interest Rate")
        OutlinedTextField(
            value = interestRate,
            onValueChange = { newValue ->
                if (newValue.all { it.isDigit() || it == '.' }) interestRate = newValue
            },
            placeholder = { Text("7.5", style = MaterialTheme.typography.bodyMedium) },
            suffix = { Text("% p.a.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(dims.buttonCornerRadius),
            modifier = Modifier.fillMaxWidth()
        )

        // ── Note ─────────────────────────────────────────────────────
        SectionLabel("Note (optional)")
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = { Text("Maturity date, lock-in period, etc.", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Outlined.Notes, contentDescription = null, modifier = Modifier.size(dims.iconSizeMedium))
            },
            minLines = 2,
            maxLines = 3,
            shape = RoundedCornerShape(dims.buttonCornerRadius),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        // ── Save Button ──────────────────────────────────────────────
        Button(
            onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                formsVM.saveInvestment(
                    name = investmentName,
                    category = selectedCategory?.label ?: "Other",
                    amount = amt,
                    frequency = selectedFrequency.label,
                    note = note.takeIf { it.isNotBlank() },
                    onSuccess = onSaveSuccess
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(dims.searchBarHeight),
                enabled = investmentName.isNotBlank() && selectedCategory != null && amount.isNotBlank()
        ) {
            Text(
                "Save Investment",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(modifier = Modifier.height(dims.itemSpacingMedium))
    }
}
