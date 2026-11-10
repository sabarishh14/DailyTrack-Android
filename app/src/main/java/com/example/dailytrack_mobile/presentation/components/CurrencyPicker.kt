package com.example.dailytrack_mobile.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dailytrack_mobile.presentation.util.Currencies

/** "₹  INR · Indian rupee": the currency an account holds, picked from the offered ones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    var open by remember { mutableStateOf(false) }
    fun label(code: String) = "${Currencies.symbol(code).trim()}  $code · ${Currencies.names[code] ?: code}"
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = label(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text("Currency") },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Currencies.symbols.keys.forEach { code ->
                DropdownMenuItem(
                    text = { Text(label(code)) },
                    onClick = {
                        onSelect(code)
                        open = false
                    }
                )
            }
        }
    }
}
