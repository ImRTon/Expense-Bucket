package com.rton.expensebucket.ui.screens

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.HistoricalCashRateProvider
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Project
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.model.millisForYearMonth
import com.rton.expensebucket.data.model.yearMonthFromMillis
import com.rton.expensebucket.ui.components.ExpenseNumpad
import com.rton.expensebucket.ui.components.YearMonthPickerDialog
import com.rton.expensebucket.ui.model.TransactionPrefill
import com.rton.expensebucket.ui.theme.amountLarge
import com.rton.expensebucket.ui.theme.semanticColors
import com.rton.expensebucket.ui.util.CurrencyFormats
import com.rton.expensebucket.ui.util.ExpressionEvaluator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
        mutableStateOf(
            initialAmount?.let { AddTransactionFormLogic.formatEditableTransactionAmount(it) } ?: ""
        )
    }
    var personalAmountText by remember {
        mutableStateOf(
            initialPersonalAmount?.let { AddTransactionFormLogic.formatEditableTransactionAmount(it) } ?: ""
        )
    }
    var showPersonalAmountSheet by remember { mutableStateOf(false) }
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
    var selectedCategory by remember {
        mutableStateOf(
            existingTransaction?.categoryId?.let { catId -> categories.find { it.id == catId } }
        )
    }
    var isExpense by remember { mutableStateOf(initialIsExpense) }
    var note by remember { mutableStateOf(initialNote) }
    var selectedProjectId by remember { mutableStateOf(initialProjectSelectionId) }
    var hasUserEditedProjectSelection by remember {
        mutableStateOf(initialProjectSelectionId != null)
    }
    var showProjectSheet by remember { mutableStateOf(false) }
    var showCurrencySheet by remember { mutableStateOf(false) }
    var showDateTimeSheet by remember { mutableStateOf(false) }
    var showPaymentMethodSheet by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val initialSettlementCurrency = initialProjectSelectionId
        ?.let { projectId -> projects.find { it.id == projectId }?.defaultCurrency }
        ?: "TWD"
    var selectedCurrency by remember {
        mutableStateOf(existingTransaction?.currency ?: prefill?.currency ?: initialSettlementCurrency)
    }
    var exchangeRateText by remember {
        mutableStateOf(CurrencyFormats.formatRate(existingTransaction?.exchangeRate ?: prefill?.exchangeRate ?: 1.0))
    }
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

    var expandedParentId by remember { mutableStateOf<Long?>(null) }
    var showNumpad by remember { mutableStateOf(!isEditMode) }
    var isNoteFocused by remember { mutableStateOf(false) }
    var selectedDateTime by remember {
        mutableStateOf(existingTransaction?.date ?: prefill?.date ?: System.currentTimeMillis())
    }

    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val hasUnsavedInput = AddTransactionFormLogic.hasUnsavedInput(
        amountText = amountText,
        selectedCategoryId = selectedCategory?.id,
        note = note,
        personalAmountText = personalAmountText,
        isEditMode = isEditMode
    )

    BackHandler(enabled = isNoteFocused) {
        focusManager.clearFocus()
    }

    BackHandler(enabled = !isNoteFocused && showNumpad) {
        showNumpad = false
    }

    BackHandler(enabled = !isNoteFocused && !showNumpad && hasUnsavedInput) {
        showDiscardDialog = true
    }

    // Auto-select active travel project that covers the selected transaction date
    LaunchedEffect(projects, selectedDateTime, existingTransaction, hasUserEditedProjectSelection) {
        if (existingTransaction != null || hasUserEditedProjectSelection) return@LaunchedEffect
        selectedProjectId = AddTransactionFormLogic.matchActiveProjectForDate(projects, selectedDateTime)?.id
    }

    // Update selectedCategory when isExpense changes (only in add mode)
    LaunchedEffect(isExpense, categories) {
        if (!isEditMode && selectedCategory != null) {
            val matchesType = categories.find { it.id == selectedCategory?.id && it.isExpense == isExpense }
            if (matchesType == null) selectedCategory = null
        }
    }

    LaunchedEffect(isExpense) {
        if (!isExpense) {
            personalAmountText = ""
            showPersonalAmountSheet = false
            showAmortizationDialog = false
            amortizationEnabled = false
        }
    }

    LaunchedEffect(selectedDateTime, amortizationEnabled) {
        if (!amortizationEnabled) {
            amortizationStartYearMonth = yearMonthFromMillis(selectedDateTime)
        }
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress && showNumpad) {
            showNumpad = false
        }
    }

    // Automatically scroll down to reveal the expanded Note box as the software keyboard opens
    LaunchedEffect(isNoteFocused, imeBottomPx, scrollState.maxValue) {
        if (isNoteFocused) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val parentCategories = remember(categories, isExpense) {
        categories.filter { it.isExpense == isExpense && it.parentId == null }.sortedBy { it.sortOrder }
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
    val selectedPaymentMethod = remember(selectedPaymentMethodId, paymentMethods) {
        paymentMethods.find { it.id == selectedPaymentMethodId }
    }
    val selectedProject = remember(selectedProjectId, projects) {
        projects.find { it.id == selectedProjectId }
    }
    val settlementCurrency = selectedProject?.defaultCurrency ?: "TWD"

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
            exchangeRateError = "匯率取得失敗，請手動輸入"
        }
        isFetchingExchangeRate = false
    }

    val resolvedAmount = AddTransactionFormLogic.resolveAmount(amountText)
    val requiresExchangeRate = selectedCurrency != settlementCurrency
    val parsedExchangeRate = if (requiresExchangeRate) exchangeRateText.toDoubleOrNull() else 1.0
    val parsedPersonalAmount = if (isExpense && personalAmountText.isNotBlank()) {
        AddTransactionFormLogic.resolveAmount(personalAmountText)
    } else {
        null
    }
    val convertedAmount = if (resolvedAmount != null && parsedExchangeRate != null) {
        resolvedAmount * parsedExchangeRate
    } else {
        null
    }
    val budgetBaseAmount = if (isExpense) parsedPersonalAmount ?: resolvedAmount else null
    val personalAmountError = AddTransactionFormLogic.validatePersonalAmount(
        personalAmountText = personalAmountText,
        isExpense = isExpense,
        resolvedAmount = resolvedAmount
    )
    val canSave = AddTransactionFormLogic.canSave(
        resolvedAmount = resolvedAmount,
        parsedExchangeRate = parsedExchangeRate,
        personalAmountError = personalAmountError
    )

    fun currentFormDraft() = AddTransactionFormDraft(
        amountText = amountText,
        personalAmountText = personalAmountText,
        selectedCategoryId = selectedCategory?.id,
        isExpense = isExpense,
        note = note,
        selectedDateTime = selectedDateTime,
        selectedPaymentMethodId = selectedPaymentMethodId,
        selectedProjectId = selectedProjectId,
        selectedCurrency = selectedCurrency,
        exchangeRateText = exchangeRateText,
        amortizationEnabled = amortizationEnabled,
        amortizationStartYearMonth = amortizationStartYearMonth,
        amortizationMonthCount = amortizationMonthCount,
        showNumpad = showNumpad
    )

    fun doSave(exitAfterSave: Boolean = true) {
        val transaction = AddTransactionFormLogic.buildTransaction(
            existingTransaction = existingTransaction,
            prefillSource = prefill?.source,
            draft = currentFormDraft(),
            settlementCurrency = settlementCurrency
        ) ?: return

        onSave(transaction)
        if (exitAfterSave) {
            onBack()
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val resetDraft = AddTransactionFormLogic.resetForSaveAndAddAnother(currentFormDraft())
            amountText = resetDraft.amountText
            personalAmountText = resetDraft.personalAmountText
            selectedCategory = null
            expandedParentId = null
            note = resetDraft.note
            amortizationEnabled = resetDraft.amortizationEnabled
            amortizationStartYearMonth = resetDraft.amortizationStartYearMonth
            amortizationMonthCount = resetDraft.amortizationMonthCount
            showNumpad = resetDraft.showNumpad
            focusManager.clearFocus()
            coroutineScope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = "已儲存",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    val semanticColors = MaterialTheme.semanticColors

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (hasUnsavedInput) {
                                showDiscardDialog = true
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    // Expense / Income Pill Toggle: uniform 4.dp inset padding around outer track
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val expenseSelected = isExpense
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (expenseSelected) semanticColors.expense else Color.Transparent)
                                    .clickable {
                                        isExpense = true
                                        expandedParentId = null
                                        focusManager.clearFocus()
                                    }
                                    .padding(horizontal = 22.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "支出",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (expenseSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (expenseSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val incomeSelected = !isExpense
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (incomeSelected) semanticColors.income else Color.Transparent)
                                    .clickable {
                                        isExpense = false
                                        expandedParentId = null
                                        focusManager.clearFocus()
                                    }
                                    .padding(horizontal = 22.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "收入",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (incomeSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (incomeSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                AnimatedVisibility(
                    visible = showNumpad,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    ExpenseNumpad(
                        displayAmount = amountText,
                        includeNavigationBarInset = true,
                        onDigitClick = { key ->
                            amountText = AddTransactionFormLogic.appendAmountKey(amountText, key)
                        },
                        onDotClick = {
                            amountText = AddTransactionFormLogic.appendAmountDot(amountText)
                        },
                        onBackspaceClick = {
                            if (amountText.isNotEmpty()) {
                                amountText = amountText.dropLast(1)
                            }
                        },
                        onEvaluate = {
                            amountText = AddTransactionFormLogic.evaluateExpressionToText(amountText)
                        },
                        onConfirm = {
                            amountText = AddTransactionFormLogic.evaluateExpressionToText(amountText)
                            showNumpad = false
                        }
                    )
                }

                AnimatedVisibility(
                    visible = !showNumpad && !isNoteFocused,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        val buttonBgColor = if (canSave) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        }
                        val buttonContentColor = if (canSave) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .combinedClickable(
                                        enabled = canSave,
                                        onClick = { doSave(exitAfterSave = true) },
                                        onLongClick = {
                                            if (!isEditMode && canSave) {
                                                doSave(exitAfterSave = false)
                                            }
                                        }
                                    ),
                                shape = MaterialTheme.shapes.large,
                                color = buttonBgColor
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = buttonContentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isEditMode) "更新" else "儲存",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = buttonContentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ─── Fixed Amount Header at Top (includes Currency & Split) ───
            val parentCategoryName = remember(selectedCategory, parentCategories) {
                selectedCategory?.parentId?.let { pId ->
                    parentCategories.find { it.id == pId }?.name
                }
            }

            AmountHeader(
                amountText = amountText,
                resolvedAmount = resolvedAmount,
                isExpense = isExpense,
                selectedCategory = selectedCategory,
                parentCategoryName = parentCategoryName,
                selectedCurrency = selectedCurrency,
                settlementCurrency = settlementCurrency,
                parsedExchangeRate = parsedExchangeRate,
                convertedAmount = convertedAmount,
                parsedPersonalAmount = parsedPersonalAmount,
                personalAmountError = personalAmountError,
                isNumpadVisible = showNumpad,
                onAmountClick = {
                    focusManager.clearFocus()
                    showNumpad = true
                },
                onCurrencyClick = {
                    focusManager.clearFocus()
                    showNumpad = false
                    showCurrencySheet = true
                },
                onPersonalAmountClick = {
                    if (isExpense) {
                        focusManager.clearFocus()
                        showNumpad = false
                        showPersonalAmountSheet = true
                    }
                }
            )

            // ─── Scrollable Form Content ────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(bottom = 16.dp)
            ) {
                CategoryGrid(
                    parentCategories = parentCategories,
                    childCategoriesMap = childCategoriesMap,
                    selectedCategory = selectedCategory,
                    expandedParentId = expandedParentId,
                    onSelectCategory = { cat ->
                        selectedCategory = cat
                        focusManager.clearFocus()
                        showNumpad = false
                    },
                    onToggleExpandParent = { parentId ->
                        expandedParentId = parentId
                        focusManager.clearFocus()
                        showNumpad = false
                    }
                )

                TransactionDetailList(
                    selectedDateTime = selectedDateTime,
                    selectedPaymentMethod = selectedPaymentMethod,
                    projects = projects,
                    selectedProject = selectedProject,
                    isExpense = isExpense,
                    amortizationEnabled = amortizationEnabled,
                    amortizationMonthCount = amortizationMonthCount,
                    note = note,
                    isNoteFocused = isNoteFocused,
                    onDateClick = {
                        focusManager.clearFocus()
                        showNumpad = false
                        showDateTimeSheet = true
                    },
                    onPaymentMethodClick = {
                        focusManager.clearFocus()
                        showNumpad = false
                        showPaymentMethodSheet = true
                    },
                    onProjectClick = {
                        focusManager.clearFocus()
                        showNumpad = false
                        showProjectSheet = true
                    },
                    onAmortizationClick = {
                        if (isExpense) {
                            focusManager.clearFocus()
                            showNumpad = false
                            showAmortizationDialog = true
                        }
                    },
                    onNoteChange = { note = it },
                    onNoteFocusChanged = { focused ->
                        isNoteFocused = focused
                        if (focused) {
                            showNumpad = false
                        }
                    }
                )
            }
        }
    }

    // ─── Sheets & Dialogs ───────────────────────────────────────────
    if (showDateTimeSheet) {
        DateTimeSheet(
            initialDateTimeMillis = selectedDateTime,
            onDismiss = { showDateTimeSheet = false },
            onConfirm = { updatedMillis ->
                selectedDateTime = updatedMillis
                showDateTimeSheet = false
            }
        )
    }

    if (showPaymentMethodSheet) {
        PaymentMethodSheet(
            parentPaymentMethods = parentPaymentMethods,
            childPaymentMethodsMap = childPaymentMethodsMap,
            selectedPaymentMethodId = selectedPaymentMethodId,
            onSelectPaymentMethod = { methodId ->
                selectedPaymentMethodId = methodId
            },
            onDismiss = { showPaymentMethodSheet = false }
        )
    }

    if (showProjectSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProjectSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "選擇專案",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("日常帳 (無專案)") },
                    leadingContent = {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    },
                    trailingContent = {
                        if (selectedProjectId == null) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            val oldSettlementCurrency = settlementCurrency
                            hasUserEditedProjectSelection = true
                            selectedProjectId = null
                            if (selectedCurrency == oldSettlementCurrency) {
                                selectedCurrency = "TWD"
                                exchangeRateText = "1"
                            }
                            showProjectSheet = false
                        }
                )
                projects.forEach { project ->
                    val isSelected = selectedProjectId == project.id
                    ListItem(
                        headlineContent = { Text(project.name) },
                        supportingContent = { Text("結算幣別：${project.defaultCurrency}") },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Luggage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                val oldSettlementCurrency = settlementCurrency
                                hasUserEditedProjectSelection = true
                                selectedProjectId = project.id
                                if (selectedCurrency == oldSettlementCurrency) {
                                    selectedCurrency = project.defaultCurrency
                                    exchangeRateText = "1"
                                }
                                showProjectSheet = false
                            }
                    )
                }
            }
        }
    }

    if (showCurrencySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCurrencySheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "記帳幣別",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Text(
                        text = "結算幣別：$settlementCurrency",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                CurrencyFormats.supportedCurrencies.chunked(3).forEach { rowCurrencies ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowCurrencies.forEach { currency ->
                            val isSelected = selectedCurrency == currency
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedCurrency = currency
                                    if (currency == settlementCurrency) {
                                        exchangeRateText = "1"
                                        showCurrencySheet = false
                                    } else if (exchangeRateText == "1") {
                                        exchangeRateText = ""
                                    }
                                },
                                label = {
                                    Text(
                                        text = currency,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowCurrencies.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                AnimatedVisibility(visible = requiresExchangeRate) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = exchangeRateText,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.all { it.isDigit() || it == '.' }) {
                                    exchangeRateText = newValue
                                }
                            },
                            label = { Text("匯率 (1 $selectedCurrency = ? $settlementCurrency)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            supportingText = {
                                if (isFetchingExchangeRate || exchangeRateError != null) {
                                    Text(
                                        text = if (isFetchingExchangeRate) "正在取得歷史匯率…" else exchangeRateError.orEmpty()
                                    )
                                } else if (convertedAmount != null) {
                                    Text("換算後約 ${CurrencyFormats.formatAmount(settlementCurrency, convertedAmount)}")
                                }
                            }
                        )
                        Button(
                            onClick = { showCurrencySheet = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("確定")
                        }
                    }
                }
            }
        }
    }

    if (showPersonalAmountSheet && isExpense) {
        PersonalAmountSheet(
            currency = selectedCurrency,
            totalAmount = resolvedAmount,
            initialValue = personalAmountText,
            onDismiss = { showPersonalAmountSheet = false },
            onConfirm = { updatedValue ->
                personalAmountText = updatedValue
                showPersonalAmountSheet = false
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

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("捨棄變更？") },
            text = { Text("尚未儲存目前已輸入的記帳內容，確定要離開嗎？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    }
                ) {
                    Text("捨棄", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("繼續編輯")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalAmountSheet(
    currency: String,
    totalAmount: Double?,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tempValue by remember(initialValue) { mutableStateOf(initialValue) }
    val parsedValue = AddTransactionFormLogic.resolveAmount(tempValue)
    val hasOperator = ExpressionEvaluator.hasOperator(tempValue)
    val errorText = AddTransactionFormLogic.validatePersonalAmount(
        personalAmountText = tempValue,
        isExpense = true,
        resolvedAmount = totalAmount
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── Sheet Header ───────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "分攤 · 我的花費",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (tempValue.isNotBlank() || initialValue.isNotBlank()) {
                    TextButton(
                        onClick = { onConfirm("") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("重設為全額")
                    }
                }
            }

            // ─── Amount Display & Quick Import Card ─────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = totalAmount?.let {
                                "支出總額 ${CurrencyFormats.formatAmount(currency, it)}"
                            } ?: "個人實際負擔金額",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        totalAmount?.let { total ->
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable {
                                        tempValue = AddTransactionFormLogic.formatEditableTransactionAmount(total)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "帶入總額",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = currency,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            text = if (tempValue.isBlank()) "全額算自己" else tempValue,
                            style = if (tempValue.isBlank()) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.amountLarge
                            },
                            color = if (tempValue.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .weight(1f)
                                .alignByBaseline()
                        )
                    }

                    if (hasOperator && parsedValue != null) {
                        Text(
                            text = "= ${ExpressionEvaluator.formatResult(parsedValue)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (errorText != null) {
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ─── Docked Numpad Inside Sheet ─────────────────────────
            ExpenseNumpad(
                displayAmount = tempValue,
                includeNavigationBarInset = true,
                onDigitClick = { key ->
                    tempValue = AddTransactionFormLogic.appendAmountKey(tempValue, key)
                },
                onDotClick = {
                    tempValue = AddTransactionFormLogic.appendAmountDot(tempValue)
                },
                onBackspaceClick = {
                    if (tempValue.isNotEmpty()) {
                        tempValue = tempValue.dropLast(1)
                    }
                },
                onEvaluate = {
                    tempValue = AddTransactionFormLogic.evaluateExpressionToText(tempValue)
                },
                onConfirm = {
                    if (errorText == null) {
                        onConfirm(AddTransactionFormLogic.evaluateExpressionToText(tempValue))
                    }
                }
            )
        }
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
    val summary = if (isValid && monthCount != null) {
        AddTransactionFormLogic.formatAmortizationSummary(
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedCard(
                    onClick = { showMonthPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
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
                                AddTransactionFormLogic.formatYearMonth(startYearMonth),
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf(3, 6, 12, 24).forEach { quickMonthCount ->
                        FilterChip(
                            selected = monthCount == quickMonthCount,
                            onClick = { monthCountText = quickMonthCount.toString() },
                            label = { Text("${quickMonthCount}期") }
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
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
                        modifier = Modifier.padding(12.dp)
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
                        Text("移除")
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
