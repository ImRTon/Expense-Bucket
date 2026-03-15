package com.rton.expensebucket.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.Image
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.rton.expensebucket.data.HistoricalCashRateProvider
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.ocr.OcrEngine
import com.rton.expensebucket.ocr.OcrResult
import com.rton.expensebucket.ui.util.CurrencyFormats
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
    val context = LocalContext.current
    var ocrResult by remember { mutableStateOf<OcrResult?>(null) }
    var isProcessing by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Editable fields from OCR result
    var amount by remember { mutableStateOf("") }
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
            errorMessage = "沒有收到圖片"
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
        } catch (e: Exception) {
            errorMessage = "OCR 辨識失敗: ${e.localizedMessage}"
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
    val parsedExchangeRate = if (requiresExchangeRate) exchangeRateText.toDoubleOrNull() else 1.0
    val convertedAmount = if (parsedAmount != null && parsedExchangeRate != null) {
        parsedAmount * parsedExchangeRate
    } else {
        null
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
            exchangeRateError = "歷史現鈔匯率抓取失敗，可手動修正"
        }
        isFetchingExchangeRate = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "截圖辨識",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                        contentDescription = "截圖",
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
                        Text("正在辨識文字...")
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
                                "辨識信心度: $confidence%",
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
                                "原始辨識文字",
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
                                result.rawText.ifBlank { "(未辨識到文字)" },
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
                "確認交易資料",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            // Amount
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("金額") },
                leadingIcon = { Text("$", style = MaterialTheme.typography.titleMedium) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Note / merchant
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("商家 / 備註") },
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
                    label = { Text("支出") }
                )
                SegmentedButton(
                    selected = !isExpense,
                    onClick = { isExpense = false },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    label = { Text("收入") }
                )
            }

            // Category selector (Two-level)
            if (parentCategories.isNotEmpty()) {
                Text(
                    "分類",
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
                            label = { Text(parent.name) }
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
                
                // Selected category label
                selectedCategory?.let { cat ->
                    val parentName = if (cat.parentId != null) {
                        parentCategories.find { it.id == cat.parentId }?.name ?: ""
                    } else ""
                    val label = if (parentName.isNotBlank()) "$parentName > ${cat.name}" else cat.name
                    Text(
                        "已選: $label",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(cat.color),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Payment method selector
            if (parentPaymentMethods.isNotEmpty()) {
                Text(
                    "支付工具",
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
                            label = { Text(parent.name, style = MaterialTheme.typography.labelMedium) },
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
                                    "記帳幣別",
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
                            label = { Text("現鈔匯率") },
                            placeholder = { Text("1 $selectedCurrency = ? TWD") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                            ),
                            supportingText = {
                                Text(
                                    when {
                                        isFetchingExchangeRate -> "正在抓取歷史現鈔匯率..."
                                        exchangeRateError != null -> exchangeRateError!!
                                        else -> "已依日期自動帶入歷史現鈔匯率，會用這個匯率換算回 TWD。"
                                    }
                                )
                            }
                        )

                        convertedAmount?.let {
                            Text(
                                "換算後約 ${CurrencyFormats.formatAmount("TWD", it)}",
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
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("確認記帳", style = MaterialTheme.typography.titleSmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
