package com.rton.expensebucket.ui.screens

import com.rton.expensebucket.ui.util.localizedLabel
import com.rton.expensebucket.R
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.rton.expensebucket.data.HistoricalCashRateProvider
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.ocr.OcrEngine
import com.rton.expensebucket.ocr.OcrResult
import com.rton.expensebucket.ui.components.ExpenseNumpad
import com.rton.expensebucket.ui.util.CurrencyFormats
import com.rton.expensebucket.ui.util.ExpressionEvaluator
import com.rton.expensebucket.ui.util.PaymentIconMapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrPreviewScreen(
    imageUri: Uri?,
    categories: List<Category>,
    paymentMethods: List<PaymentMethod>,
    ocrEngine: OcrEngine,
    onSave: (Transaction) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalContext.current.resources

    val context = LocalContext.current
    var ocrResult by remember { mutableStateOf<OcrResult?>(null) }
    var isProcessing by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Editable fields from OCR result
    var amount by remember { mutableStateOf("") }
    var personalAmountText by remember { mutableStateOf("") }
    var showPersonalAmountDialog by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedPaymentMethodId by remember { mutableStateOf<Long?>(null) }
    var selectedCurrency by remember { mutableStateOf("TWD") }
    var exchangeRateText by remember { mutableStateOf("1") }
    var showCurrencyMenu by remember { mutableStateOf(false) }
    var isFetchingExchangeRate by remember { mutableStateOf(false) }
    var exchangeRateError by remember { mutableStateOf<String?>(null) }

    // ─── Two-level category and payment methods state ────────────────────────────
    var expandedParentId by remember { mutableStateOf<Long?>(null) }
    var expandedPaymentParentId by remember { mutableStateOf<Long?>(null) }

    // Run OCR when screen opens
    LaunchedEffect(imageUri) {
        if (imageUri == null) {
            errorMessage = strings.getString(R.string.image_missing)
            isProcessing = false
            return@LaunchedEffect
        }
        try {
            val result = ocrEngine.processImage(context, imageUri)
            ocrResult = result
            // Pre-fill fields from parsed result
            result.parsed?.let { parsed ->
                amount = if (parsed.amount == parsed.amount.toLong().toDouble()) {
                    parsed.amount.toLong().toString()
                } else {
                    parsed.amount.toString()
                }
                note = parsed.merchant.ifBlank { parsed.note }
                isExpense = parsed.isExpense
                // Try to match payment method hint
                parsed.paymentMethodHint?.let { hint ->
                    paymentMethods.find { it.icon == hint }?.let {
                        selectedPaymentMethodId = it.id
                    }
                }
            }
        } catch (e: Throwable) {
            errorMessage = strings.getString(R.string.ocr_failed, e.localizedMessage)
        }
        isProcessing = false
    }

    // Default to first payment method if none matched
    LaunchedEffect(selectedPaymentMethodId, paymentMethods) {
        if (selectedPaymentMethodId == null && paymentMethods.isNotEmpty()) {
            selectedPaymentMethodId = paymentMethods.firstOrNull { it.isDefault }?.id
                ?: paymentMethods.first().id
        }

        // Resolve selected payment method parent for UI initialization
        if (selectedPaymentMethodId != null && expandedPaymentParentId == null) {
            val selectedMethod = paymentMethods.find { it.id == selectedPaymentMethodId }
            if (selectedMethod?.parentId != null) {
                expandedPaymentParentId = selectedMethod.parentId
            }
        }
    }

    // Derive parent and child payment methods
    val parentPaymentMethods = remember(paymentMethods) {
        paymentMethods.filter { it.parentId == null }.sortedBy { it.sortOrder }
    }
    val childPaymentMethodsMap = remember(paymentMethods) {
        paymentMethods.filter { it.parentId != null }.groupBy { it.parentId }
    }

    val parentCategories = remember(categories, isExpense) {
        categories.filter { it.isExpense == isExpense && it.parentId == null }
    }
    val childCategoriesMap = remember(categories) {
        categories.filter { it.parentId != null }.groupBy { it.parentId }
    }
    val requiresExchangeRate = selectedCurrency != "TWD"
    val parsedAmount = amount.toDoubleOrNull()
    val parsedPersonalAmount = if (isExpense) personalAmountText.toDoubleOrNull() else null
    val parsedExchangeRate = if (requiresExchangeRate) exchangeRateText.toDoubleOrNull() else 1.0
    val convertedAmount = if (parsedAmount != null && parsedExchangeRate != null) {
        parsedAmount * parsedExchangeRate
    } else {
        null
    }
    val personalAmountError = when {
        parsedPersonalAmount == null && personalAmountText.isNotBlank() -> strings.getString(R.string.invalid_amount)
        parsedPersonalAmount != null && parsedPersonalAmount <= 0 -> strings.getString(R.string.personal_amount_positive)
        parsedPersonalAmount != null && parsedAmount != null && parsedPersonalAmount > parsedAmount ->
            strings.getString(R.string.personal_amount_exceeds_total)
        else -> null
    }

    fun openPersonalAmountEditor() {
        if (!isExpense) return
        if (parsedAmount == null || parsedAmount <= 0) {
            personalAmountText = ""
        }
        showPersonalAmountDialog = true
    }

    LaunchedEffect(isExpense) {
        if (!isExpense) {
            personalAmountText = ""
            showPersonalAmountDialog = false
        }
    }

    LaunchedEffect(selectedCurrency) {
        if (selectedCurrency == "TWD") {
            isFetchingExchangeRate = false
            exchangeRateError = null
            exchangeRateText = "1"
            return@LaunchedEffect
        }

        isFetchingExchangeRate = true
        exchangeRateError = null
        HistoricalCashRateProvider.getExchangeRate(
            fromCurrency = selectedCurrency,
            toCurrency = "TWD",
            dateMillis = System.currentTimeMillis()
        ).onSuccess { fetchedRate ->
            exchangeRateText = CurrencyFormats.formatRate(fetchedRate)
        }.onFailure {
            exchangeRateText = ""
            exchangeRateError = strings.getString(R.string.historical_cash_rate_failed)
        }
        isFetchingExchangeRate = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        strings.getString(R.string.screenshot_ocr),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.getString(R.string.back))
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── Screenshot preview ─────────────────────────────
            if (imageUri != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = imageUri),
                        contentDescription = strings.getString(R.string.screenshot),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // ─── Processing state ───────────────────────────────
            if (isProcessing) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(strings.getString(R.string.recognizing_text))
                    }
                }
                return@Column
            }

            // ─── Error state ────────────────────────────────────
            errorMessage?.let { err ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                return@Column
            }

            // ─── OCR raw text ───────────────────────────────────
            ocrResult?.let { result ->
                // Confidence indicator
                result.parsed?.let { parsed ->
                    val confidence = (parsed.confidence * 100).toInt()
                    val confColor = when {
                        confidence >= 80 -> Color(0xFF22C55E)
                        confidence >= 50 -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    }
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = confColor.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = confColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                strings.getString(R.string.recognition_confidence, confidence),
                                style = MaterialTheme.typography.labelLarge,
                                color = confColor
                            )
                        }
                    }
                }

                // Raw text (expandable)
                var showRawText by remember { mutableStateOf(false) }
                OutlinedCard(
                    onClick = { showRawText = !showRawText },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.TextSnippet,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                strings.getString(R.string.original_ocr_text),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (showRawText) Icons.Filled.ExpandLess
                                else Icons.Filled.ExpandMore,
                                contentDescription = null
                            )
                        }
                        AnimatedVisibility(visible = showRawText) {
                            Text(
                                result.rawText.ifBlank { strings.getString(R.string.no_text_recognized) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // ─── Editable fields ────────────────────────────────
            Text(
                strings.getString(R.string.confirm_transaction_data),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            // Amount
            Column(
                modifier = Modifier.fillMaxWidth(),
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
                        if (isExpense) strings.getString(R.string.expense_total) else strings.getString(R.string.income_amount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(strings.getString(R.string.total_amount)) },
                    leadingIcon = { Text("$", style = MaterialTheme.typography.titleMedium) },
                    singleLine = true,
                    isError = isExpense && personalAmountError != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    supportingText = {
                        if (isExpense && parsedPersonalAmount != null) {
                            Text(strings.getString(R.string.ocr_my_share_amount, CurrencyFormats.formatAmount(selectedCurrency, parsedPersonalAmount)))
                        } else if (isExpense) {
                            Text(strings.getString(R.string.personal_amount_explanation))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }

            if (isExpense) personalAmountError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Note / merchant
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(strings.getString(R.string.merchant_note)) },
                leadingIcon = {
                    Icon(Icons.Filled.Store, contentDescription = null)
                },
                singleLine = false,
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Expense / Income toggle
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isExpense,
                    onClick = { isExpense = true },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    label = { Text(strings.getString(R.string.expense)) }
                )
                SegmentedButton(
                    selected = !isExpense,
                    onClick = { isExpense = false },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    label = { Text(strings.getString(R.string.income)) }
                )
            }

            // Category selector (Two-level)
            if (parentCategories.isNotEmpty()) {
                Text(
                    strings.getString(R.string.category),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Parent categories
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
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
                            label = { Text(strings.localizedLabel(parent.name)) }
                        )
                    }
                }

                // Child categories
                AnimatedVisibility(visible = expandedParentId != null) {
                    val children = childCategoriesMap[expandedParentId] ?: emptyList()
                    if (children.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(start = 16.dp, top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            children.forEach { child ->
                                val chipColor = Color(child.color)
                                val isSelected = selectedCategory?.id == child.id
                                AssistChip(
                                    onClick = { selectedCategory = child },
                                    label = { Text(strings.localizedLabel(child.name), style = MaterialTheme.typography.labelSmall) },
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

                // Selected category label
                selectedCategory?.let { cat ->
                    val parentName = if (cat.parentId != null) {
                        parentCategories.find { it.id == cat.parentId }?.name ?: ""
                    } else ""
                    val label = if (parentName.isNotBlank()) "${strings.localizedLabel(parentName)} > ${strings.localizedLabel(cat.name)}" else strings.localizedLabel(cat.name)
                    Text(
                        strings.getString(R.string.selected_label, label),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(cat.color),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Payment method selector
            if (parentPaymentMethods.isNotEmpty()) {
                Text(
                    strings.getString(R.string.payment_methods),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Parent methods
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    parentPaymentMethods.forEach { parent ->
                        val isParentSelected = expandedPaymentParentId == parent.id ||
                            (selectedPaymentMethodId != null && paymentMethods.find { it.id == selectedPaymentMethodId }?.parentId == parent.id) ||
                            selectedPaymentMethodId == parent.id
                        val color = Color(parent.color)

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
                            },
                            label = { Text(strings.localizedLabel(parent.name), style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(
                                    PaymentIconMapper.getIcon(parent.icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = color
                                )
                            }
                        )
                    }
                }

                // Child methods
                AnimatedVisibility(visible = expandedPaymentParentId != null) {
                    val children = childPaymentMethodsMap[expandedPaymentParentId] ?: emptyList()
                    if (children.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(start = 16.dp, top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            children.forEach { child ->
                                val chipColor = Color(child.color)
                                val isSelected = selectedPaymentMethodId == child.id
                                AssistChip(
                                    onClick = { selectedPaymentMethodId = child.id },
                                    label = { Text(strings.localizedLabel(child.name), style = MaterialTheme.typography.labelSmall) },
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
                        val label = if (parentName.isNotBlank()) "${strings.localizedLabel(parentName)} > ${strings.localizedLabel(m.name)}" else strings.localizedLabel(m.name)
                        Text(
                            strings.getString(R.string.selected_label, label),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(m.color),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedCard(
                    onClick = { showCurrencyMenu = true },
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
                                    strings.getString(R.string.transaction_currency),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (requiresExchangeRate) "$selectedCurrency -> TWD" else selectedCurrency,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                        Icon(Icons.Filled.ExpandMore, contentDescription = null)
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
                                if (currency == "TWD") {
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = exchangeRateText,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.all { it.isDigit() || it == '.' }) {
                                    exchangeRateText = newValue
                                }
                            },
                            label = { Text(strings.getString(R.string.cash_exchange_rate)) },
                            placeholder = { Text("1 $selectedCurrency = ? TWD") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            supportingText = {
                                Text(
                                    when {
                                        isFetchingExchangeRate -> strings.getString(R.string.fetching_cash_rate)
                                        exchangeRateError != null -> exchangeRateError!!
                                        else -> strings.getString(R.string.cash_rate_explanation)
                                    }
                                )
                            }
                        )

                        convertedAmount?.let {
                            Text(
                                strings.getString(R.string.converted_twd, CurrencyFormats.formatAmount("TWD", it)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save button
            Button(
                onClick = {
                    val amountValue = parsedAmount ?: return@Button
                    val exchangeRate = parsedExchangeRate ?: return@Button
                    onSave(
                        Transaction(
                            amount = amountValue,
                            personalAmount = if (isExpense) parsedPersonalAmount?.takeIf { it > 0 } else null,
                            note = note,
                            categoryId = selectedCategory?.id,
                            paymentMethodId = selectedPaymentMethodId,
                            isExpense = isExpense,
                            currency = selectedCurrency,
                            exchangeRate = exchangeRate,
                            source = "ocr",
                            isDraft = false,
                            date = System.currentTimeMillis(),
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = parsedAmount != null && parsedAmount > 0 && parsedExchangeRate != null && parsedExchangeRate > 0
                    && (!isExpense || personalAmountError == null)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.getString(R.string.confirm_record), style = MaterialTheme.typography.titleSmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showPersonalAmountDialog && isExpense) {
        PersonalAmountDialog(
            currency = selectedCurrency,
            totalAmount = parsedAmount,
            initialValue = personalAmountText,
            onDismiss = { showPersonalAmountDialog = false },
            onConfirm = { updatedValue ->
                personalAmountText = updatedValue
                showPersonalAmountDialog = false
            }
        )
    }
}

@Composable
private fun PersonalAmountHandle(
    isActive: Boolean,
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalContext.current.resources

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
            contentDescription = strings.getString(R.string.set_my_share),
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (isActive) strings.getString(R.string.split) else strings.getString(R.string.my_share),
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
    val strings = LocalContext.current.resources

    var tempValue by remember(initialValue) { mutableStateOf(initialValue) }
    val parsedValue = evaluatePersonalAmountInput(tempValue)
    val errorText = when {
        tempValue.isBlank() -> null
        parsedValue == null -> strings.getString(R.string.invalid_amount)
        parsedValue <= 0 -> strings.getString(R.string.personal_amount_positive)
        totalAmount != null && parsedValue > totalAmount -> strings.getString(R.string.personal_amount_exceeds_total)
        else -> null
    }
    val density = LocalDensity.current
    val keyboardLift = 28.dp
    val cardKeyboardGap = 12.dp
    var keyboardHeightPx by remember { mutableStateOf(0) }
    var cardHeightPx by remember { mutableStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val topSafePadding = with(density) {
                WindowInsets.safeDrawing.getTop(this).toDp()
            } + 12.dp
            val keyboardBottomPadding = with(density) {
                maxOf(
                    WindowInsets.navigationBars.getBottom(this),
                    WindowInsets.mandatorySystemGestures.getBottom(this),
                    WindowInsets.safeDrawing.getBottom(this)
                ).toDp()
            } + keyboardLift
            val keyboardHeight = if (keyboardHeightPx > 0) {
                with(density) { keyboardHeightPx.toDp() }
            } else {
                352.dp + keyboardBottomPadding
            }
            val cardHeight = if (cardHeightPx > 0) {
                with(density) { cardHeightPx.toDp() }
            } else {
                260.dp
            }
            val desiredCardBottomPadding = keyboardHeight + cardKeyboardGap
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
                        strings.getString(R.string.set_my_share),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        strings.getString(R.string.personal_amount_usage),
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
                                strings.getString(R.string.expense_total_amount, CurrencyFormats.formatAmount(currency, it)),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick = { tempValue = formatEditableOcrAmount(it) }
                            ) {
                                Text(strings.getString(R.string.import_total))
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
                                strings.getString(R.string.my_share),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                if (tempValue.isBlank()) strings.getString(R.string.personal_amount_empty_hint) else tempValue,
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
                        errorText ?: strings.getString(R.string.personal_amount_example),
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
                            Text(strings.getString(R.string.clear))
                        }
                        TextButton(onClick = onDismiss) {
                            Text(strings.getString(R.string.cancel))
                        }
                        TextButton(
                            onClick = { onConfirm(formatPersonalAmountInputForSave(tempValue)) },
                            enabled = errorText == null
                        ) {
                            Text(strings.getString(R.string.ok))
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = true,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn()
            ) {
                ExpenseNumpad(
                    displayAmount = tempValue,
                    includeNavigationBarInset = false,
                    bottomContentPadding = keyboardBottomPadding,
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

private fun formatEditableOcrAmount(amount: Double): String =
    if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()
