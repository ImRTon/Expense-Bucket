package com.rton.expanses.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rton.expanses.data.model.Project
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectFormDialog(
    initialProject: Project? = null,
    onDismiss: () -> Unit,
    onConfirm: (Project) -> Unit
) {
    var name by remember { mutableStateOf(initialProject?.name ?: "") }
    var description by remember { mutableStateOf(initialProject?.description ?: "") }
    var currency by remember { mutableStateOf(initialProject?.defaultCurrency ?: "JPY") }
    
    val initialBudgetStr = initialProject?.budget?.let {
        if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
    } ?: ""
    var budgetText by remember { mutableStateOf(initialBudgetStr) }
    
    val currencies = listOf("JPY", "USD", "EUR", "KRW", "GBP", "THB", "VND", "TWD")

    // Date states
    var startDate by remember { mutableStateOf(initialProject?.startDate) }
    var endDate by remember { mutableStateOf(initialProject?.endDate) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()) }
    val isEditMode = initialProject != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditMode) "編輯旅行專案" else "新增旅行專案") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("專案名稱") },
                    placeholder = { Text("例如：東京之旅 2026") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述 (選填)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    "預設幣別",
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
                    "旅行期間 (選填)",
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
                                startDate?.let { dateFormat.format(Date(it)) } ?: "開始日期",
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
                                endDate?.let { dateFormat.format(Date(it)) } ?: "結束日期",
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
                    label = { Text("預算金額 (選填)") },
                    placeholder = { Text("例如：50000") },
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
                        name = name,
                        description = description,
                        defaultCurrency = currency,
                        startDate = startDate,
                        endDate = endDate,
                        budget = budgetText.toDoubleOrNull(),
                        updatedAt = System.currentTimeMillis()
                    ) ?: Project(
                        name = name,
                        description = description,
                        defaultCurrency = currency,
                        startDate = startDate,
                        endDate = endDate,
                        budget = budgetText.toDoubleOrNull()
                    )
                    onConfirm(updatedProject)
                },
                enabled = name.isNotBlank()
            ) {
                Text(if (isEditMode) "儲存" else "建立")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
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
                }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("取消") }
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
                }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
