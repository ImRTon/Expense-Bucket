package com.rton.expensebucket.ui.components

import androidx.compose.ui.platform.LocalContext
import com.rton.expensebucket.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import com.rton.expensebucket.data.model.Project
import com.rton.expensebucket.ui.util.CurrencyFormats
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectFormDialog(
    initialProject: Project? = null,
    onDismiss: () -> Unit,
    onConfirm: (Project) -> Unit
) {
    val strings = LocalContext.current.resources

    var name by remember(initialProject) { mutableStateOf(initialProject?.name ?: "") }
    var description by remember(initialProject) { mutableStateOf(initialProject?.description ?: "") }
    var currency by remember(initialProject) { mutableStateOf(initialProject?.defaultCurrency ?: "JPY") }

    val initialBudgetStr = initialProject?.budget?.let {
        if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
    } ?: ""
    var budgetText by remember(initialProject) { mutableStateOf(initialBudgetStr) }

    val iconChoices = listOf(
        "✈️", "🧳", "🏝️", "🏙️",
        "🏔️", "🚄", "🚗", "⛩️",
        "🎡", "🌸", "🍜", "☕",
        "🏕️", "🚲", "🛳️", "🏕️"
    )
    val colorChoices = listOf(
        0xFF3F8CFF,
        0xFF14B8A6,
        0xFF22C55E,
        0xFFF59E0B,
        0xFFEF5D60,
        0xFFE56B6F,
        0xFF8B5CF6,
        0xFF0EA5E9,
        0xFF64748B,
        0xFF795548
    )
    var icon by remember(initialProject) { mutableStateOf(initialProject?.icon ?: iconChoices.first()) }
    var color by remember(initialProject) { mutableStateOf(initialProject?.color ?: colorChoices.first()) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    val currencies = CurrencyFormats.supportedCurrencies

    // Date states
    var startDate by remember(initialProject) { mutableStateOf(initialProject?.startDate) }
    var endDate by remember(initialProject) { mutableStateOf(initialProject?.endDate) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    val dialogScrollState = rememberScrollState()

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()) }
    val isEditMode = initialProject != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditMode) strings.getString(R.string.edit_trip_project) else strings.getString(R.string.add_trip_project)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(dialogScrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.getString(R.string.project_name)) },
                    placeholder = { Text(strings.getString(R.string.project_example)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(strings.getString(R.string.optional_description)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    strings.getString(R.string.appearance),
                    style = MaterialTheme.typography.labelLarge
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(color).copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = icon,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (name.isBlank()) strings.getString(R.string.project_preview) else name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                strings.getString(R.string.project_style_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Filled.Palette,
                            contentDescription = null,
                            tint = Color(color)
                        )
                    }
                }

                Text(
                    strings.getString(R.string.icon),
                    style = MaterialTheme.typography.labelLarge
                )
                val iconRows = remember(iconChoices) { iconChoices.chunked(4) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    iconRows.forEach { rowIcons ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowIcons.forEach { candidate ->
                                val isSelected = candidate == icon
                                Surface(
                                    onClick = { icon = candidate },
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (isSelected) Color(color).copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = if (isSelected) {
                                        BorderStroke(1.dp, Color(color))
                                    } else {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = candidate,
                                            style = MaterialTheme.typography.headlineSmall
                                        )
                                    }
                                }
                            }
                            repeat(4 - rowIcons.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    Surface(
                        onClick = { showEmojiPicker = true },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                strings.getString(R.string.more_emoji),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                Text(
                    strings.getString(R.string.color),
                    style = MaterialTheme.typography.labelLarge
                )
                val colorRows = remember(colorChoices) { colorChoices.chunked(5) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    colorRows.forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowColors.forEach { candidate ->
                                val isSelected = candidate == color
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(candidate))
                                        .clickable { color = candidate }
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(14.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Text(
                                            "✓",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                            repeat(5 - rowColors.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Text(
                    strings.getString(R.string.default_currency),
                    style = MaterialTheme.typography.labelLarge
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    currencies.take(4).forEachIndexed { index, cur ->
                        SegmentedButton(
                            selected = currency == cur,
                            onClick = { currency = cur },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = 4
                            ),
                            label = { Text(cur, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    currencies.drop(4).forEachIndexed { index, cur ->
                        SegmentedButton(
                            selected = currency == cur,
                            onClick = { currency = cur },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = 4
                            ),
                            label = { Text(cur, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // ─── Date Range ──────────────────────────
                Text(
                    strings.getString(R.string.optional_trip_dates),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedCard(
                        onClick = { showStartDatePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                startDate?.let { dateFormat.format(Date(it)) } ?: strings.getString(R.string.start_date),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (startDate != null) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedCard(
                        onClick = { showEndDatePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                endDate?.let { dateFormat.format(Date(it)) } ?: strings.getString(R.string.end_date),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (endDate != null) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ─── Budget ──────────────────────────────
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.toDoubleOrNull() != null) {
                            budgetText = newValue
                        }
                    },
                    label = { Text(strings.getString(R.string.optional_budget)) },
                    placeholder = { Text(strings.getString(R.string.budget_example)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    prefix = { Text(currency) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedProject = initialProject?.copy(
                        name = name.trim(),
                        description = description.trim(),
                        defaultCurrency = currency,
                        startDate = startDate,
                        endDate = endDate,
                        budget = budgetText.toDoubleOrNull(),
                        icon = icon,
                        color = color,
                        updatedAt = System.currentTimeMillis()
                    ) ?: Project(
                        name = name.trim(),
                        description = description.trim(),
                        defaultCurrency = currency,
                        startDate = startDate,
                        endDate = endDate,
                        budget = budgetText.toDoubleOrNull(),
                        icon = icon,
                        color = color
                    )
                    onConfirm(updatedProject)
                },
                enabled = name.isNotBlank()
            ) {
                Text(if (isEditMode) strings.getString(R.string.save) else strings.getString(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.getString(R.string.cancel))
            }
        }
    )

    // Start Date Picker Dialog
    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = startDate)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { startDate = it }
                    showStartDatePicker = false
                }) { Text(strings.getString(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text(strings.getString(R.string.cancel)) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    // End Date Picker Dialog
    if (showEndDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = endDate ?: startDate)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { endDate = it }
                    showEndDatePicker = false
                }) { Text(strings.getString(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text(strings.getString(R.string.cancel)) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showEmojiPicker) {
        AlertDialog(
            onDismissRequest = { showEmojiPicker = false },
            title = { Text(strings.getString(R.string.choose_emoji)) },
            text = {
                AndroidView(
                    factory = { context ->
                        EmojiPickerView(context).apply {
                            setOnEmojiPickedListener { item ->
                                icon = item.emoji
                                showEmojiPicker = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp, max = 420.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { showEmojiPicker = false }) {
                    Text(strings.getString(R.string.close))
                }
            }
        )
    }
}
