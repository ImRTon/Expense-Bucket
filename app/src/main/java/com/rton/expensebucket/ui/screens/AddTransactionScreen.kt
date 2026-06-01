package com.rton.expensebucket.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.view.HapticFeedbackConstants
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rton.expensebucket.data.HistoricalCashRateProvider
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Project
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.model.amortizationEndYearMonth
import com.rton.expensebucket.data.model.amortizedShare
import com.rton.expensebucket.data.model.millisForYearMonth
import com.rton.expensebucket.data.model.yearMonthFromMillis
import com.rton.expensebucket.ui.components.ExpenseNumpad
import com.rton.expensebucket.ui.components.YearMonthPickerDialog
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
    initialProjectId: Long? = null,
    onSave: (Transaction) -> Unit,
    onBack: () -> Unit
) {
    val isEditMode = existingTransaction != null
    val initialProjectSelectionId = existingTransaction?.projectId ?: initialProjectId
    val initialAmount = existingTransaction?.amount ?: prefill?.amount
    val initialPersonalAmount = existingTransaction
        ?.takeIf { it.isExpense }
        ?.personalAmount
    val initialNote = existingTransaction?.note ?: prefill?.note.orEmpty()
    val initialIsExpense = existingTransaction?.isExpense ?: prefill?.isExpense ?: true

    var amountText by remember {
        mutableStateOf(initialAmount?.let {
            if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        } ?: "")
    }
    var personalAmountText by remember {
        mutableStateOf(initialPersonalAmount?.let { formatEditableTransactionAmount(it) } ?: "")
    }
    var showPersonalAmountDialog by remember { mutableStateOf(false) }
    var showAmortizationDialog by remember { mutableStateOf(false) }
    var amortizationEnabled by remember {
        mutableStateOf(existingTransaction?.amortizationEnabled == true)
    }
    var amortizationStartYearMonth by remember {
        mutableStateOf(
            existingTransaction?.amortizationStartYearMonth
                ?: yearMonthFromMillis(existingTransaction?.date ?: prefill?.date ?: System.currentTimeMillis())
        )
    }
    var amortizationMonthCount by remember {
        mutableStateOf(existingTransaction?.amortizationMonthCount?.takeIf { it >= 2 } ?: 6)
    }
    var selectedCategory by remember { mutableStateOf<Category?>(
        existingTransaction?.categoryId?.let { catId -> categories.find { it.id == catId } }
    ) }
    var isExpense by remember { mutableStateOf(initialIsExpense) }
    var note by remember { mutableStateOf(initialNote) }
    var showNoteField by remember { mutableStateOf(initialNote.isNotBlank()) }
    var selectedProjectId by remember { mutableStateOf(initialProjectSelectionId) }
    var hasUserEditedProjectSelection by remember {
        mutableStateOf(initialProjectSelectionId != null)
    }
    var showProjectDropdown by remember { mutableStateOf(false) }
    val initialSettlementCurrency = initialProjectSelectionId
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

    // Auto-select active travel project that covers the selected transaction date.
    LaunchedEffect(projects, selectedDateTime, existingTransaction, hasUserEditedProjectSelection) {
        if (existingTransaction != null || hasUserEditedProjectSelection) return@LaunchedEffect

        val match = projects
            .filter { project ->
                project.isActive &&
                    project.startDate != null &&
                    project.endDate != null &&
                    isWithinProjectDateRange(
                        targetDateTime = selectedDateTime,
                        startDate = project.startDate,
                        endDate = project.endDate
                    )
            }
            .minByOrNull { project ->
                (project.endDate ?: Long.MAX_VALUE) - (project.startDate ?: Long.MIN_VALUE)
            }

        selectedProjectId = match?.id
    }
    
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

    LaunchedEffect(isExpense) {
        if (!isExpense) {
            personalAmountText = ""
            showPersonalAmountDialog = false
            showAmortizationDialog = false
            amortizationEnabled = false
        }
    }

    LaunchedEffect(selectedDateTime, amortizationEnabled) {
        if (!amortizationEnabled) {
            amortizationStartYearMonth = yearMonthFromMillis(selectedDateTime)
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
    val parsedPersonalAmount = if (isExpense) personalAmountText.toDoubleOrNull() else null
    val convertedAmount = if (resolvedAmount != null && parsedExchangeRate != null) {
        resolvedAmount * parsedExchangeRate
    } else {
        null
    }
    val budgetBaseAmount = if (isExpense) parsedPersonalAmount ?: resolvedAmount else null
    val amortizationSummary = remember(
        isExpense,
        amortizationEnabled,
        amortizationStartYearMonth,
        amortizationMonthCount,
        budgetBaseAmount,
        selectedCurrency
    ) {
        if (isExpense && amortizationEnabled && amortizationMonthCount >= 2) {
            formatAmortizationSummary(
                currency = selectedCurrency,
                amount = budgetBaseAmount,
                startYearMonth = amortizationStartYearMonth,
                monthCount = amortizationMonthCount
            )
        } else {
            null
        }
    }
    val personalAmountError = when {
        parsedPersonalAmount == null && personalAmountText.isNotBlank() -> "請輸入正確金額"
        parsedPersonalAmount != null && parsedPersonalAmount <= 0 -> "我的花費需大於 0"
        parsedPersonalAmount != null && resolvedAmount != null && parsedPersonalAmount > resolvedAmount ->
            "我的花費不能大於支出總額"
        else -> null
    }
    val canSave = resolvedAmount?.let { it > 0 } == true &&
        parsedExchangeRate?.let { it > 0 } == true &&
        personalAmountError == null

    fun openPersonalAmountEditor() {
        if (!isExpense) return
        focusManager.clearFocus()
        showNumpad = false
        showPersonalAmountDialog = true
    }

    fun doSave() {
        val amount = resolvedAmount ?: return
        val exchangeRate = parsedExchangeRate ?: return
        if (amount <= 0) return

        val transaction = Transaction(
            id = existingTransaction?.id ?: 0,
            amount = amount,
            personalAmount = if (isExpense) parsedPersonalAmount?.takeIf { it > 0 } else null,
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
            amortizationEnabled = isExpense && amortizationEnabled && amortizationMonthCount >= 2,
            amortizationStartYearMonth = if (isExpense && amortizationEnabled && amortizationMonthCount >= 2) {
                amortizationStartYearMonth
            } else {
                null
            },
            amortizationMonthCount = if (isExpense && amortizationEnabled && amortizationMonthCount >= 2) {
                amortizationMonthCount
            } else {
                null
            },
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Card(
                        onClick = {
                            focusManager.clearFocus()
                            showNumpad = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isExpense) {
                                    PersonalAmountHandle(
                                        isActive = parsedPersonalAmount != null,
                                        onTrigger = { openPersonalAmountEditor() }
                                    )
                                }
                                Text(
                                    if (isExpense) "支出總額" else "收入金額",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isExpense && parsedPersonalAmount != null) {
                                    Text(
                                        "已設定我的花費",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
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
                                    if (isExpense) parsedPersonalAmount?.let {
                                        Text(
                                            "我的花費 ${CurrencyFormats.formatAmount(selectedCurrency, it)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.fillMaxWidth()
                                        )
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
                            if (isExpense) personalAmountError?.let { error ->
                                Text(
                                    error,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
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

                if (isExpense) {
                    OutlinedCard(
                        onClick = {
                            focusManager.clearFocus()
                            showNumpad = false
                            showAmortizationDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (amortizationEnabled) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = if (amortizationEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "攤提",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    amortizationSummary ?: "將一筆支出平均分配到多個月份的預算",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (amortizationEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                                            hasUserEditedProjectSelection = true
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
                                        hasUserEditedProjectSelection = true
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

    if (showPersonalAmountDialog && isExpense) {
        PersonalAmountDialog(
            currency = selectedCurrency,
            totalAmount = resolvedAmount,
            initialValue = personalAmountText,
            onDismiss = { showPersonalAmountDialog = false },
            onConfirm = { updatedValue ->
                personalAmountText = updatedValue
                showPersonalAmountDialog = false
            }
        )
    }

    if (showAmortizationDialog && isExpense) {
        AmortizationDialog(
            currency = selectedCurrency,
            budgetAmount = budgetBaseAmount,
            initialStartYearMonth = amortizationStartYearMonth,
            initialMonthCount = amortizationMonthCount,
            canRemove = amortizationEnabled,
            onDismiss = { showAmortizationDialog = false },
            onApply = { startYearMonth, monthCount ->
                amortizationStartYearMonth = startYearMonth
                amortizationMonthCount = monthCount
                amortizationEnabled = true
                showAmortizationDialog = false
            },
            onRemove = {
                amortizationEnabled = false
                amortizationStartYearMonth = yearMonthFromMillis(selectedDateTime)
                amortizationMonthCount = 6
                showAmortizationDialog = false
            }
        )
    }
}

@Composable
private fun AmortizationDialog(
    currency: String,
    budgetAmount: Double?,
    initialStartYearMonth: Int,
    initialMonthCount: Int,
    canRemove: Boolean,
    onDismiss: () -> Unit,
    onApply: (startYearMonth: Int, monthCount: Int) -> Unit,
    onRemove: () -> Unit
) {
    var startYearMonth by remember(initialStartYearMonth) { mutableIntStateOf(initialStartYearMonth) }
    var monthCountText by remember(initialMonthCount) { mutableStateOf(initialMonthCount.toString()) }
    var showMonthPicker by remember { mutableStateOf(false) }
    val monthCount = monthCountText.toIntOrNull()
    val isValid = monthCount != null && monthCount >= 2
    val summary = if (isValid) {
        formatAmortizationSummary(
            currency = currency,
            amount = budgetAmount,
            startYearMonth = startYearMonth,
            monthCount = monthCount
        )
    } else {
        "攤提月數需至少 2 個月"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("設定攤提") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "攤提只影響預算與月份列表；支付工具額度仍使用付款日期與支出總額。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedCard(
                    onClick = { showMonthPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "起始月份",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatYearMonth(startYearMonth),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = monthCountText,
                    onValueChange = { value ->
                        if (value.length <= 3 && value.all { it.isDigit() }) {
                            monthCountText = value
                        }
                    },
                    label = { Text("攤提月數") },
                    singleLine = true,
                    isError = monthCountText.isNotBlank() && !isValid,
                    supportingText = { Text("總共 N 個月，包含起始月份") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf(3, 6, 12, 24).forEach { quickMonthCount ->
                        FilterChip(
                            selected = monthCount == quickMonthCount,
                            onClick = { monthCountText = quickMonthCount.toString() },
                            label = { Text("${quickMonthCount}月") }
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                ) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isValid) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { monthCount?.let { onApply(startYearMonth, it) } },
                enabled = isValid
            ) {
                Text("套用")
            }
        },
        dismissButton = {
            Row {
                if (canRemove) {
                    TextButton(onClick = onRemove) {
                        Text("移除攤提")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )

    if (showMonthPicker) {
        YearMonthPickerDialog(
            initialTimeMillis = millisForYearMonth(startYearMonth),
            onDateSelected = { dateMillis ->
                startYearMonth = yearMonthFromMillis(dateMillis)
                showMonthPicker = false
            },
            onDismiss = { showMonthPicker = false }
        )
    }
}

@Composable
private fun PersonalAmountHandle(
    isActive: Boolean,
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thresholdPx = with(LocalDensity.current) { 28.dp.toPx() }
    var dragDistance by remember { mutableStateOf(0f) }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(isActive) {
                detectHorizontalDragGestures(
                    onDragEnd = { dragDistance = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        dragDistance += dragAmount
                        if (dragDistance >= thresholdPx) {
                            dragDistance = 0f
                            change.consume()
                            onTrigger()
                        }
                    }
                )
            }
            .clickable(onClick = onTrigger)
            .padding(horizontal = 2.dp, vertical = 1.dp)
    ) {
        Icon(
            imageVector = if (isActive) Icons.Filled.People else Icons.Filled.Person,
            contentDescription = "設定我的花費",
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (isActive) "分攤" else "我的花費",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            textDecoration = if (isActive) null else TextDecoration.Underline
        )
    }
}

@Composable
private fun PersonalAmountDialog(
    currency: String,
    totalAmount: Double?,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var tempValue by remember(initialValue) { mutableStateOf(initialValue) }
    val parsedValue = evaluatePersonalAmountInput(tempValue)
    val errorText = when {
        tempValue.isBlank() -> null
        parsedValue == null -> "請輸入正確金額"
        parsedValue <= 0 -> "我的花費需大於 0"
        totalAmount != null && parsedValue > totalAmount -> "我的花費不能大於支出總額"
        else -> null
    }
    val density = LocalDensity.current
    val keyboardLift = 28.dp
    val cardKeyboardGap = 12.dp
    var keyboardHeightPx by remember { mutableStateOf(0) }
    var cardHeightPx by remember { mutableStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val topSafePadding = with(density) {
                WindowInsets.safeDrawing.getTop(this).toDp()
            } + 12.dp
            val navigationBarPadding = with(density) {
                WindowInsets.navigationBars.getBottom(this).toDp()
            }
            val keyboardHeight = if (keyboardHeightPx > 0) {
                with(density) { keyboardHeightPx.toDp() }
            } else {
                352.dp + navigationBarPadding
            }
            val cardHeight = if (cardHeightPx > 0) {
                with(density) { cardHeightPx.toDp() }
            } else {
                260.dp
            }
            val desiredCardBottomPadding = keyboardHeight + keyboardLift + cardKeyboardGap
            val maxCardBottomPadding = (maxHeight - topSafePadding - cardHeight).coerceAtLeast(0.dp)
            val cardBottomPadding = minOf(desiredCardBottomPadding, maxCardBottomPadding)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = cardBottomPadding)
                    .onSizeChanged { cardHeightPx = it.height },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "設定我的花費",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "支出總額會用於支付工具額度統計；這裡填的金額則會用於預算與一般統計。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    totalAmount?.let {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "支出總額 ${CurrencyFormats.formatAmount(currency, it)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick = { tempValue = formatEditableTransactionAmount(it) }
                            ) {
                                Text("導入總額")
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "我的花費",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                if (tempValue.isBlank()) "留空代表全額都算在自己" else tempValue,
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (tempValue.isBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        errorText ?: "例如聚餐刷卡 1200，可先導入總額再按 ÷ 人數",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (errorText != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onConfirm("") }) {
                            Text("清除")
                        }
                        TextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                        TextButton(
                            onClick = { onConfirm(formatPersonalAmountInputForSave(tempValue)) },
                            enabled = errorText == null
                        ) {
                            Text("確定")
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = keyboardLift),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn()
            ) {
                ExpenseNumpad(
                    displayAmount = tempValue,
                    modifier = Modifier.onSizeChanged { keyboardHeightPx = it.height },
                    onDigitClick = { key ->
                        tempValue = appendPersonalAmountKey(tempValue, key)
                    },
                    onDotClick = {
                        tempValue = appendPersonalAmountDot(tempValue)
                    },
                    onBackspaceClick = {
                        if (tempValue.isNotEmpty()) {
                            tempValue = tempValue.dropLast(1)
                        }
                    },
                    onConfirm = {
                        if (errorText == null) {
                            onConfirm(formatPersonalAmountInputForSave(tempValue))
                        }
                    }
                )
            }
        }
    }
}

private fun appendPersonalAmountKey(current: String, key: String): String {
    val operators = "+−×÷"
    return when {
        key in operators -> {
            if (current.isBlank()) {
                current
            } else if (current.last() in operators) {
                current.dropLast(1) + key
            } else {
                current + key
            }
        }
        current.length >= 20 -> current
        else -> current + key
    }
}

private fun appendPersonalAmountDot(current: String): String {
    val lastSegment = current.takeLastWhile { it !in "+−×÷" }
    return when {
        lastSegment.contains('.') -> current
        current.isEmpty() || current.last() in "+−×÷" -> current + "0."
        else -> "$current."
    }
}

private fun evaluatePersonalAmountInput(input: String): Double? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    return if (trimmed.any { it in "+−×÷" }) {
        ExpressionEvaluator.evaluate(trimmed)
    } else {
        trimmed.toDoubleOrNull()
    }
}

private fun formatPersonalAmountInputForSave(input: String): String {
    if (input.isBlank()) return ""
    return evaluatePersonalAmountInput(input)?.let { ExpressionEvaluator.formatResult(it) } ?: input
}

private fun formatAmortizationSummary(
    currency: String,
    amount: Double?,
    startYearMonth: Int,
    monthCount: Int
): String {
    val endYearMonth = amortizationEndYearMonth(startYearMonth, monthCount)
    val periodText = "${formatYearMonth(startYearMonth)} - ${formatYearMonth(endYearMonth)}"
    val amountText = amount?.let {
        "，每月 ${CurrencyFormats.formatAmount(currency, amortizedShare(it, monthCount, 0))}"
    } ?: "，輸入金額後計算每月金額"
    return "攤提：$periodText，共 ${monthCount} 個月$amountText"
}

private fun formatYearMonth(yearMonth: Int): String =
    "%04d/%02d".format(Locale.TAIWAN, yearMonth / 100, yearMonth % 100)

private fun formatEditableTransactionAmount(amount: Double): String =
    if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()

private fun isWithinProjectDateRange(
    targetDateTime: Long,
    startDate: Long?,
    endDate: Long?
): Boolean {
    if (startDate == null || endDate == null) return false
    val rangeStart = startOfDay(startDate)
    val rangeEnd = endOfDay(endDate)
    return targetDateTime in rangeStart..rangeEnd
}

private fun startOfDay(timeMillis: Long): Long = Calendar.getInstance().run {
    timeInMillis = timeMillis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    timeInMillis
}

private fun endOfDay(timeMillis: Long): Long = Calendar.getInstance().run {
    timeInMillis = timeMillis
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
    timeInMillis
}
