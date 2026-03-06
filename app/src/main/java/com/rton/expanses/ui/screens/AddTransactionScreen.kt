package com.rton.expanses.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rton.expanses.data.model.Category
import com.rton.expanses.data.model.PaymentMethod
import com.rton.expanses.data.model.Project
import com.rton.expanses.data.model.Transaction
import com.rton.expanses.ui.components.ExpenseNumpad
import com.rton.expanses.ui.util.ExpressionEvaluator
import com.rton.expanses.ui.util.PaymentIconMapper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    categories: List<Category>,
    projects: List<Project> = emptyList(),
    paymentMethods: List<PaymentMethod> = emptyList(),
    existingTransaction: Transaction? = null,
    onSave: (Transaction) -> Unit,
    onBack: () -> Unit
) {
    val isEditMode = existingTransaction != null

    var amountText by remember {
        mutableStateOf(existingTransaction?.amount?.let {
            if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        } ?: "")
    }
    var selectedCategory by remember { mutableStateOf<Category?>(
        existingTransaction?.categoryId?.let { catId -> categories.find { it.id == catId } }
    ) }
    var isExpense by remember { mutableStateOf(existingTransaction?.isExpense ?: true) }
    var note by remember { mutableStateOf(existingTransaction?.note ?: "") }
    var showNoteField by remember { mutableStateOf(existingTransaction?.note?.isNotBlank() == true) }
    var selectedProjectId by remember { mutableStateOf(existingTransaction?.projectId) }
    var showProjectDropdown by remember { mutableStateOf(false) }

    // Auto-select project whose date range covers today (only in add mode)
    LaunchedEffect(projects) {
        if (existingTransaction == null && selectedProjectId == null) {
            val now = System.currentTimeMillis()
            val match = projects.firstOrNull { p ->
                p.isActive && p.startDate != null && p.endDate != null
                    && now >= p.startDate && now <= p.endDate
            }
            if (match != null) selectedProjectId = match.id
        }
    }
    var selectedPaymentMethodId by remember {
        mutableStateOf(
            existingTransaction?.paymentMethodId
                ?: paymentMethods.firstOrNull { it.isDefault }?.id
                ?: paymentMethods.firstOrNull()?.id
        )
    }

    // ─── Two-level category state ────────────────────────────
    var expandedParentId by remember { mutableStateOf<Long?>(null) }

    // ─── Numpad visibility state ─────────────────────────────
    var showNumpad by remember { mutableStateOf(true) }

    // ─── Back handler: close numpad first, then exit ─────────
    BackHandler {
        if (showNumpad) {
            showNumpad = false
        } else {
            onBack()
        }
    }

    var selectedDateTime by remember { mutableStateOf(existingTransaction?.date ?: System.currentTimeMillis()) }
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDateTime
    )
    val timePickerState = rememberTimePickerState(
        initialHour = Calendar.getInstance().apply { timeInMillis = selectedDateTime }.get(Calendar.HOUR_OF_DAY),
        initialMinute = Calendar.getInstance().apply { timeInMillis = selectedDateTime }.get(Calendar.MINUTE),
        is24Hour = true
    )

    val view = LocalView.current
    LaunchedEffect(timePickerState.hour, timePickerState.minute) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    // Update selectedCategory when isExpense changes (only in add mode)
    LaunchedEffect(isExpense, categories) {
        if (!isEditMode && selectedCategory != null) {
            val matchesType = categories.find { it.id == selectedCategory?.id }
            if (matchesType == null) selectedCategory = null
        }
    }

    // Auto-hide numpad on scroll
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress && showNumpad) {
            showNumpad = false
        }
    }

    // Derive parent and child categories
    val parentCategories = remember(categories, isExpense) {
        categories.filter { it.isExpense == isExpense && it.parentId == null }
    }
    val childCategoriesMap = remember(categories) {
        categories.filter { it.parentId != null }.groupBy { it.parentId }
    }

    // Can save?
    val resolvedAmount = if (amountText.any { it in "+−×÷" }) {
        ExpressionEvaluator.evaluate(amountText)
    } else {
        amountText.toDoubleOrNull()
    }
    val canSave = resolvedAmount?.let { it > 0 } == true

    fun doSave() {
        val amount = resolvedAmount ?: return
        if (amount <= 0) return

        val transaction = Transaction(
            id = existingTransaction?.id ?: 0,
            amount = amount,
            note = note,
            categoryId = selectedCategory?.id,
            isExpense = isExpense,
            date = selectedDateTime,
            source = existingTransaction?.source ?: "manual",
            projectId = selectedProjectId,
            paymentMethodId = selectedPaymentMethodId,
            isDraft = false,
            createdAt = existingTransaction?.createdAt ?: System.currentTimeMillis()
        )
        onSave(transaction)
        onBack()
    }

    // Operator input handler
    fun handleInput(key: String) {
        when (key) {
            "+", "−", "×", "÷" -> {
                // Don't add operator at start or after another operator
                if (amountText.isEmpty()) return
                val lastChar = amountText.last()
                if (lastChar in "+−×÷") {
                    // Replace last operator
                    amountText = amountText.dropLast(1) + key
                } else {
                    amountText += key
                }
            }
            else -> {
                // Digit input
                if (amountText.length >= 20) return
                // After a calculation result, start fresh on digit
                amountText += key
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isEditMode -> "編輯記帳"
                            isExpense -> "新增支出"
                            else -> "新增收入"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showNumpad) showNumpad = false else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { doSave() },
                        enabled = canSave
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "儲存",
                            tint = if (canSave) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ═══════════════════════════════════════════════════════
            // Scrollable form
            // ═══════════════════════════════════════════════════════
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(bottom = 8.dp)
            ) {
                // ─── Amount Display (tappable) ───────────────────
                Card(
                    onClick = { showNumpad = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Text(
                            "金額",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "$",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = if (amountText.isEmpty()) "0" else amountText,
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-1).sp
                                    ),
                                    color = if (amountText.isEmpty())
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.End
                                )
                                // Show calculated result when expression
                                if (amountText.any { it in "+−×÷" }) {
                                    resolvedAmount?.let { result ->
                                        Text(
                                            "= ${ExpressionEvaluator.formatResult(result)}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                            if (!showNumpad) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "編輯金額",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // ─── Expense / Income Toggle ─────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) {
                        SegmentedButton(
                            selected = isExpense,
                            onClick = { isExpense = true; expandedParentId = null },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            label = { Text("支出") }
                        )
                        SegmentedButton(
                            selected = !isExpense,
                            onClick = { isExpense = false; expandedParentId = null },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            label = { Text("收入") }
                        )
                    }
                }

                // ─── Date display ────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val cal = Calendar.getInstance().apply { timeInMillis = selectedDateTime }
                    val dFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()) }
                    val tFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                    
                    OutlinedCard(
                        onClick = { showDatePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.DateRange,
                                contentDescription = "選擇日期",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(dFormat.format(cal.time), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    OutlinedCard(
                        onClick = { showTimePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = "選擇時間",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tFormat.format(cal.time), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // ─── Two-Level Category Selector ─────────────────
                if (parentCategories.isNotEmpty()) {
                    Text(
                        "分類",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp)
                    )
                    // Parent categories
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        parentCategories.forEach { parent ->
                            val chipColor = Color(parent.color)
                            val isParentSelected = expandedParentId == parent.id ||
                                selectedCategory?.parentId == parent.id ||
                                selectedCategory?.id == parent.id
                            FilterChip(
                                selected = isParentSelected,
                                onClick = {
                                    val children = childCategoriesMap[parent.id]
                                    if (children.isNullOrEmpty()) {
                                        selectedCategory = parent
                                        expandedParentId = null
                                    } else {
                                        expandedParentId = if (expandedParentId == parent.id) null else parent.id
                                    }
                                },
                                label = { Text(parent.name, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = chipColor.copy(alpha = 0.1f),
                                    labelColor = chipColor,
                                    selectedContainerColor = chipColor,
                                    selectedLabelColor = Color.White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = chipColor.copy(alpha = 0.3f),
                                    selectedBorderColor = chipColor,
                                    enabled = true,
                                    selected = isParentSelected
                                )
                            )
                        }
                    }

                    // Child categories (animated expand)
                    AnimatedVisibility(visible = expandedParentId != null) {
                        val children = childCategoriesMap[expandedParentId] ?: emptyList()
                        if (children.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(start = 32.dp, end = 16.dp, top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                children.forEach { child ->
                                    val chipColor = Color(child.color)
                                    val isSelected = selectedCategory?.id == child.id
                                    AssistChip(
                                        onClick = { selectedCategory = child },
                                        label = { Text(child.name, style = MaterialTheme.typography.labelSmall) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (isSelected) chipColor.copy(alpha = 0.2f)
                                                else MaterialTheme.colorScheme.surface,
                                            labelColor = if (isSelected) chipColor
                                                else MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = AssistChipDefaults.assistChipBorder(
                                            borderColor = if (isSelected) chipColor
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            enabled = true
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Show selected category label
                    selectedCategory?.let { cat ->
                        val parentName = if (cat.parentId != null) {
                            parentCategories.find { it.id == cat.parentId }?.name ?: ""
                        } else ""
                        val label = if (parentName.isNotBlank()) "$parentName > ${cat.name}" else cat.name
                        Text(
                            "已選: $label",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(cat.color),
                            modifier = Modifier.padding(start = 20.dp, top = 4.dp)
                        )
                    }
                }

                // ─── Payment Method Selector ─────────────────────
                if (paymentMethods.isNotEmpty()) {
                    Text(
                        "支付工具",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        paymentMethods.forEach { method ->
                            val isSelected = selectedPaymentMethodId == method.id
                            val color = Color(method.color)
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedPaymentMethodId = method.id },
                                label = { Text(method.name, style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = {
                                    Icon(
                                        PaymentIconMapper.getIcon(method.icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                        else color
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = color.copy(alpha = 0.2f),
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                    selectedLeadingIconColor = color
                                )
                            )
                        }
                    }
                }

                // ─── Project Selector ────────────────────────────
                if (projects.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        OutlinedCard(
                            onClick = { showProjectDropdown = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row {
                                    Icon(
                                        Icons.Filled.Luggage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = projects.find { it.id == selectedProjectId }?.name
                                            ?: "選擇旅行專案 (選填)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selectedProjectId != null)
                                            MaterialTheme.colorScheme.onSurface
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selectedProjectId != null) {
                                    IconButton(
                                        onClick = { selectedProjectId = null },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "清除",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = showProjectDropdown,
                            onDismissRequest = { showProjectDropdown = false }
                        ) {
                            projects.forEach { project ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${project.name}  (${project.defaultCurrency})")
                                    },
                                    onClick = {
                                        selectedProjectId = project.id
                                        showProjectDropdown = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Luggage, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                }

                // ─── Note Field ──────────────────────────────────
                if (showNoteField) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = { Text("備註...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                TextButton(
                    onClick = { showNoteField = !showNoteField },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(if (showNoteField) "隱藏備註" else "＋ 新增備註")
                }

                // ─── Save Button (always visible) ────────────────
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { doSave() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = canSave
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isEditMode) "儲存變更" else "確認記帳",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // ═══════════════════════════════════════════════════════
            // Collapsible Numpad
            // ═══════════════════════════════════════════════════════
            AnimatedVisibility(
                visible = showNumpad,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                ExpenseNumpad(
                    displayAmount = amountText,
                    onDigitClick = { key -> handleInput(key) },
                    onDotClick = {
                        // Find last number segment (after last operator)
                        val lastSegment = amountText.takeLastWhile { it !in "+−×÷" }
                        if (!lastSegment.contains('.')) {
                            amountText = if (amountText.isEmpty() || amountText.last() in "+−×÷") {
                                amountText + "0."
                            } else {
                                "$amountText."
                            }
                        }
                    },
                    onBackspaceClick = {
                        if (amountText.isNotEmpty()) {
                            amountText = amountText.dropLast(1)
                        }
                    },
                    onConfirm = { showNumpad = false }
                )
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val currentCal = Calendar.getInstance().apply { timeInMillis = selectedDateTime }
                        val newCal = Calendar.getInstance().apply { timeInMillis = millis }
                        currentCal.set(Calendar.YEAR, newCal.get(Calendar.YEAR))
                        currentCal.set(Calendar.MONTH, newCal.get(Calendar.MONTH))
                        currentCal.set(Calendar.DAY_OF_MONTH, newCal.get(Calendar.DAY_OF_MONTH))
                        selectedDateTime = currentCal.timeInMillis
                    }
                    showDatePicker = false
                }) {
                    Text("確定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("選擇時間") },
            text = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val currentCal = Calendar.getInstance().apply { timeInMillis = selectedDateTime }
                    currentCal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    currentCal.set(Calendar.MINUTE, timePickerState.minute)
                    selectedDateTime = currentCal.timeInMillis
                    showTimePicker = false
                }) {
                    Text("確定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("取消")
                }
            }
        )
    }
}
