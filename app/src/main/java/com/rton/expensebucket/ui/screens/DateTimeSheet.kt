package com.rton.expensebucket.ui.screens

import androidx.compose.ui.platform.LocalContext
import com.rton.expensebucket.R
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeSheet(
    initialDateTimeMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val strings = LocalContext.current.resources

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val view = LocalView.current

    var editingTimeTab by remember { mutableStateOf(false) }
    var workingMillis by remember(initialDateTimeMillis) { mutableLongStateOf(initialDateTimeMillis) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = localMillisToUtcMidnight(initialDateTimeMillis)
    )
    val initialCal = remember(initialDateTimeMillis) {
        Calendar.getInstance().apply { timeInMillis = initialDateTimeMillis }
    }
    val timePickerState = rememberTimePickerState(
        initialHour = initialCal.get(Calendar.HOUR_OF_DAY),
        initialMinute = initialCal.get(Calendar.MINUTE),
        is24Hour = true
    )

    // Keep workingMillis updated when DatePicker or TimePicker changes
    LaunchedEffect(datePickerState.selectedDateMillis, timePickerState.hour, timePickerState.minute) {
        val baseCal = Calendar.getInstance().apply { timeInMillis = workingMillis }
        datePickerState.selectedDateMillis?.let { utcMillis ->
            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = utcMillis
            }
            baseCal.set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
            baseCal.set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
            baseCal.set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
        }
        baseCal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
        baseCal.set(Calendar.MINUTE, timePickerState.minute)
        workingMillis = baseCal.timeInMillis
    }

    LaunchedEffect(timePickerState.hour, timePickerState.minute) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun selectQuickDayOffset(dayOffset: Int) {
        val targetCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
            set(Calendar.MINUTE, timePickerState.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        workingMillis = targetCal.timeInMillis
        datePickerState.selectedDateMillis = localMillisToUtcMidnight(targetCal.timeInMillis)
    }

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", strings.configuration.locales[0]) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", strings.configuration.locales[0]) }
    val activeDayOffset = remember(workingMillis) { getDayOffsetFromToday(workingMillis) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // ─── Header: Quick Day Chips + Confirm Button ───────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to strings.getString(R.string.today), -1 to strings.getString(R.string.yesterday), -2 to strings.getString(R.string.day_before_yesterday)).forEach { (offset, label) ->
                        FilterChip(
                            selected = activeDayOffset == offset,
                            onClick = { selectQuickDayOffset(offset) },
                            label = {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        )
                    }
                }
                Button(
                    onClick = { onConfirm(workingMillis) },
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(strings.getString(R.string.ok))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ─── Date / Time Mode Switcher ──────────────────────────
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                SegmentedButton(
                    selected = !editingTimeTab,
                    onClick = { editingTimeTab = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        Icon(
                            Icons.Filled.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(dateFormat.format(Date(workingMillis))) }
                )
                SegmentedButton(
                    selected = editingTimeTab,
                    onClick = { editingTimeTab = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(timeFormat.format(Date(workingMillis))) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!editingTimeTab) {
                DatePicker(
                    state = datePickerState,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            }
        }
    }
}

private fun localMillisToUtcMidnight(localMillis: Long): Long {
    val localCal = Calendar.getInstance().apply { timeInMillis = localMillis }
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            localCal.get(Calendar.YEAR),
            localCal.get(Calendar.MONTH),
            localCal.get(Calendar.DAY_OF_MONTH),
            0,
            0,
            0
        )
    }
    return utcCal.timeInMillis
}

internal fun getDayOffsetFromToday(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): Int? {
    val todayStart = AddTransactionFormLogic.startOfDay(nowMillis)
    val targetStart = AddTransactionFormLogic.startOfDay(targetMillis)
    val diffDays = ((targetStart - todayStart) / (24L * 60 * 60 * 1000)).toInt()
    return if (diffDays in -2..0) diffDays else null
}

internal fun formatFriendlyDateTime(strings: android.content.res.Resources, targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val timeStr = SimpleDateFormat("HH:mm", strings.configuration.locales[0]).format(Date(targetMillis))
    return when (getDayOffsetFromToday(targetMillis, nowMillis)) {
        0 -> strings.getString(R.string.today_time, timeStr)
        -1 -> strings.getString(R.string.yesterday_time, timeStr)
        -2 -> strings.getString(R.string.day_before_yesterday_time, timeStr)
        else -> {
            val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val targetCal = Calendar.getInstance().apply { timeInMillis = targetMillis }
            val pattern = if (nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR)) {
                "MM/dd (E) HH:mm"
            } else {
                "yyyy/MM/dd HH:mm"
            }
            SimpleDateFormat(pattern, strings.configuration.locales[0]).format(Date(targetMillis))
        }
    }
}
