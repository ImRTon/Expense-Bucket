package com.rton.expensebucket.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable
fun YearMonthPickerDialog(
    initialTimeMillis: Long,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedYear by remember {
        mutableIntStateOf(Calendar.getInstance().apply { timeInMillis = initialTimeMillis }.get(Calendar.YEAR))
    }
    var selectedMonth by remember { // 0-based
        mutableIntStateOf(Calendar.getInstance().apply { timeInMillis = initialTimeMillis }.get(Calendar.MONTH))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedYear-- }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "上一年")
                }
                Text(
                    text = "${selectedYear}年",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = { selectedYear++ }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "下一年")
                }
            }
        },
        text = {
            // Grid of 12 months
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (row in 0..3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0..2) {
                            val month = row * 3 + col
                            val isSelected = month == selectedMonth
                            TextButton(
                                onClick = { selectedMonth = month },
                                colors = if (isSelected) ButtonDefaults.textButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) else ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text("${month + 1}月", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.YEAR, selectedYear)
                    cal.set(Calendar.MONTH, selectedMonth)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    onDateSelected(cal.timeInMillis)
                }
            ) {
                Text("確定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun YearPickerDialog(
    initialTimeMillis: Long,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val initialYear = remember { Calendar.getInstance().apply { timeInMillis = initialTimeMillis }.get(Calendar.YEAR) }
    var selectedYear by remember { mutableIntStateOf(initialYear) }
    
    // Range of years ± 10
    val years = remember { (initialYear - 10)..(initialYear + 10) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇年份") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(years.count()) { index ->
                    val year = years.elementAt(index)
                    val isSelected = year == selectedYear
                    TextButton(
                        onClick = { selectedYear = year },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .padding(vertical = 2.dp),
                        colors = if (isSelected) ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) else ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("${year}年", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.YEAR, selectedYear)
                    cal.set(Calendar.DAY_OF_YEAR, 1)
                    onDateSelected(cal.timeInMillis)
                }
            ) {
                Text("確定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
