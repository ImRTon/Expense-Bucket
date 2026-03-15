package com.rton.expensebucket.ui.screens

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
import androidx.compose.ui.platform.LocalFocusManager
import android.view.HapticFeedbackConstants
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rton.expensebucket.data.HistoricalCashRateProvider
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Project
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.ui.components.ExpenseNumpad
import com.rton.expensebucket.ui.model.TransactionPrefill
import com.rton.expensebucket.ui.util.CurrencyFormats
import com.rton.expensebucket.ui.util.ExpressionEvaluator
import com.rton.expensebucket.ui.util.PaymentIconMapper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    categories: List<Category>,
    projects: List<Project> = emptyList(),
    paymentMethods: List<PaymentMethod> = emptyList(),
    existingTransaction: Transaction? = null,
    prefill: TransactionPrefill? = null,
    onSave: (Transaction) -> Unit,
    onBack: () -> Unit
) {
    val isEditMode = existingTransaction != null
    val initialAmount = existingTransaction?.amount ?: prefill?.amount
    val initialNote = existingTransaction?.note ?: prefill?.note.orEmpty()
    val initialIsExpense = existingTransaction?.isExpense ?: prefill?.isExpense ?: true

    var amountText by remember {
        mutableStateOf(initialAmount?.let {
            if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        } ?: "")
    }
    var selectedCategory by remember { mutableStateOf<Category?>(
        existingTransaction?.categoryId?.let { catId -> categories.find { it.id == catId } }
    ) }
    var isExpense by remember { mutableStateOf(initialIsExpense) }
    var note by remember { mutableStateOf(initialNote) }
    var showNoteField by remember { mutableStateOf(initialNote.isNotBlank()) }
    var selectedProjectId by remember { mutableStateOf(existingTransaction?.projectId) }
    var showProjectDropdown by remember { mutableStateOf(false) }
    val initialSettlementCurrency = existingTransaction?.projectId
        ?.let { projectId -> projects.find { it.id == projectId }?.defaultCurrency }
        ?: "TWD"
    var selectedCurrency by remember {
        mutableStateOf(existingTransaction?.currency ?: prefill?.currency ?: initialSettlementCurrency)
    }
    var exchangeRateText by remember {
        mutableStateOf(CurrencyFormats.formatRate(existingTransaction?.exchangeRate ?: prefill?.exchangeRate ?: 1.0))
    }
    var showCurrencyMenu by remember { mutableStateOf(false) }
    var previousSettlementCurrency by remember { mutableStateOf(initialSettlementCurrency) }
    var isFetchingExchangeRate by remember { mutableStateOf(false) }
    var exchangeRateError by remember { mutableStateOf<String?>(null) }
    var skipInitialRateFetch by remember {
        mutableStateOf(
            existingTransaction != null || (
                prefill != null &&
                    prefill.currency != initialSettlementCurrency &&
                    prefill.exchangeRate > 0
                )
        )
    }

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

    // ─── Two-level category and payment methods state ────────────────────────────
    var expandedParentId by remember { mutableStateOf<Long?>(null) }
    var expandedPaymentParentId by remember { mutableStateOf<Long?>(null) }

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

    var selectedDateTime by remember {
        mutableStateOf(existingTransaction?.date ?: prefill?.date ?: System.currentTimeMillis())
    }
    
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
    val focusManager = LocalFocusManager.current
    
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

    val parentCategories = remember(categories, isExpense) {
        categories.filter { it.isExpense == isExpense && it.parentId == null }
    }
    val childCategoriesMap = remember(categories) {
        categories.filter { it.parentId != null }.groupBy { it.parentId }
    }

    val parentPaymentMethods = remember(paymentMethods) {
        paymentMethods.filter { it.parentId == null }.sortedBy { it.sortOrder }
    }
    val childPaymentMethodsMap = remember(paymentMethods) {
        paymentMethods.filter { it.parentId != null }.groupBy { it.parentId }
    }
    val selectedProject = remember(selectedProjectId, projects) {
        projects.find { it.id == selectedProjectId }
    }
    val settlementCurrency = selectedProject?.defaultCurrency ?: "TWD"

    // Resolve selected payment method parent for UI initialization
    LaunchedEffect(selectedPaymentMethodId, paymentMethods) {
        if (selectedPaymentMethodId != null && expandedPaymentParentId == null) {
            val selectedMethod = paymentMethods.find { it.id == selectedPaymentMethodId }
            if (selectedMethod?.parentId != null) {
                expandedPaymentParentId = selectedMethod.parentId
            }
        }
    }

    LaunchedEffect(settlementCurrency) {
        if (selectedCurrency == previousSettlementCurrency) {
            selectedCurrency = settlementCurrency
        }
        if (selectedCurrency == settlementCurrency) {
            exchangeRateText = "1"
        }
        previousSettlementCurrency = settlementCurrency
    }

    LaunchedEffect(selectedCurrency, settlementCurrency, selectedDateTime) {
        if (skipInitialRateFetch) {
            skipInitialRateFetch = false
            return@LaunchedEffect
        }

        if (selectedCurrency == settlementCurrency) {
            isFetchingExchangeRate = false
            exchangeRateError = null
            exchangeRateText = "1"
            return@LaunchedEffect
        }

        isFetchingExchangeRate = true
        exchangeRateError = null
        HistoricalCashRateProvider.getExchangeRate(
            fromCurrency = selectedCurrency,
            toCurrency = settlementCurrency,
            dateMillis = selectedDateTime
        ).onSuccess { fetchedRate ->
            exchangeRateText = CurrencyFormats.formatRate(fetchedRate)
        }.onFailure {
            exchangeRateText = ""
            exchangeRateError = "歷史現鈔匯率抓取失敗，可手動修正"
        }
        isFetchingExchangeRate = false
    }

    // Can save?
    val resolvedAmount = if (amountText.any { it in "+−×÷" }) {
        ExpressionEvaluator.evaluate(amountText)
    } else {
        amountText.toDoubleOrNull()
    }
    val requiresExchangeRate = selectedCurrency != settlementCurrency
    val parsedExchangeRate = if (requiresExchangeRate) exchangeRateText.toDoubleOrNull() else 1.0
    val convertedAmount = if (resolvedAmount != null && parsedExchangeRate != null) {
        resolvedAmount * parsedExchangeRate
    } else {
        null
    }
    val canSave = resolvedAmount?.let { it > 0 } == true &&
        parsedExchangeRate?.let { it > 0 } == true

    fun doSave() {
        val amount = resolvedAmount ?: return
        val exchangeRate = parsedExchangeRate ?: return
        if (amount <= 0) return

        val transaction = Transaction(
            id = existingTransaction?.id ?: 0,
            amount = amount,
            note = note,
            categoryId = selectedCategory?.id,
            isExpense = isExpense,
            date = selectedDateTime,
            source = existingTransaction?.source ?: prefill?.source ?: "manual",
            projectId = selectedProjectId,
            paymentMethodId = selectedPaymentMethodId,
            currency = selectedCurrency,
            exchangeRate = exchangeRate,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ═══════════════════════════════════════════════════════
            // Scrollable form
            // ═══════════════════════════════════════════════════════
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .imePadding()
                    .padding(bottom = 8.dp)
            ) {
                // ─── Amount Display (tappable) ───────────────────
                Card(
                    onClick = { 
                        focusManager.clearFocus()
                        showNumpad = true 
                    },
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
                                selectedCurrency,
                                style = MaterialTheme.typography.titleLarge.copy(
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
                            onClick = { 
                                isExpense = true
                                expandedParentId = null
                                focusManager.clearFocus()
                                showNumpad = false
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            label = { Text("支出") }
                        )
                        SegmentedButton(
                            selected = !isExpense,
                            onClick = { 
                                isExpense = false
                                expandedParentId = null
                                focusManager.clearFocus()
                                showNumpad = false
                            },
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
                        onClick = { 
                            showDatePicker = true
                            focusManager.clearFocus()
                            showNumpad = false
                        },
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
                        onClick = { 
                            showTimePicker = true
                            focusManager.clearFocus()
                            showNumpad = false
                        },
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
                                    focusManager.clearFocus()
                                    showNumpad = false
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
                                        onClick = { 
                                            selectedCategory = child 
                                            focusManager.clearFocus()
                                            showNumpad = false
                                        },
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

                // ─── Two-Level Payment Method Selector ─────────────────────
                if (parentPaymentMethods.isNotEmpty()) {
                    Text(
                        "支付工具",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp)
                    )
                    // Parent payment methods
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        parentPaymentMethods.forEach { parent ->
                            val chipColor = Color(parent.color)
                            val isParentSelected = expandedPaymentParentId == parent.id ||
                                (selectedPaymentMethodId != null && paymentMethods.find { it.id == selectedPaymentMethodId }?.parentId == parent.id) ||
                                selectedPaymentMethodId == parent.id
                                
                            FilterChip(
                                selected = isParentSelected,
                                onClick = {
                                    val children = childPaymentMethodsMap[parent.id]
                                    if (children.isNullOrEmpty()) {
                                        selectedPaymentMethodId = parent.id
                                        expandedPaymentParentId = null
                                    } else {
                                        expandedPaymentParentId = if (expandedPaymentParentId == parent.id) null else parent.id
                                    }
                                    focusManager.clearFocus()
                                    showNumpad = false
                                },
                                label = { Text(parent.name, style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = {
                                    Icon(
                                        PaymentIconMapper.getIcon(parent.icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isParentSelected) Color.White else chipColor
                                    )
                                },
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

                    // Child payment methods (animated expand)
                    AnimatedVisibility(visible = expandedPaymentParentId != null) {
                        val children = childPaymentMethodsMap[expandedPaymentParentId] ?: emptyList()
                        if (children.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(start = 32.dp, end = 16.dp, top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                children.forEach { child ->
                                    val chipColor = Color(child.color)
                                    val isSelected = selectedPaymentMethodId == child.id
                                    AssistChip(
                                        onClick = { 
                                            selectedPaymentMethodId = child.id 
                                            focusManager.clearFocus()
                                            showNumpad = false
                                        },
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

                    // Show selected payment method label
                    selectedPaymentMethodId?.let { methodId ->
                        val method = paymentMethods.find { it.id == methodId }
                        method?.let { m ->
                            val parentName = if (m.parentId != null) {
                                parentPaymentMethods.find { it.id == m.parentId }?.name ?: ""
                            } else ""
                            val label = if (parentName.isNotBlank()) "$parentName > ${m.name}" else m.name
                            Text(
                                "已選: $label",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(m.color),
                                modifier = Modifier.padding(start = 20.dp, top = 4.dp)
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
                            onClick = { 
                                showProjectDropdown = true 
                                focusManager.clearFocus()
                                showNumpad = false
                            },
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
                                        onClick = {
                                            val oldSettlementCurrency = settlementCurrency
                                            selectedProjectId = null
                                            if (selectedCurrency == oldSettlementCurrency) {
                                                selectedCurrency = "TWD"
                                                exchangeRateText = "1"
                                            }
                                        },
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
                                        val oldSettlementCurrency = settlementCurrency
                                        selectedProjectId = project.id
                                        if (selectedCurrency == oldSettlementCurrency) {
                                            selectedCurrency = project.defaultCurrency
                                            exchangeRateText = "1"
                                        }
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedCard(
                        onClick = {
                            showCurrencyMenu = true
                            focusManager.clearFocus()
                            showNumpad = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.SwapHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        "記帳幣別",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        if (requiresExchangeRate) {
                                            "$selectedCurrency -> $settlementCurrency"
                                        } else {
                                            selectedCurrency
                                        },
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }
                            Icon(
                                Icons.Filled.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showCurrencyMenu,
                        onDismissRequest = { showCurrencyMenu = false }
                    ) {
                        CurrencyFormats.supportedCurrencies.forEach { currency ->
                            DropdownMenuItem(
                                text = { Text(currency) },
                                onClick = {
                                    selectedCurrency = currency
                                    if (currency == settlementCurrency) {
                                        exchangeRateText = "1"
                                    } else if (exchangeRateText == "1") {
                                        exchangeRateText = ""
                                    }
                                    showCurrencyMenu = false
                                }
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = requiresExchangeRate) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "結算幣別: $settlementCurrency${selectedProject?.let { " (${it.name})" } ?: " (日常帳)"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = exchangeRateText,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.all { it.isDigit() || it == '.' }) {
                                        exchangeRateText = newValue
                                    }
                                },
                                label = { Text("現鈔匯率") },
                                placeholder = { Text("1 $selectedCurrency = ? $settlementCurrency") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                                ),
                                supportingText = {
                                    Text(
                                        when {
                                            isFetchingExchangeRate -> "正在依記帳日期抓取歷史現鈔匯率..."
                                            exchangeRateError != null -> exchangeRateError!!
                                            else -> "已依記帳日期自動帶入歷史現鈔匯率，之後統整與專案預算都用它換算。"
                                        }
                                    )
                                }
                            )

                            convertedAmount?.let {
                                Text(
                                    "換算後約 ${CurrencyFormats.formatAmount(settlementCurrency, it)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
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
                        singleLine = false,
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                TextButton(
                    onClick = { 
                        showNoteField = !showNoteField 
                        if (!showNoteField) focusManager.clearFocus()
                        showNumpad = false
                    },
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
                modifier = Modifier.align(Alignment.BottomCenter),
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
