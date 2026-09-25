package com.example.dailytrack_mobile.presentation.components.transaction

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.presentation.screens.money.CategoryEmojis
import com.example.dailytrack_mobile.presentation.theme.AppTheme
import com.example.dailytrack_mobile.presentation.theme.LocalAppTheme
import com.example.dailytrack_mobile.presentation.util.AmountExpression
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Transaction entry fields
//
// The building blocks of every transaction form — Add Money, Edit and Bulk
// Edit all compose these, so an entry looks and behaves the same wherever it
// is typed.
// ─────────────────────────────────────────────────────────────────────────────

private val sectionLabelStyle
    @Composable get() = MaterialTheme.typography.labelSmall.copy(
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Bold
    )

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = sectionLabelStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = modifier
    )
}

@Composable
fun entryCardColor(): Color = MaterialTheme.colorScheme.surfaceContainer

@Composable
fun entryCardBorder(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

/** Accent for a type — theme-driven for expense/income, fixed hues for the rest. */
@Composable
fun entryTypeAccent(type: EntryType): Color {
    val isDtOg = LocalAppTheme.current == AppTheme.DT_OG
    return when (type) {
        EntryType.EXPENSE -> MaterialTheme.colorScheme.error
        EntryType.INCOME -> if (isDtOg) Color(0xFF10B981) else MaterialTheme.colorScheme.tertiary
        EntryType.SAVINGS -> Color(0xFF29B6F6)
        EntryType.INVESTMENT -> Color(0xFFAB47BC)
    }
}

@Composable
private fun entryTypeContainer(type: EntryType): Pair<Color, Color> {
    val isDtOg = LocalAppTheme.current == AppTheme.DT_OG
    return when (type) {
        EntryType.EXPENSE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        EntryType.INCOME -> if (isDtOg) {
            Color(0xFF10B981).copy(alpha = 0.18f) to Color(0xFF10B981)
        } else {
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        }
        else -> entryTypeAccent(type).copy(alpha = 0.18f) to entryTypeAccent(type)
    }
}

fun formatRupees(value: Double): String =
    if (value % 1.0 == 0.0) "%,.0f".format(value) else "%,.2f".format(value)

// ── Type selector ────────────────────────────────────────────────────────────

@Composable
fun EntryTypeSelector(
    selected: EntryType,
    options: List<EntryType>,
    onSelect: (EntryType) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val (containerColor, contentColor) = entryTypeContainer(option)
                val bg by animateColorAsState(
                    targetValue = if (isSelected) containerColor else Color.Transparent,
                    animationSpec = tween(200),
                    label = "typeBg"
                )
                val fg by animateColorAsState(
                    targetValue = if (isSelected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "typeFg"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .clickable { onSelect(option) }
                        .padding(vertical = if (options.size > 2) 10.dp else 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option.label,
                        style = (if (options.size > 2) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge)
                            .copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                        color = fg,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ── Amount ───────────────────────────────────────────────────────────────────

private val AMOUNT_OPERATORS = listOf(
    "+" to "+",
    "−" to "-",
    "×" to "*",
    "÷" to "/",
    "(" to "(",
    ")" to ")"
)

/**
 * The amount card. Doubles as a calculator: operators appear while it is
 * focused, and a live "= ₹…" preview shows while it holds an expression.
 */
@Composable
fun EntryAmountCard(
    amount: String,
    onAmountChange: (String) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    compact: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val preview = remember(amount) { AmountExpression.previewFor(amount) }
    val bigSize = if (compact) 34.sp else 42.sp
    val smallSize = if (compact) 26.sp else 30.sp

    // Plain-String BasicTextField doesn't know where the cursor should land
    // when the text changes from outside typing (an operator key, ⌫, or the
    // Done-collapse) — it leaves the cursor where it was, which reads as
    // "before" whatever got appended. Tracking a TextFieldValue lets us pin
    // the cursor to the end whenever such an external change happens, while
    // leaving it exactly where the user left it while actually typing.
    var fieldValue by remember { mutableStateOf(TextFieldValue(amount, TextRange(amount.length))) }
    if (fieldValue.text != amount) {
        fieldValue = TextFieldValue(amount, TextRange(amount.length))
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = entryCardColor()),
        border = entryCardBorder(),
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                runCatching { focusRequester.requestFocus() }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = if (compact) 14.dp else 18.dp)
        ) {
            SectionLabel("AMOUNT")
            Spacer(modifier = Modifier.height(if (compact) 6.dp else 10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "₹",
                    fontSize = if (compact) 26.sp else 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent.copy(alpha = 0.85f),
                    modifier = Modifier.padding(end = 6.dp)
                )
                BasicTextField(
                    value = fieldValue,
                    onValueChange = { newValue ->
                        // Digits, one decimal point, and arithmetic — a bill split
                        // across three items can be typed as "120+40+15".
                        if (AmountExpression.isAllowedInput(newValue.text)) {
                            fieldValue = newValue
                            onAmountChange(newValue.text)
                        }
                    },
                    textStyle = TextStyle(
                        fontSize = if (amount.length > 12) smallSize else bigSize,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    ),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            // Collapse the expression on Done so the field then
                            // shows exactly the number that will be saved.
                            if (!AmountExpression.isPlainNumber(amount)) {
                                AmountExpression.evaluate(amount)?.let { value ->
                                    onAmountChange(AmountExpression.formatAmount(value))
                                }
                            }
                            defaultKeyboardAction(ImeAction.Done)
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused },
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (amount.isEmpty()) {
                                Text(
                                    text = "0",
                                    fontSize = bigSize,
                                    fontWeight = FontWeight.Bold,
                                    color = accent.copy(alpha = 0.35f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            // The decimal keypad offers no minus, times or divide, so the field
            // supplies them rather than asking the user to hunt for them.
            AnimatedVisibility(
                visible = isFocused,
                enter = fadeIn(tween(160)) + expandVertically(tween(180)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(140))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AMOUNT_OPERATORS.forEach { (label, token) ->
                        AmountOperatorKey(
                            label = label,
                            accent = accent,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val next = amount + token
                                if (AmountExpression.isAllowedInput(next)) onAmountChange(next)
                            }
                        )
                    }
                    AmountOperatorKey(
                        label = "⌫",
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onClick = { onAmountChange(amount.dropLast(1)) }
                    )
                }
            }

            AnimatedVisibility(
                visible = preview != null,
                enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Functions,
                        contentDescription = null,
                        tint = accent.copy(alpha = 0.8f),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "= ₹${preview.orEmpty()}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = accent.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountOperatorKey(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.10f),
        modifier = modifier
            .height(38.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = accent.copy(alpha = 0.9f)
            )
        }
    }
}

// ── Date & account ───────────────────────────────────────────────────────────

/** "Today", "Yesterday", or the date itself. */
fun friendlyDate(millis: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(day, today)) return "Today"
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(day, today)) return "Yesterday"
    val sameYear = day.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
    val pattern = if (sameYear) "dd MMM" else "dd MMM yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDateAccountRow(
    dateMillis: Long,
    account: String?,
    onDateClick: () -> Unit,
    onAccountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PickerCard(
            label = "DATE",
            value = friendlyDate(dateMillis),
            icon = Icons.Default.CalendarToday,
            iconSize = 16.dp,
            onClick = onDateClick,
            modifier = Modifier.weight(1f)
        )
        PickerCard(
            label = "ACCOUNT",
            value = account ?: "Select",
            icon = Icons.Default.KeyboardArrowDown,
            iconSize = 18.dp,
            onClick = onAccountClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = entryCardColor()),
        border = entryCardBorder(),
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            SectionLabel(label)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

// ── Category ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryCategoryPills(
    pills: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
) {
    val cardBg = entryCardColor()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionLabel("CATEGORY")

        if (isLoading) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(72.dp, 84.dp, 68.dp, 80.dp).forEach { w ->
                    Box(
                        modifier = Modifier
                            .width(w)
                            .height(36.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                    )
                }
            }
            return@Column
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            pills.forEach { cat ->
                val isSelected = selected.trim().equals(cat.trim(), ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else cardBg)
                        .border(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { onSelect(if (isSelected) "" else cat) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = cat,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.5.sp
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBg)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .clickable(onClick = onMore)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Search / Add Category",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── Description ──────────────────────────────────────────────────────────────

@Composable
fun EntryDescriptionCard(
    note: String,
    onNoteChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = entryCardColor()),
        border = entryCardBorder(),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("DESCRIPTION")
                if (note.isNotBlank()) {
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onNoteChange("") }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            BasicTextField(
                value = note,
                onValueChange = onNoteChange,
                textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                minLines = 2,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onFocusChanged(it.isFocused) },
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.TopStart) {
                        if (note.isEmpty()) {
                            Text(
                                text = "What was this for? (optional)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}

// ── Exclude from analytics ───────────────────────────────────────────────────

@Composable
fun EntryExcludeCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // One slim line, full width like the fields above it: an occasional option
    // shouldn't take a whole card, nor leave a gap beside it.
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(14.dp),
        color = entryCardColor(),
        border = entryCardBorder(),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.VisibilityOff,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Exclude from analytics",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.75f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        }
    }
}

// ── Description suggestions bar ──────────────────────────────────────────────

/** Docked above the keyboard while a description is being typed. */
@Composable
fun EntrySuggestionBar(
    category: String,
    suggestions: List<String>,
    categorySuggestions: Collection<String>,
    onPick: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (category.isNotBlank()) "SUGGESTIONS • ${category.uppercase()}" else "SUGGESTIONS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onDone,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                suggestions.forEach { suggestion ->
                    val isCategoryMatch = suggestion in categorySuggestions
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCategoryMatch) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isCategoryMatch) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        onClick = { onPick(suggestion) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isCategoryMatch) Icons.Default.Check else Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = if (isCategoryMatch) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Past descriptions for this [type] and [category] (the type alone until one is picked), filtered by [query]. */
fun rankDescriptionSuggestions(
    type: EntryType,
    category: String,
    query: String,
    history: EntryHistory,
    limit: Int = 50
): List<String> {
    val combined = history.descriptionsFor(type, category)
    val q = query.trim()
    if (q.isBlank()) return combined.take(limit)
    val (startsWith, rest) = combined
        .filter { !it.equals(q, ignoreCase = true) }
        .partition { it.startsWith(q, ignoreCase = true) }
    return (startsWith + rest.filter { it.contains(q, ignoreCase = true) }).take(limit)
}

// ── Collapsed entry card ─────────────────────────────────────────────────────

/**
 * An entry folded down to one line: what it was, where it came from, and how
 * much. Tapping it opens the entry back up for editing.
 */
@Composable
fun EntrySummaryCard(
    number: Int,
    entry: TransactionEntryState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showIncomplete: Boolean = true,
    onDuplicate: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    val accent = entryTypeAccent(entry.type)
    val missing = entry.missingFields
    val flagged = showIncomplete && !entry.isBlank && missing.isNotEmpty()
    val value = entry.evaluatedAmount

    val title = entry.note.trim().ifBlank { entry.category.ifBlank { "New entry" } }
    val subtitle = if (flagged) {
        "Needs ${missing.joinAsSentence()}"
    } else {
        listOfNotNull(
            entry.category.takeIf { it.isNotBlank() && entry.note.isNotBlank() },
            entry.account,
            friendlyDate(entry.dateMillis)
        ).joinToString(" · ")
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = entryCardColor()),
        border = if (flagged) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)) else entryCardBorder(),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = if (onDuplicate != null || onRemove != null) 2.dp else 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.14f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (entry.category.isBlank()) "💳" else CategoryEmojis.forCategory(entry.category),
                            fontSize = 19.sp
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.5.dp, entryCardColor()),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$number",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (flagged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = when {
                    value == null || value <= 0.0 -> "₹ —"
                    entry.type == EntryType.INCOME -> "+₹${formatRupees(value)}"
                    else -> "−₹${formatRupees(value)}"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (value == null || value <= 0.0) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else accent,
                maxLines = 1
            )

            if (onDuplicate != null || onRemove != null) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Entry options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        onDuplicate?.let { duplicate ->
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = { menuOpen = false; duplicate() }
                            )
                        }
                        onRemove?.let { remove ->
                            DropdownMenuItem(
                                text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = { menuOpen = false; remove() }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Sits above an open entry when there are several, so it's clear which one is being edited. */
@Composable
fun EntryEditorHeader(
    number: Int,
    total: Int,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    onDuplicate: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Text(
                text = "ENTRY $number OF $total",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        onDuplicate?.let {
            IconButton(onClick = it, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate entry", modifier = Modifier.size(18.dp))
            }
        }
        onRemove?.let {
            IconButton(onClick = it, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Remove entry",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        TextButton(
            onClick = onCollapse,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text("Done", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.width(2.dp))
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}
